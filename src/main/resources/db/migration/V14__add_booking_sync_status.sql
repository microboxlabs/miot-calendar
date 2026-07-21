-- Acknowledgement state of a booking's current data by an external
-- downstream system that mirrors this calendar — orthogonal to the
-- monotonic lifecycle `status` (a booking can be ASSIGNED yet unconfirmed
-- downstream, and a data change legitimately drops back to PENDING, which
-- the forward-only status ladder cannot express).
--
-- NULL = untracked (calendars with no external mirror, or rows created
-- before this feature).
-- PENDING   = a synchronization is queued/in flight; not acknowledged yet.
-- CONFIRMED = the external system acknowledged the booking's current data.
-- REJECTED  = the external system refused it; see sync_detail.

ALTER TABLE cld_bookings ADD COLUMN sync_status VARCHAR(20);
ALTER TABLE cld_bookings ADD COLUMN sync_detail VARCHAR(500);
ALTER TABLE cld_bookings ADD COLUMN sync_at TIMESTAMPTZ;

ALTER TABLE cld_bookings ADD CONSTRAINT chk_cld_bookings_sync_status CHECK
    (sync_status IS NULL OR sync_status IN ('PENDING', 'CONFIRMED', 'REJECTED'));

COMMENT ON COLUMN cld_bookings.sync_status IS
    'External-system acknowledgement of the booking''s current data: PENDING/CONFIRMED/REJECTED; NULL = untracked';
COMMENT ON COLUMN cld_bookings.sync_detail IS
    'External-system acknowledgement/rejection detail';
COMMENT ON COLUMN cld_bookings.sync_at IS
    'Timestamp of the last sync_status transition';
