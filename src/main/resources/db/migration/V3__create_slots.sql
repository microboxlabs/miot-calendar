-- V3: Create slots table
-- Materialized slots representing bookable time units

CREATE TABLE cld_slots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    calendar_id UUID NOT NULL REFERENCES cld_calendars(id),
    time_window_id UUID REFERENCES cld_time_windows(id),
    slot_date DATE NOT NULL,
    slot_hour INTEGER NOT NULL CHECK (slot_hour BETWEEN 0 AND 23),
    slot_minutes INTEGER NOT NULL CHECK (slot_minutes IN (0, 30)),
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    current_occupancy INTEGER DEFAULT 0 CHECK (current_occupancy >= 0),
    status VARCHAR(20) DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'FULL', 'CLOSED')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    -- Unique constraint for calendar + datetime
    CONSTRAINT uk_cld_slots_calendar_datetime UNIQUE (calendar_id, slot_date, slot_hour, slot_minutes)
);

-- Indexes for common queries
CREATE INDEX idx_cld_slots_search ON cld_slots(calendar_id, slot_date, status);
CREATE INDEX idx_cld_slots_availability ON cld_slots(slot_date, status, current_occupancy, capacity);
CREATE INDEX idx_cld_slots_time_window ON cld_slots(time_window_id);

-- Comments
COMMENT ON TABLE cld_slots IS 'Materialized booking slots - pre-generated for fast querying';
COMMENT ON COLUMN cld_slots.slot_date IS 'Date of the slot';
COMMENT ON COLUMN cld_slots.slot_hour IS 'Hour of the slot (0-23)';
COMMENT ON COLUMN cld_slots.slot_minutes IS 'Minutes of the slot (0 or 30 for half-hour slots)';
COMMENT ON COLUMN cld_slots.capacity IS 'Maximum number of bookings for this slot';
COMMENT ON COLUMN cld_slots.current_occupancy IS 'Current number of bookings';
COMMENT ON COLUMN cld_slots.status IS 'OPEN = available, FULL = at capacity, CLOSED = manually closed';
