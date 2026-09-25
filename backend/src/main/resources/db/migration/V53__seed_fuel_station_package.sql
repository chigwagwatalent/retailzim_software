INSERT INTO saas_plans (
    name, code, description, price_usd, price_zwg, billing_cycle,
    max_branches, max_users, max_products, max_gas_tanks,
    allowed_modules, allow_mixed_modules, gas_reconciliation_enabled,
    features, is_active
)
SELECT
    'Fuel Station Growth',
    'FUEL_GROWTH',
    'Multi-branch fuel station operations with forecourt POS, wet-stock control, deliveries, shifts and reporting.',
    49.00,
    1715.00,
    'MONTHLY',
    10,
    100,
    5000,
    0,
    'FUEL_MODULE',
    b'0',
    b'0',
    JSON_ARRAY('Fuel cashier app', 'Pump and nozzle management', 'Tank wet-stock control', 'Shift reconciliation', 'Multi-branch reporting'),
    b'1'
WHERE NOT EXISTS (
    SELECT 1 FROM saas_plans WHERE code = 'FUEL_GROWTH'
);
