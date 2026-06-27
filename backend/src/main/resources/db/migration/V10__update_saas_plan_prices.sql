UPDATE saas_plans
SET price_usd = 30.00,
    price_zwg = 900.00
WHERE code = 'STARTER';

UPDATE saas_plans
SET price_usd = 60.00,
    price_zwg = 1800.00
WHERE code = 'GROWTH';

UPDATE saas_plans
SET price_usd = 150.00,
    price_zwg = 4500.00
WHERE code = 'ENTERPRISE';
