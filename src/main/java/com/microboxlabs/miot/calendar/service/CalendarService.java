package com.microboxlabs.miot.calendar.service;

import com.microboxlabs.miot.calendar.entity.Calendar;
import com.microboxlabs.miot.calendar.entity.CalendarGroup;
import com.microboxlabs.miot.calendar.entity.Slot;
import com.microboxlabs.miot.calendar.entity.SlotManager;
import com.microboxlabs.miot.calendar.entity.TimeWindow;
import com.microboxlabs.miot.calendar.model.CalendarRequest;
import com.microboxlabs.miot.calendar.model.SlotManagerTriggerEvent;
import com.microboxlabs.miot.calendar.model.TimeWindowRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for calendar operations
 */
@ApplicationScoped
public class CalendarService {

    private static final Logger LOG = Logger.getLogger(CalendarService.class);

    @Inject
    CalendarGroupService calendarGroupService;

    private final Event<SlotManagerTriggerEvent> slotManagerTrigger;
    private final SlotManagerService slotManagerService;

    @Inject
    public CalendarService(SlotManagerService slotManagerService,
                           Event<SlotManagerTriggerEvent> slotManagerTrigger) {
        this.slotManagerService = slotManagerService;
        this.slotManagerTrigger = slotManagerTrigger;
    }

    /**
     * Get all calendars
     */
    @Transactional
    public List<Calendar> getAllCalendars() {
        return Calendar.listAll();
    }

    /**
     * Get all active calendars
     */
    @Transactional
    public List<Calendar> getActiveCalendars() {
        return Calendar.findAllActive();
    }

    /**
     * Get calendar by ID
     */
    @Transactional
    public Optional<Calendar> getCalendarById(UUID id) {
        return Optional.ofNullable(Calendar.findById(id));
    }

    /**
     * Get active calendars belonging to a group
     */
    @Transactional
    public List<Calendar> getCalendarsByGroupCode(String groupCode) {
        return Calendar.findByGroupCode(groupCode);
    }

    /**
     * Get calendar by code
     */
    public Optional<Calendar> getCalendarByCode(String code) {
        return Optional.ofNullable(Calendar.findByCode(code));
    }

    /**
     * Create a new calendar
     */
    @Transactional
    public Calendar createCalendar(CalendarRequest request) {
        request.validate();
        
        // Check if code already exists
        if (Calendar.findByCode(request.code()) != null) {
            throw new IllegalArgumentException("Calendar with code '" + request.code() + "' already exists");
        }

        Calendar calendar = new Calendar();
        calendar.code = request.code();
        calendar.name = request.name();
        calendar.description = request.description();
        calendar.timezone = request.timezone() != null ? request.timezone() : "America/Santiago";
        calendar.active = request.active() != null ? request.active() : true;
        
        calendar.persist();

        if (request.groups() != null && !request.groups().isEmpty()) {
            resolveAndAssignGroups(calendar, request.groups());
        }

        // Auto-provision SlotManager (defaults to true when null)
        boolean autoSlotManager = request.autoSlotManager() == null || request.autoSlotManager();
        if (autoSlotManager) {
            SlotManager manager = slotManagerService.createDefaultManager(calendar);
            slotManagerTrigger.fire(new SlotManagerTriggerEvent(manager.id, "API"));
        }

        LOG.infof("Created calendar: %s (%s)", calendar.name, calendar.code);

        return calendar;
    }

    /**
     * Update an existing calendar
     */
    @Transactional
    public Calendar updateCalendar(UUID id, CalendarRequest request) {
        Calendar calendar = Calendar.findById(id);
        if (calendar == null) {
            throw new IllegalArgumentException("Calendar not found: " + id);
        }

        // Check if new code conflicts with existing
        if (request.code() != null && !request.code().equals(calendar.code)) {
            Calendar existing = Calendar.findByCode(request.code());
            if (existing != null && !existing.id.equals(id)) {
                throw new IllegalArgumentException("Calendar with code '" + request.code() + "' already exists");
            }
            calendar.code = request.code();
        }

        if (request.name() != null) {
            calendar.name = request.name();
        }
        if (request.description() != null) {
            calendar.description = request.description();
        }
        if (request.timezone() != null) {
            calendar.timezone = request.timezone();
        }
        if (request.active() != null) {
            calendar.active = request.active();
        }

        if (request.groups() != null) {
            calendar.groups.clear();
            if (!request.groups().isEmpty()) {
                resolveAndAssignGroups(calendar, request.groups());
            }
        }

        LOG.infof("Updated calendar: %s (%s)", calendar.name, calendar.code);
        return calendar;
    }

    /**
     * Deactivate a calendar
     */
    @Transactional
    public void deactivateCalendar(UUID id) {
        Calendar calendar = Calendar.findById(id);
        if (calendar == null) {
            throw new IllegalArgumentException("Calendar not found: " + id);
        }
        calendar.active = false;
        slotManagerService.deactivateManagerByCalendarId(id);
        LOG.infof("Deactivated calendar: %s (%s)", calendar.name, calendar.code);
    }

    /**
     * Hard delete a calendar and all its associated data
     */
    @Transactional
    public void hardDeleteCalendar(UUID id) {
        Calendar calendar = Calendar.findById(id);
        if (calendar == null) {
            throw new IllegalArgumentException("Calendar not found: " + id);
        }
        // Step 1: Delete all slots (DB cascade deletes related bookings via slot_id FK)
        Slot.deleteByCalendarId(id);
        // Step 2: Delete the calendar (JPA cascade deletes TimeWindows; DB cascade deletes SlotManager+Runs+GroupMembers)
        calendar.delete();
        LOG.infof("Hard deleted calendar: %s (%s)", calendar.name, calendar.code);
    }

    /**
     * Resolve group codes to entities and assign them to the calendar
     */
    private void resolveAndAssignGroups(Calendar calendar, List<String> groupCodes) {
        for (String code : groupCodes) {
            CalendarGroup group = CalendarGroup.findByCode(code);
            if (group == null || !group.active) {
                throw new IllegalArgumentException("Unknown or inactive group code: " + code);
            }
            calendar.groups.add(group);
        }
    }

    // Time Window operations

    /**
     * Get time windows for a calendar
     */
    public List<TimeWindow> getTimeWindows(UUID calendarId) {
        return TimeWindow.findActiveByCalendarId(calendarId);
    }

    /**
     * Create a time window
     */
    @Transactional
    public TimeWindow createTimeWindow(UUID calendarId, TimeWindowRequest request) {
        request.validate();
        
        Calendar calendar = Calendar.findById(calendarId);
        if (calendar == null) {
            throw new IllegalArgumentException("Calendar not found: " + calendarId);
        }

        TimeWindow timeWindow = new TimeWindow();
        timeWindow.calendar = calendar;
        timeWindow.name = request.name();
        timeWindow.startHour = request.startHour();
        timeWindow.endHour = request.endHour();
        timeWindow.slotDurationMinutes = request.slotDurationMinutes() != null ? request.slotDurationMinutes() : 30;
        timeWindow.capacityPerSlot = request.capacityPerSlot() != null ? request.capacityPerSlot() : 1;
        timeWindow.daysOfWeek = request.daysOfWeek() != null ? request.daysOfWeek() : "1,2,3,4,5";
        timeWindow.validFrom = request.validFrom();
        timeWindow.validTo = request.validTo();
        timeWindow.active = request.active() != null ? request.active() : true;

        timeWindow.persist();
        LOG.infof("Created time window: %s for calendar %s", timeWindow.name, calendar.code);

        SlotManager manager = SlotManager.findByCalendarId(calendarId);
        if (manager != null && Boolean.TRUE.equals(manager.active)) {
            // If the manager already generated slots through a future date, force a
            // reprocess so this new time window's slots are created for the full range.
            if (manager.generatedThrough != null) {
                manager.reprocessFrom = LocalDate.now();
                manager.reprocessTo   = manager.generatedThrough;
            }
            slotManagerTrigger.fire(new SlotManagerTriggerEvent(manager.id, "API"));
        }

        return timeWindow;
    }

    /**
     * Update a time window
     */
    @Transactional
    public TimeWindow updateTimeWindow(UUID timeWindowId, TimeWindowRequest request) {
        TimeWindow timeWindow = TimeWindow.findById(timeWindowId);
        if (timeWindow == null) {
            throw new IllegalArgumentException("Time window not found: " + timeWindowId);
        }

        if (request.name() != null) {
            timeWindow.name = request.name();
        }
        if (request.startHour() != null) {
            timeWindow.startHour = request.startHour();
        }
        if (request.endHour() != null) {
            timeWindow.endHour = request.endHour();
        }
        if (request.slotDurationMinutes() != null) {
            timeWindow.slotDurationMinutes = request.slotDurationMinutes();
        }
        if (request.capacityPerSlot() != null) {
            timeWindow.capacityPerSlot = request.capacityPerSlot();
        }
        if (request.daysOfWeek() != null) {
            timeWindow.daysOfWeek = request.daysOfWeek();
        }
        if (request.validFrom() != null) {
            timeWindow.validFrom = request.validFrom();
        }
        if (request.validTo() != null) {
            timeWindow.validTo = request.validTo();
        }
        if (request.active() != null) {
            timeWindow.active = request.active();
        }

        LOG.infof("Updated time window: %s", timeWindow.name);

        SlotManager manager = SlotManager.findByCalendarId(timeWindow.calendar.id);
        if (manager != null && Boolean.TRUE.equals(manager.active)) {
            // Force a reprocess so updated schedule rules apply to already-generated dates.
            if (manager.generatedThrough != null) {
                manager.reprocessFrom = LocalDate.now();
                manager.reprocessTo   = manager.generatedThrough;
            }
            slotManagerTrigger.fire(new SlotManagerTriggerEvent(manager.id, "API"));
        }

        return timeWindow;
    }
}
