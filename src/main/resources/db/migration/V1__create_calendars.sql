-- V1: Create calendars table
-- Calendar is the top-level container for booking configuration

CREATE TABLE cld_calendars (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    timezone VARCHAR(50) DEFAULT 'America/Santiago',
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_cld_calendars_code ON cld_calendars(code);
CREATE INDEX idx_cld_calendars_active ON cld_calendars(active);

-- Comments
COMMENT ON TABLE cld_calendars IS 'Booking calendars - top level configuration container';
COMMENT ON COLUMN cld_calendars.code IS 'Unique identifier code for the calendar';
COMMENT ON COLUMN cld_calendars.timezone IS 'Timezone for date/time calculations';
