-- V9: Add optional task filter to calendars
-- Stores a JSONB map of filter criteria (e.g., {"origin":"ANF","destination":"ANF"})
-- consumed by client UIs to constrain task lists tied to the calendar.
-- Allowed keys are validated by the application; PostgreSQL only enforces shape.

ALTER TABLE cld_calendars
    ADD COLUMN filter JSONB;

ALTER TABLE cld_calendars
    ADD CONSTRAINT chk_cld_calendars_filter_object
    CHECK (filter IS NULL OR jsonb_typeof(filter) = 'object');
