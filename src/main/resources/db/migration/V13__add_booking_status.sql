-- CALSYNC: booking lifecycle status, advanced by the workflow coordinator.
-- Historic rows default to PLANNED; a one-off ops backfill marks past-slot
-- rows FINISHED (runbook lives with the ECM CALSYNC integration docs).
ALTER TABLE cld_bookings ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PLANNED';

ALTER TABLE cld_bookings ADD CONSTRAINT chk_cld_bookings_status CHECK
    (status IN ('PLANNED', 'ASSIGNED', 'IN_TRANSIT', 'ARRIVED', 'FINISHED', 'CANCELLED'));

CREATE INDEX idx_cld_bookings_status ON cld_bookings (status);
