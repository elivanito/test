-- Sample suppliers for development. Idempotent: re-runs are no-ops.
-- The example in the README (country with turnovers 200k, 200k, 200k, 210k, 250k)
-- is reproduced here so the bonus rule can be observed end-to-end.

INSERT INTO suppliers (duns, name, country, annual_turnover, sustainability_rating, status) VALUES
    (100000001, 'Zippers & Buttons',  'ES', 200000,    'A', 'ACTIVE'),
    (100000002, 'Threads SL',         'ES', 200000,    'B', 'ACTIVE'),
    (100000003, 'Fabrics Co',         'ES', 200000,    'C', 'ON_PROBATION'),
    (100000004, 'Linen Works',        'ES', 210000,    'A', 'ACTIVE'),
    (100000005, 'Premium Wool',       'ES', 250000,    'B', 'ACTIVE'),
    (100000006, 'Eco Cottons',        'PT', 1500000,   'A', 'ACTIVE'),
    (100000007, 'Sustainable Silks',  'PT', 3000000,   'A', 'ACTIVE'),
    (100000008, 'Recycled Polyester', 'PT', 800000,    'B', 'ACTIVE'),
    (100000009, 'Fast Fashion Ltd',   'FR', 5000000,   'D', 'ON_PROBATION'),
    (100000010, 'Quality Leather',    'FR', 12000000,  'A', 'ACTIVE'),
    (100000011, 'Banned Supplier',    'FR', 9000000,   'E', 'DISQUALIFIED'),
    (100000012, 'Italian Wool',       'IT', 4500000,   'B', 'ACTIVE'),
    (100000013, 'Milan Buttons',      'IT', 1200000,   'C', 'ON_PROBATION'),
    (100000014, 'Roma Threads',       'IT', 950000,    'A', 'ACTIVE'),
    (100000015, 'Genoa Fabrics',      'IT', 2200000,   'D', 'ON_PROBATION'),
    (100000016, 'Berlin Cotton',      'DE', 7000000,   'A', 'ACTIVE'),
    (100000017, 'Munich Silk',        'DE', 5500000,   'B', 'ACTIVE'),
    (100000018, 'Hamburg Wool',       'DE', 1800000,   'C', 'ON_PROBATION'),
    (100000019, 'Lisbon Leather',     'PT', 6000000,   'E', 'ON_PROBATION'),
    (100000020, 'Madrid Textiles',    'ES', 8500000,   'A', 'ACTIVE')
ON CONFLICT (duns) DO NOTHING;

INSERT INTO candidates (duns, name, country, annual_turnover, state) VALUES
    (200000001, 'Pending Applicant 1', 'ES', 1500000, 'PENDING'),
    (200000002, 'Pending Applicant 2', 'PT', 2000000, 'PENDING'),
    (200000003, 'Refused Applicant',   'FR', 500000,  'REFUSED')
ON CONFLICT DO NOTHING;
