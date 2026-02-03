-- V4: Create bookings table
-- Generic resource bookings with JSONB for flexible data storage

CREATE TABLE cld_bookings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slot_id UUID NOT NULL REFERENCES cld_slots(id) ON DELETE CASCADE,
    calendar_id UUID NOT NULL REFERENCES cld_calendars(id),
    
    -- Denormalized slot data for faster queries
    slot_date DATE NOT NULL,
    slot_hour INTEGER NOT NULL,
    slot_minutes INTEGER NOT NULL,
    
    -- Generic resource reference
    resource_id VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50),
    resource_label VARCHAR(255),
    
    -- Flexible resource data as JSONB
    resource_data JSONB,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    -- Unique constraint: same resource can't be in same slot twice
    CONSTRAINT uk_cld_bookings_slot_resource UNIQUE (slot_id, resource_id)
);

-- Indexes for common queries
CREATE INDEX idx_cld_bookings_slot ON cld_bookings(slot_id);
CREATE INDEX idx_cld_bookings_date ON cld_bookings(calendar_id, slot_date);
CREATE INDEX idx_cld_bookings_resource ON cld_bookings(resource_id);
CREATE INDEX idx_cld_bookings_type ON cld_bookings(resource_type);

-- GIN index for JSONB queries (analytics)
CREATE INDEX idx_cld_bookings_data ON cld_bookings USING GIN (resource_data);

-- Composite index for analytics queries
CREATE INDEX idx_cld_bookings_analytics ON cld_bookings(calendar_id, slot_date, resource_type);

-- Comments
COMMENT ON TABLE cld_bookings IS 'Resource bookings - generic booking records with JSONB payload';
COMMENT ON COLUMN cld_bookings.resource_id IS 'External identifier for the booked resource';
COMMENT ON COLUMN cld_bookings.resource_type IS 'Type discriminator (SERVICE, VEHICLE, DRIVER, etc.)';
COMMENT ON COLUMN cld_bookings.resource_label IS 'Human-readable label for display';
COMMENT ON COLUMN cld_bookings.resource_data IS 'Full resource payload as JSONB for flexibility';
