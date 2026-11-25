ALTER TABLE messages DROP CONSTRAINT messages_kind_check;
ALTER TABLE messages ADD CONSTRAINT messages_kind_check
    CHECK (kind IN ('TEXT', 'IMAGE', 'ITEM_CARD', 'OFFER_CHANGE', 'SCHEDULE_SET', 'SYSTEM'));
