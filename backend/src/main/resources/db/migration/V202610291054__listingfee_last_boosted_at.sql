ALTER TABLE items ADD COLUMN last_boosted_at TIMESTAMPTZ;

-- Existing paid boosts use the latest ledger entry; older free boosts fall back to the last item update.
UPDATE items i
SET last_boosted_at = COALESCE(
    (SELECT MAX(l.created_at) FROM credit_ledger l
     WHERE l.item_id = i.id AND l.type = 'BOOST_FEE' AND l.amount < 0),
    i.updated_at)
WHERE i.boosted_until > CURRENT_TIMESTAMP;

CREATE INDEX ix_items_last_boosted_at ON items (last_boosted_at DESC, id DESC)
WHERE boosted_until IS NOT NULL AND is_active AND NOT is_deleted AND status = 'APPROVED';

-- The new browse order no longer caps boosted items per page.
UPDATE website_attributes SET is_active = false
WHERE attr_group = 'CONFIG' AND attr_key = 'boost_max_per_page';
