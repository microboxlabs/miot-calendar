package com.microboxlabs.miot.calendar.service;

import com.microboxlabs.miot.calendar.entity.Calendar;
import com.microboxlabs.miot.calendar.entity.CalendarGroup;
import com.microboxlabs.miot.calendar.entity.Slot;
import com.microboxlabs.miot.calendar.entity.SlotManager;
import com.microboxlabs.miot.calendar.entity.TimeWindow;
import com.microboxlabs.miot.calendar.model.CalendarRequest;
import com.microboxlabs.miot.calendar.model.SlotGenerationMode;
import com.microboxlabs.miot.calendar.model.SlotManagerTriggerEvent;
import com.microboxlabs.miot.calendar.model.TimeWindowKind;
import com.microboxlabs.miot.calendar.model.TimeWindowRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for calendar operations
 */
@ApplicationScoped
@SuppressWarnings("java:S3252") // Panache active-record requires static calls on the entity subclass
public class CalendarService {

    private static final Logger LOG = Logger.getLogger(CalendarService.class);

    private final CalendarGroupService calendarGroupService;
    private final Event<SlotManagerTriggerEvent> slotManagerTrigger;
    private final SlotManagerService slotManagerService;

    @Inject
    public CalendarService(SlotManagerService slotManagerService,
                           Event<SlotManagerTriggerEvent> slotManagerTrigger,
                           CalendarGroupService calendarGroupService) {
        this.slotManagerService = slotManagerService;
        this.slotManagerTrigger = slotManagerTrigger;
        this.calendarGroupService = calendarGroupService;
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
        calendar.parallelism = request.parallelism() != null ? request.parallelism() : 1;
        calendar.filter = sanitizeFilter(request.filter());

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

        updateCalendarCode(id, request, calendar);
        if (request.name() != null)        calendar.name = request.name();
        if (request.description() != null) calendar.description = request.description();
        if (request.timezone() != null)    calendar.timezone = request.timezone();
        if (request.active() != null)      calendar.active = request.active();

        boolean parallelismChanged = request.parallelism() != null
                && !request.parallelism().equals(calendar.parallelism);
        if (parallelismChanged) {
            calendar.parallelism = request.parallelism();
        }

        if (request.groups() != null) {
            calendar.groups.clear();
            if (!request.groups().isEmpty()) {
                resolveAndAssignGroups(calendar, request.groups());
            }
        }

        if (request.filter() != null) {
            calendar.filter = sanitizeFilter(request.filter());
        }

        if (parallelismChanged) {
            reprocessSlotsForParallelismChange(id);
        }

        LOG.infof("Updated calendar: %s (%s)", calendar.name, calendar.code);
        return calendar;
    }

    private void updateCalendarCode(UUID id, CalendarRequest request, Calendar calendar) {
        if (request.code() == null || request.code().equals(calendar.code)) {
            return;
        }
        Calendar existing = Calendar.findByCode(request.code());
        if (existing != null && !existing.id.equals(id)) {
            throw new IllegalArgumentException("Calendar with code '" + request.code() + "' already exists");
        }
        calendar.code = request.code();
    }

    private void reprocessSlotsForParallelismChange(UUID calendarId) {
        List<TimeWindow> timeWindows = TimeWindow.findActiveByCalendarId(calendarId);
        for (TimeWindow tw : timeWindows) {
            // Only AUTO windows re-derive their slot length from the new parallelism. MANUAL windows
            // keep the admin-set duration; their bookable/overflow split still shifts on regen because
            // bookableSlots() = ceil(capacity / parallelism) changed.
            if (tw.slotGenerationMode == SlotGenerationMode.AUTO) {
                tw.slotDurationMinutes = tw.computeSlotDurationMinutes();
            }
        }
        triggerSlotManagerReprocess(calendarId);
    }

    private void triggerSlotManagerReprocess(UUID calendarId) {
        SlotManager manager = SlotManager.findByCalendarId(calendarId);
        if (manager == null || !Boolean.TRUE.equals(manager.active)) {
            return;
        }
        if (manager.generatedThrough != null) {
            manager.reprocessFrom = LocalDate.now();
            manager.reprocessTo   = manager.generatedThrough;
        }
        slotManagerTrigger.fire(new SlotManagerTriggerEvent(manager.id, "API"));
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
     * Drop blank/null entries and detach from the request map. Returns null when nothing remains
     * so the JSONB column stores SQL NULL instead of an empty object.
     */
    @SuppressWarnings("java:S1168") // intentional null so JPA writes SQL NULL to the JSONB filter column
    private Map<String, String> sanitizeFilter(Map<String, String> input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        Map<String, String> clean = new HashMap<>();
        for (Map.Entry<String, String> e : input.entrySet()) {
            if (e.getValue() != null && !e.getValue().isBlank()) {
                clean.put(e.getKey(), e.getValue());
            }
        }
        return clean.isEmpty() ? null : clean;
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
        timeWindow.kind = request.kind() != null ? request.kind() : TimeWindowKind.WINDOW;
        timeWindow.slotGenerationMode = resolveSlotGenerationMode(request.slotGenerationMode());
        timeWindow.capacity = resolveCapacity(request.capacity(), timeWindow.kind);
        timeWindow.daysOfWeek = request.daysOfWeek() != null ? request.daysOfWeek() : "1,2,3,4,5";
        // Clear the entity default so applySlotDuration resolves it from the request or the derived
        // value rather than treating the placeholder 30 as an admin-chosen duration.
        timeWindow.slotDurationMinutes = null;
        applySlotDuration(timeWindow, request.slotDurationMinutes());
        timeWindow.validFrom = request.validFrom();
        timeWindow.validTo = request.validTo();
        timeWindow.active = request.active() != null ? request.active() : true;
        timeWindow.color = request.color();

        timeWindow.persist();
        LOG.infof("Created time window: %s for calendar %s", timeWindow.name, calendar.code);
        triggerSlotManagerReprocess(calendarId);
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

        boolean needsRecompute = applyTimeWindowFields(timeWindow, request);
        if (needsRecompute || request.slotDurationMinutes() != null) {
            applySlotDuration(timeWindow, request.slotDurationMinutes());
        }

        LOG.infof("Updated time window: %s", timeWindow.name);
        triggerSlotManagerReprocess(timeWindow.calendar.id);
        return timeWindow;
    }

    /**
     * Default capacity: WINDOW = 1, BLOCK = 0 (BLOCK rows carry no quota).
     */
    private static int resolveCapacity(Integer requested, TimeWindowKind kind) {
        if (requested != null) {
            return requested;
        }
        return kind == TimeWindowKind.BLOCK ? 0 : 1;
    }

    /** Default slot generation mode for new windows: MANUAL. */
    private static SlotGenerationMode resolveSlotGenerationMode(SlotGenerationMode requested) {
        return requested != null ? requested : SlotGenerationMode.MANUAL;
    }

    /**
     * Resolve and persist {@code slotDurationMinutes} after a window's other fields are set.
     * <ul>
     *   <li>BLOCK or AUTO — derived ({@link TimeWindow#computeSlotDurationMinutes()}); any requested value is ignored.</li>
     *   <li>MANUAL WINDOW — uses {@code requestedDuration} (validated to
     *       [{@value TimeWindowRequest#MIN_MANUAL_SLOT_DURATION_MINUTES}, windowMinutes]); if absent, keeps the current
     *       value when it is still usable ({@code 0 < d <= windowMinutes}), otherwise seeds it from the derived value
     *       (so a freshly-created window or one whose window shrank below the old duration falls back to AUTO-equivalent).</li>
     * </ul>
     * Throws {@link IllegalArgumentException} (mapped to HTTP 400 by the resource layer) on an out-of-range requested value.
     */
    private static void applySlotDuration(TimeWindow tw, Integer requestedDuration) {
        if (tw.kind == TimeWindowKind.BLOCK || tw.slotGenerationMode == SlotGenerationMode.AUTO) {
            tw.slotDurationMinutes = tw.computeSlotDurationMinutes();
            return;
        }
        int windowMinutes = (tw.endHour - tw.startHour) * 60;
        if (requestedDuration != null) {
            int min = TimeWindowRequest.MIN_MANUAL_SLOT_DURATION_MINUTES;
            if (requestedDuration < min || requestedDuration > windowMinutes) {
                throw new IllegalArgumentException(
                    "Slot duration must be between " + min + " and " + windowMinutes + " minutes (the window length)");
            }
            tw.slotDurationMinutes = requestedDuration;
            return;
        }
        Integer current = tw.slotDurationMinutes;
        if (current == null || current <= 0 || current > windowMinutes) {
            tw.slotDurationMinutes = tw.computeSlotDurationMinutes();
        }
        // else: keep the existing admin-set duration unchanged (capacity/parallelism edits don't move it).
    }

    private boolean applyTimeWindowFields(TimeWindow tw, TimeWindowRequest request) {
        boolean needsRecompute = false;
        if (request.name() != null)      tw.name = request.name();
        if (request.startHour() != null)  { tw.startHour = request.startHour(); needsRecompute = true; }
        if (request.endHour() != null)    { tw.endHour = request.endHour();     needsRecompute = true; }
        if (request.capacity() != null)   { tw.capacity = request.capacity();   needsRecompute = true; }
        if (request.daysOfWeek() != null) tw.daysOfWeek = request.daysOfWeek();
        if (request.validFrom() != null)  tw.validFrom = request.validFrom();
        if (request.validTo() != null)    tw.validTo = request.validTo();
        if (request.active() != null)     tw.active = request.active();
        if (request.color() != null)      tw.color = request.color();
        if (request.kind() != null)      { tw.kind = request.kind();           needsRecompute = true; }
        if (request.slotGenerationMode() != null) { tw.slotGenerationMode = request.slotGenerationMode(); needsRecompute = true; }
        return needsRecompute;
    }
}
