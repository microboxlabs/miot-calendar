-- Widens the default-calendar key from the origin alone to (origin, service type).
--
-- V15 made "there can be only one default per origin" true at the storage
-- layer. That was right while every trip was a `v`: one delegación, one
-- calendar to catch what nobody planned by hand. Freight now runs `otr` and
-- `ote` alongside `v`, and those book into their own calendars — which the V15
-- index forbids outright, so no application change could have delivered it.
--
-- `serviceType` needs no column: `filter` is already a free-form JSONB map and
-- the application validates which keys may appear in it.
--
-- COALESCE folds the unset case into a single key exactly as before, now on
-- both axes: a default with no service type answers for every type nothing
-- claims specifically, and a default with neither origin nor type stays the
-- catch-all of last resort.
--
-- Existing defaults carry no `serviceType`, so they keep the key they had
-- (origin, ''), and `v` traffic resolves where it resolves today. Nothing moves
-- until someone creates a calendar that names a service type.

DROP INDEX IF EXISTS uq_cld_calendars_default_per_origin;

CREATE UNIQUE INDEX uq_cld_calendars_default_per_origin_and_type
    ON cld_calendars (
        COALESCE(filter->>'origin', ''),
        COALESCE(filter->>'serviceType', '')
    )
    WHERE is_default;

COMMENT ON COLUMN cld_calendars.is_default IS
    'Default calendar for this calendar''s filter origin and service type: used when an integrating system must book but was given no calendar';
