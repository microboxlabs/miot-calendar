package com.microboxlabs.miot.calendar.model;

/**
 * Execution status for a slot manager run
 */
public enum RunStatus {
    /** Manager is idle — no run in progress */
    IDLE,
    /** A run is currently executing */
    RUNNING,
    /** Run completed successfully */
    SUCCESS,
    /** Run completed with an error */
    FAILED,
    /** Run was skipped (already up-to-date or another instance running) */
    SKIPPED
}
