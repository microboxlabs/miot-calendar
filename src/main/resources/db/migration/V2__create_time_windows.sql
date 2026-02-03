-- V2: Create time windows table
-- Time windows define when slots are available and their constraints

CREATE TABLE cld_time_windows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    calendar_id UUID NOT NULL REFERENCES cld_calendars(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    start_hour INTEGER NOT NULL CHECK (start_hour BETWEEN 0 AND 23),
    end_hour INTEGER NOT NULL CHECK (end_hour BETWEEN 0 AND 23),
    slot_duration_minutes INTEGER NOT NULL DEFAULT 30 CHECK (slot_duration_minutes > 0),
    capacity_per_slot INTEGER NOT NULL DEFAULT 1 CHECK (capacity_per_slot > 0),
    days_of_week VARCHAR(50) DEFAULT 'MON,TUE,WED,THU,FRI',
    valid_from DATE NOT NULL,
    valid_to DATE,
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    -- Ensure valid_to is after valid_from if set
    CONSTRAINT chk_time_window_dates CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

-- Indexes
CREATE INDEX idx_cld_time_windows_calendar ON cld_time_windows(calendar_id, active);
CREATE INDEX idx_cld_time_windows_validity ON cld_time_windows(valid_from, valid_to);

-- Comments
COMMENT ON TABLE cld_time_windows IS 'Time window configuration - defines when and how slots are generated';
COMMENT ON COLUMN cld_time_windows.start_hour IS 'Start hour of the time window (0-23)';
COMMENT ON COLUMN cld_time_windows.end_hour IS 'End hour of the time window (0-23)';
COMMENT ON COLUMN cld_time_windows.slot_duration_minutes IS 'Duration of each slot in minutes';
COMMENT ON COLUMN cld_time_windows.capacity_per_slot IS 'Maximum bookings allowed per slot';
COMMENT ON COLUMN cld_time_windows.days_of_week IS 'Comma-separated list of days (MON,TUE,WED,THU,FRI,SAT,SUN)';
