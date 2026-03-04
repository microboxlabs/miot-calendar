-- V8: Relax slot_minutes constraint to allow any valid minute value (0-59)
-- The new capacity model derives slotDurationMinutes from capacity/parallelism,
-- which can produce arbitrary durations (e.g., 12min), generating minute offsets
-- beyond the old {0, 30} restriction.

ALTER TABLE cld_slots
    DROP CONSTRAINT IF EXISTS cld_slots_slot_minutes_check;

ALTER TABLE cld_slots
    ADD CONSTRAINT cld_slots_slot_minutes_check CHECK (slot_minutes BETWEEN 0 AND 59);
