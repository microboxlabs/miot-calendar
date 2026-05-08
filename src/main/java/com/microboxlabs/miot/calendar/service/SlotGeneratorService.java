package com.microboxlabs.miot.calendar.service;

import com.microboxlabs.miot.calendar.entity.Calendar;
import com.microboxlabs.miot.calendar.entity.Slot;
import com.microboxlabs.miot.calendar.entity.TimeWindow;
import com.microboxlabs.miot.calendar.model.GenerateSlotsResponse;
import com.microboxlabs.miot.calendar.model.SlotStatus;
import com.microboxlabs.miot.calendar.model.TimeWindowKind;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Service for generating slots based on time window configuration
 */
@ApplicationScoped
public class SlotGeneratorService {

    private static final Logger LOG = Logger.getLogger(SlotGeneratorService.class);

    /**
     * Generate slots for a calendar within a date range.
     */
    @Transactional
    public GenerateSlotsResponse generateSlots(UUID calendarId, LocalDate startDate, LocalDate endDate) {
        return generateSlots(calendarId, startDate, endDate, false);
    }

    /**
     * Generate slots for a calendar within a date range.
     * @param reprocess if true, delete unbooked slots first so they are regenerated
     *                  with the current capacity/duration configuration
     */
    @Transactional
    public GenerateSlotsResponse generateSlots(UUID calendarId, LocalDate startDate, LocalDate endDate, boolean reprocess) {
        Calendar calendar = Calendar.findById(calendarId);
        if (calendar == null) {
            throw new IllegalArgumentException("Calendar not found: " + calendarId);
        }

        List<TimeWindow> timeWindows = TimeWindow.findActiveByCalendarId(calendarId);
        if (timeWindows.isEmpty()) {
            LOG.warnf("No active time windows found for calendar %s", calendarId);
            return GenerateSlotsResponse.of(0, 0);
        }

        if (reprocess) {
            long deleted = Slot.deleteUnbookedByCalendarAndDateRange(calendarId, startDate, endDate);
            LOG.infof("Reprocess: deleted %d unbooked slots for calendar %s (%s to %s)",
                deleted, calendarId, startDate, endDate);
        }

        int[] counts = {0, 0}; // [created, skipped]

        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            generateSlotsForDate(calendar, timeWindows, currentDate, counts);
            currentDate = currentDate.plusDays(1);
        }

        LOG.infof("Generated %d slots for calendar %s (%d skipped)", counts[0], calendarId, counts[1]);
        return GenerateSlotsResponse.of(counts[0], counts[1]);
    }

    private void generateSlotsForDate(Calendar calendar, List<TimeWindow> timeWindows,
                                      LocalDate date, int[] counts) {
        String dayOfWeek = String.valueOf(date.getDayOfWeek().getValue());

        // BLOCK windows are processed last so they can override an OPEN slot
        // already created by a colliding WINDOW (CLOSED wins).
        List<TimeWindow> validWindows = timeWindows.stream()
            .filter(tw -> tw.validFrom.compareTo(date) <= 0)
            .filter(tw -> tw.validTo == null || tw.validTo.compareTo(date) >= 0)
            .filter(tw -> tw.includesDay(dayOfWeek))
            .sorted(Comparator.comparing(tw -> tw.kind == TimeWindowKind.BLOCK))
            .toList();

        for (TimeWindow timeWindow : validWindows) {
            generateSlotsForWindow(calendar, timeWindow, date, counts);
        }
    }

    private void generateSlotsForWindow(Calendar calendar, TimeWindow timeWindow,
                                        LocalDate date, int[] counts) {
        int hour = timeWindow.startHour;
        int minutes = 0;
        int slotIndex = 0;
        int maxSlots = timeWindow.kind == TimeWindowKind.BLOCK
                ? Integer.MAX_VALUE
                : timeWindow.computeNumberOfSlots();

        while (hour < timeWindow.endHour && slotIndex < maxSlots) {
            Slot existing = Slot.findByCalendarAndDateTime(calendar.id, date, hour, minutes);
            if (existing == null) {
                createSlot(calendar, timeWindow, date, hour, minutes, slotIndex);
                counts[0]++;
            } else if (timeWindow.kind == TimeWindowKind.BLOCK
                    && existing.status == SlotStatus.OPEN
                    && existing.currentOccupancy == 0) {
                // BLOCK overrides an unbooked OPEN slot left by a colliding window.
                existing.status = SlotStatus.CLOSED;
                existing.timeWindow = timeWindow;
                existing.capacity = 0;
                counts[1]++;
            } else {
                counts[1]++;
            }

            minutes += timeWindow.slotDurationMinutes;
            if (minutes >= 60) {
                hour += minutes / 60;
                minutes = minutes % 60;
            }
            slotIndex++;
        }
    }

    private void createSlot(Calendar calendar, TimeWindow timeWindow,
                            LocalDate date, int hour, int minutes, int slotIndex) {
        Slot slot = new Slot();
        slot.calendar = calendar;
        slot.timeWindow = timeWindow;
        slot.slotDate = date;
        slot.slotHour = hour;
        slot.slotMinutes = minutes;
        if (timeWindow.kind == TimeWindowKind.BLOCK) {
            slot.capacity = 0;
            slot.currentOccupancy = 0;
            slot.status = SlotStatus.CLOSED;
        } else {
            slot.capacity = timeWindow.computeSlotCapacity(slotIndex);
            slot.currentOccupancy = 0;
            slot.status = SlotStatus.OPEN;
        }
        slot.persist();
    }
}
