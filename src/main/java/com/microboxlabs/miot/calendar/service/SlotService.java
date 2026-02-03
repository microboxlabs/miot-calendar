package com.microboxlabs.miot.calendar.service;

import com.microboxlabs.miot.calendar.entity.Slot;
import com.microboxlabs.miot.calendar.model.SlotStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for slot operations
 */
@ApplicationScoped
public class SlotService {

    private static final Logger LOG = Logger.getLogger(SlotService.class);

    /**
     * Get slots by calendar and date range
     */
    public List<Slot> getSlots(UUID calendarId, LocalDate startDate, LocalDate endDate) {
        return Slot.findByCalendarAndDateRange(calendarId, startDate, endDate);
    }

    /**
     * Get available slots by calendar and date range
     */
    public List<Slot> getAvailableSlots(UUID calendarId, LocalDate startDate, LocalDate endDate) {
        return Slot.findAvailableByCalendarAndDateRange(calendarId, startDate, endDate);
    }

    /**
     * Get slot by ID
     */
    public Optional<Slot> getSlotById(UUID id) {
        return Optional.ofNullable(Slot.findById(id));
    }

    /**
     * Get slot by calendar, date, hour, and minutes
     */
    public Optional<Slot> getSlotByDateTime(UUID calendarId, LocalDate date, Integer hour, Integer minutes) {
        return Optional.ofNullable(Slot.findByCalendarAndDateTime(calendarId, date, hour, minutes));
    }

    /**
     * Update slot status
     */
    @Transactional
    public Slot updateSlotStatus(UUID slotId, SlotStatus status) {
        Slot slot = Slot.findById(slotId);
        if (slot == null) {
            throw new IllegalArgumentException("Slot not found: " + slotId);
        }

        // Don't allow manual FULL status
        if (status == SlotStatus.FULL) {
            throw new IllegalArgumentException("Cannot manually set status to FULL");
        }

        SlotStatus previousStatus = slot.status;
        slot.status = status;
        
        LOG.infof("Updated slot %s status from %s to %s", slotId, previousStatus, status);
        return slot;
    }

    /**
     * Check if slot has availability
     */
    public boolean hasAvailability(UUID slotId) {
        Slot slot = Slot.findById(slotId);
        return slot != null && slot.hasAvailability();
    }

    /**
     * Increment slot occupancy (called when booking is created)
     */
    @Transactional
    public void incrementOccupancy(Slot slot) {
        slot.incrementOccupancy();
        LOG.debugf("Incremented occupancy for slot %s: %d/%d", 
            slot.id, slot.currentOccupancy, slot.capacity);
    }

    /**
     * Decrement slot occupancy (called when booking is cancelled)
     */
    @Transactional
    public void decrementOccupancy(Slot slot) {
        slot.decrementOccupancy();
        LOG.debugf("Decremented occupancy for slot %s: %d/%d", 
            slot.id, slot.currentOccupancy, slot.capacity);
    }
}
