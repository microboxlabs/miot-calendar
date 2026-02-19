package com.microboxlabs.miot.calendar.service;

import com.microboxlabs.miot.calendar.entity.CalendarGroup;
import com.microboxlabs.miot.calendar.model.CalendarGroupRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for calendar group operations
 */
@ApplicationScoped
public class CalendarGroupService {

    private static final Logger LOG = Logger.getLogger(CalendarGroupService.class);

    /**
     * Get all groups
     */
    public List<CalendarGroup> getAllGroups() {
        return CalendarGroup.listAll();
    }

    /**
     * Get all active groups
     */
    public List<CalendarGroup> getActiveGroups() {
        return CalendarGroup.findAllActive();
    }

    /**
     * Get group by ID
     */
    public Optional<CalendarGroup> getGroupById(UUID id) {
        return Optional.ofNullable(CalendarGroup.findById(id));
    }

    /**
     * Get group by code
     */
    public Optional<CalendarGroup> getGroupByCode(String code) {
        return Optional.ofNullable(CalendarGroup.findByCode(code));
    }

    /**
     * Create a new calendar group
     */
    @Transactional
    public CalendarGroup createGroup(CalendarGroupRequest request) {
        request.validate();

        if (CalendarGroup.findByCode(request.code()) != null) {
            throw new IllegalArgumentException("Group with code '" + request.code() + "' already exists");
        }

        CalendarGroup group = new CalendarGroup();
        group.code = request.code();
        group.name = request.name();
        group.description = request.description();
        group.active = request.active() != null ? request.active() : true;

        group.persist();
        LOG.infof("Created calendar group: %s (%s)", group.name, group.code);

        return group;
    }

    /**
     * Update an existing calendar group
     */
    @Transactional
    public CalendarGroup updateGroup(UUID id, CalendarGroupRequest request) {
        CalendarGroup group = CalendarGroup.findById(id);
        if (group == null) {
            throw new IllegalArgumentException("Group not found: " + id);
        }

        if (request.code() != null && !request.code().equals(group.code)) {
            CalendarGroup existing = CalendarGroup.findByCode(request.code());
            if (existing != null && !existing.id.equals(id)) {
                throw new IllegalArgumentException("Group with code '" + request.code() + "' already exists");
            }
            group.code = request.code();
        }

        if (request.name() != null) {
            group.name = request.name();
        }
        if (request.description() != null) {
            group.description = request.description();
        }
        if (request.active() != null) {
            group.active = request.active();
        }

        LOG.infof("Updated calendar group: %s (%s)", group.name, group.code);
        return group;
    }

    /**
     * Deactivate a calendar group (soft-delete)
     */
    @Transactional
    public void deactivateGroup(UUID id) {
        CalendarGroup group = CalendarGroup.findById(id);
        if (group == null) {
            throw new IllegalArgumentException("Group not found: " + id);
        }
        group.active = false;
        LOG.infof("Deactivated calendar group: %s (%s)", group.name, group.code);
    }
}
