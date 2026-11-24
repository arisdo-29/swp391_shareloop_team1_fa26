ALTER TABLE items
    ADD COLUMN reference_price bigint,
    ADD CONSTRAINT ck_items_reference_price CHECK (reference_price BETWEEN 1 AND 9007199254740991);
