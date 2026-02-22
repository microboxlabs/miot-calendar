package com.microboxlabs.miot.calendar.service;

import com.microboxlabs.miot.calendar.entity.Calendar;
import com.microboxlabs.miot.calendar.entity.Slot;
import com.microboxlabs.miot.calendar.entity.TimeWindow;
import com.microboxlabs.miot.calendar.model.GenerateSlotsResponse;
import com.microboxlabs.miot.calendar.model.SlotStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service for generating slots based on time window configuration
 */
@ApplicationScoped
public class SlotGeneratorService {

    private static final Logger LOG = Logger.getLogger(SlotGeneratorService.class);

    /**
     * Generate slots for a calendar within a date range
     */
    @Transactional
    public GenerateSlotsResponse generateSlots(UUID calendarId, LocalDate startDate, LocalDate endDate) {
        Calendar calendar = Calendar.findById(calendarId);
        if (calendar == null) {
            throw new IllegalArgumentException("Calendar not found: " + calendarId);
        }

        List<TimeWindow> timeWindows = TimeWindow.findActiveByCalendarId(calendarId);
        if (timeWindows.isEmpty()) {
            LOG.warnf("No active time windows found for calendar %s", calendarId);
            return GenerateSlotsResponse.of(0, 0);
        }

        int created = 0;
        int skipped = 0;

        // Iterate through each date in the range
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            final LocalDate date = currentDate;
            String dayOfWeek = getDayOfWeekCode(date.getDayOfWeek());

            // Find applicable time windows for this date
            List<TimeWindow> validWindows = timeWindows.stream()
                .filter(tw -> tw.validFrom.compareTo(date) <= 0)
                .filter(tw -> tw.validTo == null || tw.validTo.compareTo(date) >= 0)
                .filter(tw -> tw.includesDay(dayOfWeek))
                .toList();

            for (TimeWindow timeWindow : validWindows) {
                // Generate slots for this time window
                int hour = timeWindow.startHour;
                int minutes = 0;

                while (hour < timeWindow.endHour) {
                    // Check if slot already exists
                    Slot existing = Slot.findByCalendarAndDateTime(calendarId, date, hour, minutes);
                    
                    if (existing == null) {
                        // Create new slot
                        Slot slot = new Slot();
                        slot.calendar = calendar;
                        slot.timeWindow = timeWindow;
                        slot.slotDate = date;
                        slot.slotHour = hour;
                        slot.slotMinutes = minutes;
                        slot.capacity = timeWindow.capacityPerSlot;
                        slot.currentOccupancy = 0;
                        slot.status = SlotStatus.OPEN;
                        slot.persist();
                        created++;
                    } else {
                        skipped++;
                    }

                    // Move to next slot
                    minutes += timeWindow.slotDurationMinutes;
                    if (minutes >= 60) {
                        hour += minutes / 60;
                        minutes = minutes % 60;
                    }
                }
            }

            currentDate = currentDate.plusDays(1);
        }

        LOG.infof("Generated %d slots for calendar %s (%d skipped)", created, calendarId, skipped);
        return GenerateSlotsResponse.of(created, skipped);
    }

    /**
     * Convert DayOfWeek to its ISO numeric string (1=Monday … 7=Sunday),
     * matching the format stored by the API ("daysOfWeek": "1,2,3,4,5").
     */
    private String getDayOfWeekCode(DayOfWeek dayOfWeek) {
        return String.valueOf(dayOfWeek.getValue());
    }
}
