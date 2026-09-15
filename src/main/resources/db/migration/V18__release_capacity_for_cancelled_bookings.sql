-- A booking cancelled in place used to keep its seat: current_occupancy was only
-- decremented on delete or move, never on a status patch to CANCELLED. The rows
-- left behind are invisible to the planning grid and still count towards the
-- slot's capacity and its time window's daily cap.
--
-- Rebuild current_occupancy for the slots that carry a cancelled booking, and
-- re-derive OPEN/FULL for them. CLOSED and OVERFLOW are verdicts from the
-- operator and the slot generator, so those slots keep the status they have.

WITH held AS (
    SELECT s.id,
           COUNT(b.id) FILTER (WHERE b.status <> 'CANCELLED') AS occupancy
    FROM cld_slots s
    JOIN cld_bookings b ON b.slot_id = s.id
    WHERE s.id IN (SELECT slot_id FROM cld_bookings WHERE status = 'CANCELLED')
    GROUP BY s.id
)
UPDATE cld_slots s
SET current_occupancy = held.occupancy,
    status = CASE
        WHEN s.status IN ('OPEN', 'FULL')
             THEN CASE WHEN held.occupancy >= s.capacity THEN 'FULL' ELSE 'OPEN' END
        ELSE s.status
    END,
    updated_at = NOW()
FROM held
WHERE s.id = held.id
  AND s.current_occupancy <> held.occupancy;
