-- Repair tenants that selected one module before package synchronization began
-- preserving the explicit tenant choice. Branch type is the reliable historical
-- signal: gas-only signup creates a GAS_MODULE head-office branch.

INSERT INTO tenant_enabled_modules (tenant_id, module, status, created_at, updated_at)
SELECT DISTINCT t.id, 'SHOP_MODULE', 'ENABLED', NOW(6), NOW(6)
FROM tenants t
JOIN branches b ON b.tenant_id = t.id
WHERE t.business_mode = 'SINGLE_MODULE'
  AND b.is_active = b'1'
  AND b.module_type = 'SHOP_MODULE'
  AND NOT EXISTS (
    SELECT 1
    FROM tenant_enabled_modules existing
    WHERE existing.tenant_id = t.id
      AND existing.module = 'SHOP_MODULE'
  );

INSERT INTO tenant_enabled_modules (tenant_id, module, status, created_at, updated_at)
SELECT DISTINCT t.id, 'GAS_MODULE', 'ENABLED', NOW(6), NOW(6)
FROM tenants t
JOIN branches b ON b.tenant_id = t.id
WHERE t.business_mode = 'SINGLE_MODULE'
  AND b.is_active = b'1'
  AND b.module_type = 'GAS_MODULE'
  AND NOT EXISTS (
    SELECT 1
    FROM tenant_enabled_modules existing
    WHERE existing.tenant_id = t.id
      AND existing.module = 'GAS_MODULE'
  );

UPDATE tenant_enabled_modules m
JOIN tenants t ON t.id = m.tenant_id
SET m.status = CASE
    WHEN m.module = 'SHOP_MODULE' AND EXISTS (
      SELECT 1
      FROM branches b
      WHERE b.tenant_id = t.id
        AND b.is_active = b'1'
        AND b.module_type = 'SHOP_MODULE'
    ) THEN 'ENABLED'
    WHEN m.module = 'GAS_MODULE' AND EXISTS (
      SELECT 1
      FROM branches b
      WHERE b.tenant_id = t.id
        AND b.is_active = b'1'
        AND b.module_type = 'GAS_MODULE'
    ) THEN 'ENABLED'
    ELSE 'DISABLED'
  END,
  m.updated_at = NOW(6)
WHERE t.business_mode = 'SINGLE_MODULE'
  AND m.module IN ('SHOP_MODULE', 'GAS_MODULE')
  AND (
    SELECT COUNT(DISTINCT b2.module_type)
    FROM branches b2
    WHERE b2.tenant_id = t.id
      AND b2.is_active = b'1'
      AND b2.module_type IN ('SHOP_MODULE', 'GAS_MODULE')
  ) = 1;
