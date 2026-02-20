CREATE TABLE cld_slot_managers (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    calendar_id       UUID         NOT NULL UNIQUE REFERENCES cld_calendars(id) ON DELETE CASCADE,
    active            BOOLEAN      NOT NULL DEFAULT true,
    days_in_advance   INT          NOT NULL DEFAULT 30,
    batch_days        INT          NOT NULL DEFAULT 7,
    reprocess_from    DATE,
    reprocess_to      DATE,
    last_run_at       TIMESTAMP WITH TIME ZONE,
    last_run_status   VARCHAR(20),
    last_run_error    TEXT,
    generated_through DATE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_cld_slot_managers_days_in_advance CHECK (days_in_advance > 0),
    CONSTRAINT chk_cld_slot_managers_batch_days      CHECK (batch_days > 0),
    CONSTRAINT chk_cld_slot_managers_reprocess       CHECK (
        reprocess_from IS NULL OR reprocess_to IS NULL OR reprocess_from <= reprocess_to
    )
);

CREATE INDEX idx_cld_slot_managers_calendar ON cld_slot_managers(calendar_id);
CREATE INDEX idx_cld_slot_managers_active   ON cld_slot_managers(active);

CREATE TABLE cld_slot_manager_runs (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    manager_id        UUID        NOT NULL REFERENCES cld_slot_managers(id) ON DELETE CASCADE,
    triggered_by      VARCHAR(20) NOT NULL,
    started_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    finished_at       TIMESTAMP WITH TIME ZONE,
    status            VARCHAR(20) NOT NULL,
    slots_created     INT         NOT NULL DEFAULT 0,
    slots_skipped     INT         NOT NULL DEFAULT 0,
    generated_from    DATE,
    generated_through DATE,
    error_message     TEXT
);

CREATE INDEX idx_cld_slot_manager_runs_manager ON cld_slot_manager_runs(manager_id);
CREATE INDEX idx_cld_slot_manager_runs_status  ON cld_slot_manager_runs(status, started_at);

COMMENT ON TABLE cld_slot_managers      IS 'Per-calendar automatic slot generation configuration';
COMMENT ON TABLE cld_slot_manager_runs  IS 'Audit trail of slot manager execution runs';
