-- Support case-insensitive substring searches on generic booking resource IDs.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_cld_bookings_resource_lower_trgm
    ON cld_bookings USING GIN (lower(resource_id) gin_trgm_ops);
