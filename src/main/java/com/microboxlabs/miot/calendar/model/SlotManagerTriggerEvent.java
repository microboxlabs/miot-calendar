package com.microboxlabs.miot.calendar.model;

import java.util.UUID;

/**
 * CDI event fired to trigger a slot manager run after a transaction commits.
 *
 * Observed by SlotManagerExecutor with TransactionPhase.AFTER_SUCCESS so the
 * manager always runs after the originating transaction (calendar or time-window
 * write) has been committed and is visible to the generator's queries.
 */
public record SlotManagerTriggerEvent(UUID managerId, String triggeredBy) {}
