-- Repair legacy cashier accounts only when the tenant has exactly one active branch.
-- Multi-branch tenants are intentionally left untouched so sales are never posted to the wrong branch.
UPDATE users u
JOIN roles r ON r.id = u.role_id
JOIN (
    SELECT tenant_id, MIN(id) AS branch_id
    FROM branches
    WHERE is_active = TRUE
    GROUP BY tenant_id
    HAVING COUNT(*) = 1
) single_branch ON single_branch.tenant_id = u.tenant_id
SET u.branch_id = single_branch.branch_id
WHERE r.name = 'CASHIER'
  AND u.branch_id IS NULL;

CREATE INDEX idx_users_tenant_role_branch ON users (tenant_id, role_id, branch_id);

-- Existing branches created before the full branch service need stock rows and a till
-- before the mobile POS can open a shift cleanly.
INSERT INTO inventory (tenant_id, branch_id, product_id, quantity_on_hand, average_cost_usd, average_cost_zwg)
SELECT p.tenant_id, b.id, p.id, 0.0000, COALESCE(p.cost_price_usd, 0.0000), COALESCE(p.cost_price_zwg, 0.0000)
FROM products p
JOIN branches b ON b.tenant_id = p.tenant_id AND b.is_active = TRUE
LEFT JOIN inventory i ON i.tenant_id = p.tenant_id AND i.branch_id = b.id AND i.product_id = p.id
WHERE i.id IS NULL
  AND COALESCE(p.is_active, TRUE) = TRUE;

INSERT INTO cash_drawers (tenant_id, branch_id, name, is_active)
SELECT b.tenant_id, b.id, 'Till 1', TRUE
FROM branches b
LEFT JOIN cash_drawers d ON d.tenant_id = b.tenant_id AND d.branch_id = b.id AND d.is_active = TRUE
WHERE b.is_active = TRUE
  AND d.id IS NULL;
