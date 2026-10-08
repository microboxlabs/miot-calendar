-- V18: Overbooked bookings sit outside capacity.
-- An overbooked booking does not count in cld_slots.current_occupancy nor in a
-- MANUAL window's daily cap. Automatic integrations create their bookings this way
-- so they never take capacity from a planner.

ALTER TABLE cld_bookings
    ADD COLUMN overbooked BOOLEAN NOT NULL DEFAULT FALSE;
