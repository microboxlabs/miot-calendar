-- V7: Add parallelism to calendars and redesign capacity model
-- Calendar.parallelism = simultaneous resources per slot (e.g., loading docks)
-- TimeWindow.capacity = total services the window can handle (replaces capacity_per_slot)
-- slotDurationMinutes is now derived: floor(windowDuration * parallelism / capacity)

-- Add parallelism to calendars
ALTER TABLE cld_calendars
    ADD COLUMN parallelism INTEGER NOT NULL DEFAULT 1;
ALTER TABLE cld_calendars
    ADD CONSTRAINT chk_cld_calendars_parallelism CHECK (parallelism >= 1);

-- Rename capacity_per_slot → capacity (now means total window capacity)
ALTER TABLE cld_time_windows
    RENAME COLUMN capacity_per_slot TO capacity;

-- Backfill: convert per-slot capacity to total window capacity
-- total = old_per_slot * number_of_slots_in_window
-- Use CEIL to match the generator loop which creates a slot at each offset
-- while the start time is still before endHour (e.g., 120min/45min → 3 slots, not 2)
UPDATE cld_time_windows
SET capacity = capacity * CEIL(((end_hour - start_hour) * 60.0) / slot_duration_minutes)
WHERE true;

-- Update CHECK constraint for new column name
ALTER TABLE cld_time_windows
    DROP CONSTRAINT IF EXISTS cld_time_windows_capacity_per_slot_check;
ALTER TABLE cld_time_windows
    ADD CONSTRAINT chk_cld_time_windows_capacity CHECK (capacity > 0);
