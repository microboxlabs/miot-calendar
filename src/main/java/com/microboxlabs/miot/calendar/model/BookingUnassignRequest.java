package com.microboxlabs.miot.calendar.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * Request to unassign every booking of a resource: reset the lifecycle to
 * {@link BookingStatus#PLANNED} and drop the caller's assignment keys from
 * {@code resource.data}.
 *
 * <p>The caller names the keys to clear because {@code resource.data} is an
 * opaque payload owned by the coordinator — miot-calendar knows a booking has
 * data, not what "assigned driver" is called in it. Keeping the vocabulary on
 * the caller's side means a new assignment field never needs a change here.
 *
 * <p>An empty or absent {@code clearDataKeys} is valid: it unassigns the
 * lifecycle without touching the payload.
 */
@Schema(description = "Unassign bookings of a resource: reset status to PLANNED and clear the named resource.data keys")
public record BookingUnassignRequest(
    @Schema(description = "Top-level resource.data keys to remove (omit to leave the payload unchanged)",
        examples = {"[\"assignedDriver\", \"assignedTruck\"]"})
    List<String> clearDataKeys
) {
    /**
     * Normalized view of {@link #clearDataKeys}: never null, no null or blank
     * entries. A blank key would otherwise silently target nothing.
     */
    public List<String> safeClearDataKeys() {
        if (clearDataKeys == null) {
            return List.of();
        }
        return clearDataKeys.stream()
            .filter(k -> k != null && !k.isBlank())
            .toList();
    }
}
