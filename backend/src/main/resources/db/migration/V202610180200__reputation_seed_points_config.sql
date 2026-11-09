-- Seed reputation points configuration from SRS v10 FR-97.
-- Give: donor +10, receiver +2. Swap: each +6.
INSERT INTO website_attributes (attr_group, attr_key, attr_value)
VALUES
  ('CONFIG', 'reputation_give_donor', '10'),
  ('CONFIG', 'reputation_give_receiver', '2'),
  ('CONFIG', 'reputation_swap_each', '6'),
  ('CONFIG', 'REPUTATION_GIVE_DONOR', '10'),
  ('CONFIG', 'REPUTATION_GIVE_RECEIVER', '2'),
  ('CONFIG', 'REPUTATION_SWAP_EACH', '6')
ON CONFLICT (attr_group, attr_key) WHERE NOT is_deleted DO NOTHING;

