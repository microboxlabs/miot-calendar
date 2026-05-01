-- V10: Add optional display color to time windows
-- Stores a UI color token (e.g., "emerald", "amber"). Closed set is enforced
-- by the client; the database stays permissive.

ALTER TABLE cld_time_windows
    ADD COLUMN color VARCHAR(32);
