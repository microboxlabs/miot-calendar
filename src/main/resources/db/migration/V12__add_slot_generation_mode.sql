-- V12: Per-time-window manual slot duration
--
-- Adds `slot_generation_mode` to time windows:
--   AUTO   = slot_duration_minutes is derived from capacity/parallelism (historical behaviour).
--   MANUAL = slot_duration_minutes is admin-set; slots beyond the window's bookable quota are
--            still generated (so the planning grid can render them) but marked OVERFLOW.
-- New default is MANUAL. Existing rows keep their already-persisted slot_duration_minutes, which
-- makes this migration behaviour-preserving for parallelism = 1 (and a no-op visual change on
-- deploy); switching a window to AUTO recomputes the duration on the next save.
--
-- Also relaxes the cld_slots status CHECK to include 'OVERFLOW'.

ALTER TABLE cld_time_windows
    ADD COLUMN slot_generation_mode VARCHAR(8) NOT NULL DEFAULT 'MANUAL'
        CHECK (slot_generation_mode IN ('AUTO', 'MANUAL'));

COMMENT ON COLUMN cld_time_windows.slot_generation_mode IS
    'AUTO = slot_duration_minutes derived from capacity/parallelism; MANUAL = admin-set slot length';
COMMENT ON COLUMN cld_time_windows.slot_duration_minutes IS
    'Slot length in minutes - admin-authoritative when slot_generation_mode = MANUAL, derived otherwise';

ALTER TABLE cld_slots
    DROP CONSTRAINT IF EXISTS cld_slots_status_check;

ALTER TABLE cld_slots
    ADD CONSTRAINT cld_slots_status_check
        CHECK (status IN ('OPEN', 'FULL', 'CLOSED', 'OVERFLOW'));

COMMENT ON COLUMN cld_slots.status IS
    'OPEN = bookable; FULL = at capacity; CLOSED = blocked (BLOCK window); OVERFLOW = generated beyond window quota, not bookable';
