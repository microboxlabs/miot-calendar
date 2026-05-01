-- V11: Add `kind` discriminator to time windows
-- WINDOW (default) is the historical bookable window with capacity.
-- BLOCK marks a non-bookable period: slot generation produces CLOSED slots
-- so the planning UI can paint blocked cells, and bookings are rejected.

ALTER TABLE cld_time_windows
    ADD COLUMN kind VARCHAR(16) NOT NULL DEFAULT 'WINDOW'
        CHECK (kind IN ('WINDOW', 'BLOCK'));

COMMENT ON COLUMN cld_time_windows.kind IS
    'WINDOW = bookable with capacity; BLOCK = non-bookable, generates CLOSED slots';

-- BLOCK windows persist with capacity = 0 (no quota); BLOCK-derived slots
-- inherit capacity = 0. The original `> 0` checks assumed every row was
-- bookable, which no longer holds. Booking validation rejects CLOSED slots
-- before capacity is read, so the relaxed check is safe.
ALTER TABLE cld_time_windows
    DROP CONSTRAINT IF EXISTS chk_cld_time_windows_capacity;

ALTER TABLE cld_time_windows
    ADD CONSTRAINT chk_cld_time_windows_capacity CHECK (capacity >= 0);

ALTER TABLE cld_slots
    DROP CONSTRAINT IF EXISTS cld_slots_capacity_check;

ALTER TABLE cld_slots
    ADD CONSTRAINT cld_slots_capacity_check CHECK (capacity >= 0);
