-- Alerce/TMS confirmation state of a booking's current assignment tuple —
-- orthogonal to the monotonic lifecycle `status` (a booking can be ASSIGNED
-- yet unconfirmed, and a re-assignment legitimately drops back to PENDING,
-- which the forward-only status ladder could not express).
--
-- NULL = no integration tracking for this booking (calendars without an
-- Alerce-integrated origin, or rows created before this feature).
-- PENDING   = a push is queued/in-flight; confirmation not yet received.
-- CONFIRMED = the TMS accepted the tuple (alerce_assignment job SUCCEEDED
--             with body code=OK; stamped by the calendar_confirm chain leg).
-- REJECTED  = the TMS refused the tuple (stamped by the job-failure hook).

ALTER TABLE cld_bookings ADD COLUMN sync_status VARCHAR(20);
ALTER TABLE cld_bookings ADD COLUMN sync_detail VARCHAR(500);
ALTER TABLE cld_bookings ADD COLUMN sync_at TIMESTAMPTZ;

ALTER TABLE cld_bookings ADD CONSTRAINT chk_cld_bookings_sync_status CHECK
    (sync_status IS NULL OR sync_status IN ('PENDING', 'CONFIRMED', 'REJECTED'));

COMMENT ON COLUMN cld_bookings.sync_status IS
    'TMS (Alerce) confirmation of the current assignment tuple: PENDING/CONFIRMED/REJECTED; NULL = untracked';
COMMENT ON COLUMN cld_bookings.sync_detail IS
    'TMS rejection/confirmation detail (e.g. ERROR_ACCION: CONDUCTOR2 NO EXISTE)';
COMMENT ON COLUMN cld_bookings.sync_at IS
    'Timestamp of the last sync_status transition';
