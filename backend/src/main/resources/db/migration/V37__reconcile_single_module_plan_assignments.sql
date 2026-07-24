-- Keep the tenant, active subscription, enabled module and branch context in
-- agreement. A package with exactly one supported business module is the
-- source of truth for existing tenants moved between retail and gas plans.

UPDATE tenant_subscriptions subscription
JOIN tenants tenant ON tenant.id = subscription.tenant_id
SET subscription.plan_id = tenant.plan_id
WHERE tenant.plan_id IS NOT NULL
  AND subscription.status IN ('ACTIVE', 'TRIAL')
  AND subscription.plan_id <> tenant.plan_id;

UPDATE tenants tenant
JOIN saas_plans plan ON plan.id = tenant.plan_id
SET tenant.business_mode = 'SINGLE_MODULE'
WHERE (
    FIND_IN_SET('GAS_MODULE', REPLACE(plan.allowed_modules, ' ', '')) > 0
    AND FIND_IN_SET('SHOP_MODULE', REPLACE(plan.allowed_modules, ' ', '')) = 0
  ) OR (
    FIND_IN_SET('SHOP_MODULE', REPLACE(plan.allowed_modules, ' ', '')) > 0
    AND FIND_IN_SET('GAS_MODULE', REPLACE(plan.allowed_modules, ' ', '')) = 0
  );

UPDATE branches branch
JOIN tenants tenant ON tenant.id = branch.tenant_id
JOIN saas_plans plan ON plan.id = tenant.plan_id
SET branch.module_type = 'GAS_MODULE'
WHERE branch.is_active = b'1'
  AND FIND_IN_SET('GAS_MODULE', REPLACE(plan.allowed_modules, ' ', '')) > 0
  AND FIND_IN_SET('SHOP_MODULE', REPLACE(plan.allowed_modules, ' ', '')) = 0;

UPDATE branches branch
JOIN tenants tenant ON tenant.id = branch.tenant_id
JOIN saas_plans plan ON plan.id = tenant.plan_id
SET branch.module_type = 'SHOP_MODULE'
WHERE branch.is_active = b'1'
  AND FIND_IN_SET('SHOP_MODULE', REPLACE(plan.allowed_modules, ' ', '')) > 0
  AND FIND_IN_SET('GAS_MODULE', REPLACE(plan.allowed_modules, ' ', '')) = 0;

INSERT INTO tenant_enabled_modules (tenant_id, module, status, created_at, updated_at)
SELECT tenant.id, 'GAS_MODULE', 'ENABLED', NOW(6), NOW(6)
FROM tenants tenant
JOIN saas_plans plan ON plan.id = tenant.plan_id
WHERE FIND_IN_SET('GAS_MODULE', REPLACE(plan.allowed_modules, ' ', '')) > 0
  AND FIND_IN_SET('SHOP_MODULE', REPLACE(plan.allowed_modules, ' ', '')) = 0
  AND NOT EXISTS (
    SELECT 1
    FROM tenant_enabled_modules existing
    WHERE existing.tenant_id = tenant.id
      AND existing.module = 'GAS_MODULE'
  );

INSERT INTO tenant_enabled_modules (tenant_id, module, status, created_at, updated_at)
SELECT tenant.id, 'SHOP_MODULE', 'ENABLED', NOW(6), NOW(6)
FROM tenants tenant
JOIN saas_plans plan ON plan.id = tenant.plan_id
WHERE FIND_IN_SET('SHOP_MODULE', REPLACE(plan.allowed_modules, ' ', '')) > 0
  AND FIND_IN_SET('GAS_MODULE', REPLACE(plan.allowed_modules, ' ', '')) = 0
  AND NOT EXISTS (
    SELECT 1
    FROM tenant_enabled_modules existing
    WHERE existing.tenant_id = tenant.id
      AND existing.module = 'SHOP_MODULE'
  );

UPDATE tenant_enabled_modules module_access
JOIN tenants tenant ON tenant.id = module_access.tenant_id
JOIN saas_plans plan ON plan.id = tenant.plan_id
SET module_access.status = CASE
      WHEN module_access.module = 'GAS_MODULE'
        AND FIND_IN_SET('GAS_MODULE', REPLACE(plan.allowed_modules, ' ', '')) > 0
        AND FIND_IN_SET('SHOP_MODULE', REPLACE(plan.allowed_modules, ' ', '')) = 0
        THEN 'ENABLED'
      WHEN module_access.module = 'SHOP_MODULE'
        AND FIND_IN_SET('SHOP_MODULE', REPLACE(plan.allowed_modules, ' ', '')) > 0
        AND FIND_IN_SET('GAS_MODULE', REPLACE(plan.allowed_modules, ' ', '')) = 0
        THEN 'ENABLED'
      ELSE 'DISABLED'
    END,
    module_access.updated_at = NOW(6)
WHERE module_access.module IN ('SHOP_MODULE', 'GAS_MODULE')
  AND ((
    FIND_IN_SET('GAS_MODULE', REPLACE(plan.allowed_modules, ' ', '')) > 0
    AND FIND_IN_SET('SHOP_MODULE', REPLACE(plan.allowed_modules, ' ', '')) = 0
  ) OR (
    FIND_IN_SET('SHOP_MODULE', REPLACE(plan.allowed_modules, ' ', '')) > 0
    AND FIND_IN_SET('GAS_MODULE', REPLACE(plan.allowed_modules, ' ', '')) = 0
  ));
