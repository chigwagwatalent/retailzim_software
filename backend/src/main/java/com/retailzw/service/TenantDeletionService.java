package com.retailzw.service;

import com.retailzw.model.Tenant;
import com.retailzw.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/** Explicit, tenant-scoped deletion order. Never disables referential integrity. */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantDeletionService {
    private final TenantRepository tenants;
    private final JdbcTemplate jdbc;

    static final List<String> OWNED_TABLES = List.of(
        "billing_reminder_logs", "subscription_payments", "smile_pay_checkouts", "tenant_subscriptions",
        "tenant_chat_messages", "notifications", "offline_sale_queue", "audit_logs",
        "fuel_expenses", "fuel_audit_events", "fuel_stock_movements", "fuel_deliveries", "fuel_sales", "fuel_shifts",
        "fuel_prices", "fuel_nozzles", "fuel_pumps", "fuel_tanks", "fuel_grades",
        "gas_sale_payments", "gas_sale_tank_allocations", "gas_shift_tanks", "gas_stock_adjustments",
        "retail_expenses", "gas_expenses", "gas_restocks", "held_change", "borrower_transactions", "gas_sales", "gas_shifts", "gas_prices", "gas_tanks",
        "returns", "sales", "cash_movements", "cash_sessions", "cash_drawers",
        "goods_received_notes", "purchase_order_approvals", "purchase_orders", "stock_transfers", "stocktake_sessions",
        "stock_variance_investigations", "inventory_lots", "inventory_adjustments", "inventory_transactions", "inventory",
        "coupon_codes", "promotions", "product_suppliers", "product_branch_pricing", "product_variants", "product_wholesale_pricing",
        "products", "product_categories", "units_of_measure", "suppliers", "loyalty_transactions", "store_credits", "customers", "borrowers",
        "staff_profiles", "attendance_records", "leave_requests", "users", "branches", "tenant_exchange_rates", "system_settings", "tenant_enabled_modules");

    @PreAuthorize("hasRole('SAAS_ADMIN')")
    @Transactional(timeout = 120)
    public void delete(Long id, String confirmation, boolean acknowledged, String actor) {
        Tenant tenant = tenants.findLockedById(id).orElseThrow(() -> new IllegalArgumentException("Shop not found."));
        if (tenant.getStatus() != Tenant.TenantStatus.SUSPENDED && tenant.getStatus() != Tenant.TenantStatus.CANCELLED)
            throw new IllegalArgumentException("Suspend the shop first, then stop its tills before deleting it.");
        if (!acknowledged || !("DELETE " + tenant.getTenantCode()).equals(confirmation))
            throw new IllegalArgumentException("Type DELETE followed by the exact shop code and confirm the permanent deletion.");
        // Child tables without tenant_id must be cleared before their parent rows.
        child("payment_notification_outbox", "checkout_id", "smile_pay_checkouts", id);
        jdbc.update("DELETE FROM password_reset_tokens WHERE account_type = 'SHOP_USER' AND account_id IN (SELECT id FROM users WHERE tenant_id = ?)", id);
        child("refresh_tokens", "user_id", "users", id);
        child("return_items", "return_id", "returns", id);
        child("sale_items", "sale_id", "sales", id);
        child("sale_payments", "sale_id", "sales", id);
        child("purchase_order_items", "po_id", "purchase_orders", id);
        child("stock_transfer_items", "transfer_id", "stock_transfers", id);
        child("stocktake_items", "session_id", "stocktake_sessions", id);
        child("fuel_sale_payments", "sale_id", "fuel_sales", id);
        child("fuel_shift_nozzle_meters", "shift_id", "fuel_shifts", id);
        child("fuel_shift_tank_dips", "shift_id", "fuel_shifts", id);
        for (String table : OWNED_TABLES) jdbc.update("DELETE FROM `" + table + "` WHERE tenant_id = ?", id);
        if (jdbc.update("DELETE FROM tenants WHERE id = ?", id) != 1) throw new IllegalStateException("Shop deletion did not complete.");
        // Database business records are purged; operational logs remain subject to retention.
        log.warn("Platform administrator {} requested permanent deletion of tenant {}", actor, id);
    }

    private void child(String table, String foreignKey, String parent, Long id) {
        jdbc.update("DELETE FROM `" + table + "` WHERE `" + foreignKey + "` IN (SELECT id FROM `" + parent + "` WHERE tenant_id = ?)", id);
    }
}
