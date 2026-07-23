-- Marks the calendar an integrating system should use when it must place a
-- booking but was given no calendar to place it in — e.g. a service created
-- automatically rather than through a planning UI.
--
-- Scoped by the calendar's own `filter->>'origin'`: the default is "the
-- default FOR that origin", so several may coexist as long as each answers for
-- a different origin. A default with no origin filter answers for everything
-- else. Absence is meaningful: no default for an origin means an integrator
-- has nowhere to put the booking and should create none.
--
-- The partial unique index makes "there can be only one" true at the storage
-- layer; COALESCE folds the no-origin case into a single key so it cannot be
-- claimed twice. The application demotes the previous holder before setting a
-- new one, so the index is a backstop, not the mechanism.

ALTER TABLE cld_calendars
    ADD COLUMN is_default BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX uq_cld_calendars_default_per_origin
    ON cld_calendars (COALESCE(filter->>'origin', ''))
    WHERE is_default;

COMMENT ON COLUMN cld_calendars.is_default IS
    'Default calendar for this calendar''s filter origin: used when an integrating system must book but was given no calendar';
