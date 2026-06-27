-- =============================================================
-- RetailZW SaaS Platform - Complete Database Schema
-- Multi-tenant, USD + ZWG dual currency, Zimbabwe retail market
-- =============================================================

SET FOREIGN_KEY_CHECKS = 0;
SET sql_mode = 'STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';

-- =============================================================
-- SAAS PLATFORM TABLES (Platform-level, no tenant_id)
-- =============================================================

CREATE TABLE IF NOT EXISTS saas_plans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    price_usd DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    price_zwg DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    billing_cycle ENUM('MONTHLY','QUARTERLY','ANNUALLY') NOT NULL DEFAULT 'MONTHLY',
    max_branches INT NOT NULL DEFAULT 1,
    max_users INT NOT NULL DEFAULT 5,
    max_products INT NOT NULL DEFAULT 500,
    features JSON,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tenants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_code VARCHAR(20) NOT NULL UNIQUE COMMENT 'Short unique identifier e.g. SHOP001',
    company_name VARCHAR(200) NOT NULL,
    registration_number VARCHAR(100),
    vat_number VARCHAR(100),
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(30),
    address TEXT,
    city VARCHAR(100),
    country VARCHAR(100) DEFAULT 'Zimbabwe',
    logo_url VARCHAR(500),
    website VARCHAR(255),
    status ENUM('PENDING','ACTIVE','SUSPENDED','CANCELLED') NOT NULL DEFAULT 'PENDING',
    plan_id BIGINT,
    subscription_start DATE,
    subscription_end DATE,
    trial_end DATE,
    default_currency ENUM('USD','ZWG') NOT NULL DEFAULT 'USD',
    secondary_currency ENUM('USD','ZWG') DEFAULT 'ZWG',
    timezone VARCHAR(100) NOT NULL DEFAULT 'Africa/Harare',
    default_tax_rate DECIMAL(5,2) NOT NULL DEFAULT 15.00,
    receipt_footer TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenants_status (status),
    INDEX idx_tenants_plan (plan_id),
    FOREIGN KEY (plan_id) REFERENCES saas_plans(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS saas_admins (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tenant_subscriptions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    currency ENUM('USD','ZWG') NOT NULL DEFAULT 'USD',
    amount DECIMAL(14,2) NOT NULL,
    billing_cycle ENUM('MONTHLY','QUARTERLY','ANNUALLY') NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status ENUM('ACTIVE','EXPIRED','CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    payment_reference VARCHAR(255),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sub_tenant (tenant_id),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    FOREIGN KEY (plan_id) REFERENCES saas_plans(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================
-- TENANT-LEVEL TABLES (all have tenant_id)
-- =============================================================

CREATE TABLE IF NOT EXISTS branches (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    branch_code VARCHAR(20) NOT NULL COMMENT 'e.g. HRE01, BLW01',
    name VARCHAR(200) NOT NULL,
    address TEXT,
    city VARCHAR(100),
    phone VARCHAR(30),
    email VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_branch_code_tenant (tenant_id, branch_code),
    INDEX idx_branches_tenant (tenant_id),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name ENUM('SUPER_ADMIN','BRANCH_MANAGER','INVENTORY_CLERK','CASHIER','ACCOUNTANT','CUSTOMER_SERVICE') NOT NULL,
    description VARCHAR(255),
    is_system_role BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    branch_id BIGINT NULL COMMENT 'NULL for SUPER_ADMIN (HQ)',
    role_id BIGINT NOT NULL,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone VARCHAR(30),
    employee_number VARCHAR(50),
    pin_hash VARCHAR(255) COMMENT '4-digit PIN for Flutter app lock screen',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    force_password_change BOOLEAN NOT NULL DEFAULT TRUE,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP NULL,
    last_login_at TIMESTAMP NULL,
    last_login_ip VARCHAR(45),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_username_tenant (tenant_id, username),
    UNIQUE KEY uk_email_tenant (tenant_id, email),
    INDEX idx_users_tenant (tenant_id),
    INDEX idx_users_branch (branch_id),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE SET NULL,
    FOREIGN KEY (role_id) REFERENCES roles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_refresh_user (user_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================
-- PRODUCT CATALOGUE
-- =============================================================

CREATE TABLE IF NOT EXISTS product_categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    name VARCHAR(200) NOT NULL,
    code VARCHAR(50),
    description TEXT,
    image_url VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_categories_tenant (tenant_id),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    FOREIGN KEY (parent_id) REFERENCES product_categories(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS units_of_measure (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    abbreviation VARCHAR(10) NOT NULL,
    is_decimal BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'True for kg, litre — allows decimal quantities',
    INDEX idx_uom_tenant (tenant_id),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    category_id BIGINT,
    uom_id BIGINT,
    sku VARCHAR(100) NOT NULL,
    barcode VARCHAR(100),
    name VARCHAR(300) NOT NULL,
    description TEXT,
    cost_price_usd DECIMAL(14,4) NOT NULL DEFAULT 0.0000,
    selling_price_usd DECIMAL(14,4) NOT NULL DEFAULT 0.0000,
    cost_price_zwg DECIMAL(14,4) NOT NULL DEFAULT 0.0000,
    selling_price_zwg DECIMAL(14,4) NOT NULL DEFAULT 0.0000,
    tax_rate DECIMAL(5,2) NOT NULL DEFAULT 15.00,
    is_taxable BOOLEAN NOT NULL DEFAULT TRUE,
    reorder_level DECIMAL(14,4) NOT NULL DEFAULT 5.0000,
    max_stock_level DECIMAL(14,4) NOT NULL DEFAULT 100.0000,
    image_url VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    has_variants BOOLEAN NOT NULL DEFAULT FALSE,
    is_service BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Services not tracked in inventory',
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sku_tenant (tenant_id, sku),
    UNIQUE KEY uk_barcode_tenant (tenant_id, barcode),
    INDEX idx_products_tenant (tenant_id),
    INDEX idx_products_category (category_id),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    FOREIGN KEY (category_id) REFERENCES product_categories(id) ON DELETE SET NULL,
    FOREIGN KEY (uom_id) REFERENCES units_of_measure(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS product_variants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    variant_name VARCHAR(200) NOT NULL COMMENT 'e.g. Size: Large, Colour: Red',
    sku_suffix VARCHAR(50),
    barcode VARCHAR(100),
    price_modifier_usd DECIMAL(14,4) NOT NULL DEFAULT 0.0000,
    price_modifier_zwg DECIMAL(14,4) NOT NULL DEFAULT 0.0000,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    INDEX idx_variants_product (product_id),
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS product_branch_pricing (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    selling_price_usd DECIMAL(14,4) NOT NULL,
    selling_price_zwg DECIMAL(14,4) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_product_branch (product_id, branch_id),
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================
-- INVENTORY
-- =============================================================

CREATE TABLE IF NOT EXISTS inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity_on_hand DECIMAL(14,4) NOT NULL DEFAULT 0.0000,
    quantity_reserved DECIMAL(14,4) NOT NULL DEFAULT 0.0000,
    quantity_on_order DECIMAL(14,4) NOT NULL DEFAULT 0.0000,
    average_cost_usd DECIMAL(14,4) NOT NULL DEFAULT 0.0000,
    average_cost_zwg DECIMAL(14,4) NOT NULL DEFAULT 0.0000,
    last_counted_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_inventory_branch_product (branch_id, product_id),
    INDEX idx_inventory_tenant (tenant_id),
    INDEX idx_inventory_product (product_id),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS inventory_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    type ENUM('SALE','PURCHASE','RETURN','ADJUSTMENT','TRANSFER_OUT','TRANSFER_IN','STOCKTAKE','WRITE_OFF','OPENING') NOT NULL,
    quantity DECIMAL(14,4) NOT NULL COMMENT 'Positive = in, Negative = out',
    quantity_before DECIMAL(14,4) NOT NULL,
    quantity_after DECIMAL(14,4) NOT NULL,
    unit_cost_usd DECIMAL(14,4),
    unit_cost_zwg DECIMAL(14,4),
    reference_type VARCHAR(50) COMMENT 'SALE, PURCHASE_ORDER, TRANSFER, ADJUSTMENT',
    reference_id BIGINT,
    reason VARCHAR(255),
    notes TEXT,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_inv_tx_tenant (tenant_id),
    INDEX idx_inv_tx_branch (branch_id),
    INDEX idx_inv_tx_product (product_id),
    INDEX idx_inv_tx_type (type),
    INDEX idx_inv_tx_created_at (created_at),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS inventory_adjustments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity_change DECIMAL(14,4) NOT NULL,
    reason ENUM('DAMAGE','WRITE_OFF','THEFT','CORRECTION','INITIAL_STOCK','OTHER') NOT NULL,
    notes TEXT,
    approved_by BIGINT,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_adj_tenant (tenant_id),
    INDEX idx_adj_branch (branch_id),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS stock_transfers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    reference_number VARCHAR(50) NOT NULL UNIQUE,
    from_branch_id BIGINT NOT NULL,
    to_branch_id BIGINT NOT NULL,
    status ENUM('PENDING','IN_TRANSIT','RECEIVED','CANCELLED') NOT NULL DEFAULT 'PENDING',
    notes TEXT,
    dispatched_by BIGINT,
    dispatched_at TIMESTAMP NULL,
    received_by BIGINT,
    received_at TIMESTAMP NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_transfers_tenant (tenant_id),
    INDEX idx_transfers_from (from_branch_id),
    INDEX idx_transfers_to (to_branch_id),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    FOREIGN KEY (from_branch_id) REFERENCES branches(id),
    FOREIGN KEY (to_branch_id) REFERENCES branches(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS stock_transfer_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transfer_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity_sent DECIMAL(14,4) NOT NULL,
    quantity_received DECIMAL(14,4),
    FOREIGN KEY (transfer_id) REFERENCES stock_transfers(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS stocktake_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    reference_number VARCHAR(50) NOT NULL,
    status ENUM('IN_PROGRESS','PENDING_APPROVAL','APPROVED','CANCELLED') NOT NULL DEFAULT 'IN_PROGRESS',
    type ENUM('FULL','PARTIAL') NOT NULL DEFAULT 'FULL',
    notes TEXT,
    submitted_by BIGINT,
    submitted_at TIMESTAMP NULL,
    approved_by BIGINT,
    approved_at TIMESTAMP NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_stocktake_tenant (tenant_id),
    INDEX idx_stocktake_branch (branch_id),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS stocktake_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    system_quantity DECIMAL(14,4) NOT NULL,
    counted_quantity DECIMAL(14,4),
    variance DECIMAL(14,4) AS (counted_quantity - system_quantity) STORED,
    variance_comment TEXT,
    FOREIGN KEY (session_id) REFERENCES stocktake_sessions(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================
-- SUPPLIERS & PURCHASING
-- =============================================================

CREATE TABLE IF NOT EXISTS suppliers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    contact_name VARCHAR(200),
    email VARCHAR(255),
    phone VARCHAR(30),
    address TEXT,
    city VARCHAR(100),
    vat_number VARCHAR(100),
    payment_terms_days INT NOT NULL DEFAULT 30,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_suppliers_tenant (tenant_id),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS product_suppliers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    supplier_product_code VARCHAR(100),
    purchase_price_usd DECIMAL(14,4),
    purchase_price_zwg DECIMAL(14,4),
    lead_time_days INT DEFAULT 7,
    is_preferred BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE KEY uk_product_supplier (product_id, supplier_id),
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    FOREIGN KEY (supplier_id) REFERENCES suppliers(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS purchase_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    po_number VARCHAR(50) NOT NULL UNIQUE,
    status ENUM('DRAFT','SUBMITTED','APPROVED','ORDERED','PARTIAL','RECEIVED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
    currency ENUM('USD','ZWG') NOT NULL DEFAULT 'USD',
    total_amount DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    notes TEXT,
    expected_delivery_date DATE,
    approved_by BIGINT,
    approved_at TIMESTAMP NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_po_tenant (tenant_id),
    INDEX idx_po_branch (branch_id),
    INDEX idx_po_supplier (supplier_id),
    INDEX idx_po_status (status),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE CASCADE,
    FOREIGN KEY (supplier_id) REFERENCES suppliers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS purchase_order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    po_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity_ordered DECIMAL(14,4) NOT NULL,
    quantity_received DECIMAL(14,4) NOT NULL DEFAULT 0.0000,
    unit_price_usd DECIMAL(14,4) NOT NULL,
    unit_price_zwg DECIMAL(14,4) NOT NULL DEFAULT 0.0000,
    line_total_usd DECIMAL(14,2) AS (quantity_ordered * unit_price_usd) STORED,
    FOREIGN KEY (po_id) REFERENCES purchase_orders(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS goods_received_notes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    po_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    grn_number VARCHAR(50) NOT NULL UNIQUE,
    notes TEXT,
    received_by BIGINT NOT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_grn_tenant (tenant_id),
    INDEX idx_grn_po (po_id),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    FOREIGN KEY (po_id) REFERENCES purchase_orders(id),
    FOREIGN KEY (branch_id) REFERENCES branches(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================
-- CUSTOMERS & LOYALTY
-- =============================================================

CREATE TABLE IF NOT EXISTS customers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    loyalty_card_number VARCHAR(20) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(30),
    date_of_birth DATE,
    address TEXT,
    loyalty_tier ENUM('BRONZE','SILVER','GOLD','PLATINUM') NOT NULL DEFAULT 'BRONZE',
    loyalty_points_balance INT NOT NULL DEFAULT 0,
    total_spent_usd DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    total_spent_zwg DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    registered_branch_id BIGINT,
    registered_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_loyalty_card_tenant (tenant_id, loyalty_card_number),
    INDEX idx_customers_tenant (tenant_id),
    INDEX idx_customers_phone (phone),
    INDEX idx_customers_email (email),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    FOREIGN KEY (registered_branch_id) REFERENCES branches(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS loyalty_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    type ENUM('EARN','REDEEM','ADJUST','EXPIRE','REVERSE') NOT NULL,
    points INT NOT NULL COMMENT 'Positive = credit, Negative = debit',
    points_balance_after INT NOT NULL,
    reference_type VARCHAR(50),
    reference_id BIGINT,
    reason VARCHAR(255),
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL,
    INDEX idx_loyalty_tx_tenant (tenant_id),
    INDEX idx_loyalty_tx_customer (customer_id),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS store_credits (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    amount_usd DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    amount_zwg DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    reference_type VARCHAR(50),
    reference_id BIGINT,
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_store_credit_customer (customer_id),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================
-- PROMOTIONS & DISCOUNTS
-- =============================================================

CREATE TABLE IF NOT EXISTS promotions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    branch_id BIGINT NULL COMMENT 'NULL = all branches',
    name VARCHAR(200) NOT NULL,
    description TEXT,
    type ENUM('PERCENTAGE','FIXED_AMOUNT','BUY_X_GET_Y','BUNDLE','HAPPY_HOUR') NOT NULL,
    discount_value DECIMAL(10,4),
    buy_quantity INT,
    get_quantity INT,
    applies_to ENUM('ALL','CATEGORY','PRODUCT','LOYALTY_TIER') NOT NULL DEFAULT 'ALL',
    category_id BIGINT,
    product_id BIGINT,
    loyalty_tier ENUM('BRONZE','SILVER','GOLD','PLATINUM'),
    minimum_purchase_usd DECIMAL(14,2) DEFAULT 0.00,
    minimum_purchase_zwg DECIMAL(14,2) DEFAULT 0.00,
    usage_limit INT,
    usage_count INT NOT NULL DEFAULT 0,
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    happy_hour_start TIME,
    happy_hour_end TIME,
    happy_hour_days VARCHAR(20) COMMENT 'e.g. 1,2,3,4,5 for Mon-Fri',
    is_stackable BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_promotions_tenant (tenant_id),
    INDEX idx_promotions_dates (start_date, end_date),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE SET NULL,
    FOREIGN KEY (category_id) REFERENCES product_categories(id) ON DELETE SET NULL,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS coupon_codes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    promotion_id BIGINT NOT NULL,
    code VARCHAR(50) NOT NULL,
    is_single_use BOOLEAN NOT NULL DEFAULT TRUE,
    used_count INT NOT NULL DEFAULT 0,
    max_uses INT NOT NULL DEFAULT 1,
    used_by_customer_id BIGINT,
    used_at TIMESTAMP NULL,
    expires_at TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE KEY uk_coupon_code_tenant (tenant_id, code),
    INDEX idx_coupon_promotion (promotion_id),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    FOREIGN KEY (promotion_id) REFERENCES promotions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================
-- CASH MANAGEMENT
-- =============================================================

CREATE TABLE IF NOT EXISTS cash_drawers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL COMMENT 'e.g. Till 1, Express Checkout',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_drawers_branch (branch_id),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cash_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    drawer_id BIGINT NOT NULL,
    cashier_id BIGINT NOT NULL,
    status ENUM('OPEN','CLOSED') NOT NULL DEFAULT 'OPEN',
    opening_float_usd DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    opening_float_zwg DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    closing_actual_usd DECIMAL(14,2),
    closing_actual_zwg DECIMAL(14,2),
    variance_usd DECIMAL(14,2) AS (closing_actual_usd - (opening_float_usd)) STORED,
    notes TEXT,
    opened_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMP NULL,
    INDEX idx_cash_sessions_tenant (tenant_id),
    INDEX idx_cash_sessions_branch (branch_id),
    INDEX idx_cash_sessions_cashier (cashier_id),
    INDEX idx_cash_sessions_status (status),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE CASCADE,
    FOREIGN KEY (drawer_id) REFERENCES cash_drawers(id),
    FOREIGN KEY (cashier_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cash_movements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    type ENUM('CASH_IN','CASH_OUT','SAFE_DROP') NOT NULL,
    currency ENUM('USD','ZWG') NOT NULL DEFAULT 'USD',
    amount DECIMAL(14,2) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    approved_by BIGINT,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_cash_movements_session (session_id),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    FOREIGN KEY (session_id) REFERENCES cash_sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================
-- POINT OF SALE - SALES
-- =============================================================

CREATE TABLE IF NOT EXISTS sales (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    cash_session_id BIGINT NOT NULL,
    receipt_number VARCHAR(50) NOT NULL UNIQUE,
    cashier_id BIGINT NOT NULL,
    customer_id BIGINT NULL,
    status ENUM('COMPLETED','VOIDED','REFUNDED','PARTIAL_REFUND') NOT NULL DEFAULT 'COMPLETED',
    currency ENUM('USD','ZWG') NOT NULL DEFAULT 'USD',
    subtotal DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    discount_total DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    tax_total DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    grand_total DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    cost_of_goods DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    loyalty_points_earned INT NOT NULL DEFAULT 0,
    loyalty_points_redeemed INT NOT NULL DEFAULT 0,
    coupon_code VARCHAR(50),
    void_reason TEXT,
    voided_by BIGINT,
    voided_at TIMESTAMP NULL,
    is_offline_sale BOOLEAN NOT NULL DEFAULT FALSE,
    offline_created_at TIMESTAMP NULL,
    synced_at TIMESTAMP NULL,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sales_tenant (tenant_id),
    INDEX idx_sales_branch (branch_id),
    INDEX idx_sales_cashier (cashier_id),
    INDEX idx_sales_customer (customer_id),
    INDEX idx_sales_status (status),
    INDEX idx_sales_created_at (created_at),
    INDEX idx_sales_receipt (receipt_number),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE CASCADE,
    FOREIGN KEY (cash_session_id) REFERENCES cash_sessions(id),
    FOREIGN KEY (cashier_id) REFERENCES users(id),
    FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sale_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sale_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(300) NOT NULL COMMENT 'Snapshot at time of sale',
    sku VARCHAR(100) NOT NULL,
    quantity DECIMAL(14,4) NOT NULL,
    unit_price DECIMAL(14,4) NOT NULL,
    discount_amount DECIMAL(14,4) NOT NULL DEFAULT 0.0000,
    tax_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    tax_amount DECIMAL(14,4) NOT NULL DEFAULT 0.0000,
    line_total DECIMAL(14,2) NOT NULL,
    cost_price DECIMAL(14,4) NOT NULL DEFAULT 0.0000,
    promotion_id BIGINT NULL,
    INDEX idx_sale_items_sale (sale_id),
    INDEX idx_sale_items_product (product_id),
    FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sale_payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sale_id BIGINT NOT NULL,
    method ENUM('CASH','CARD','ECOCASH','ONEMONEY','INNBUCKS','LOYALTY_POINTS','STORE_CREDIT','OTHER') NOT NULL,
    currency ENUM('USD','ZWG') NOT NULL DEFAULT 'USD',
    amount DECIMAL(14,2) NOT NULL,
    amount_tendered DECIMAL(14,2) COMMENT 'For cash payments',
    change_given DECIMAL(14,2) COMMENT 'For cash payments',
    reference_number VARCHAR(100) COMMENT 'Card approval code or mobile money ref',
    mobile_number VARCHAR(30) COMMENT 'For mobile money',
    INDEX idx_payments_sale (sale_id),
    FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================
-- RETURNS & REFUNDS
-- =============================================================

CREATE TABLE IF NOT EXISTS returns (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    original_sale_id BIGINT NOT NULL,
    return_number VARCHAR(50) NOT NULL UNIQUE,
    cashier_id BIGINT NOT NULL,
    customer_id BIGINT NULL,
    status ENUM('PENDING_APPROVAL','COMPLETED','REJECTED') NOT NULL DEFAULT 'COMPLETED',
    reason ENUM('DEFECTIVE','WRONG_ITEM','CHANGED_MIND','DAMAGED_IN_TRANSIT','OTHER') NOT NULL,
    reason_notes TEXT,
    refund_method ENUM('CASH','CARD','STORE_CREDIT','EXCHANGE') NOT NULL,
    total_refund_usd DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    total_refund_zwg DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    loyalty_points_reversed INT NOT NULL DEFAULT 0,
    manager_id BIGINT COMMENT 'Who approved if required',
    manager_approved_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_returns_tenant (tenant_id),
    INDEX idx_returns_branch (branch_id),
    INDEX idx_returns_sale (original_sale_id),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE CASCADE,
    FOREIGN KEY (original_sale_id) REFERENCES sales(id),
    FOREIGN KEY (cashier_id) REFERENCES users(id),
    FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS return_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    return_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity_returned DECIMAL(14,4) NOT NULL,
    unit_price DECIMAL(14,4) NOT NULL,
    refund_amount DECIMAL(14,2) NOT NULL,
    FOREIGN KEY (return_id) REFERENCES returns(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================
-- STAFF & HR
-- =============================================================

CREATE TABLE IF NOT EXISTS staff_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL UNIQUE,
    branch_id BIGINT NOT NULL,
    employee_number VARCHAR(50) NOT NULL,
    department VARCHAR(100),
    employment_type ENUM('FULL_TIME','PART_TIME','CONTRACT','CASUAL') NOT NULL DEFAULT 'FULL_TIME',
    hire_date DATE NOT NULL,
    termination_date DATE NULL,
    basic_salary_usd DECIMAL(14,2),
    basic_salary_zwg DECIMAL(14,2),
    national_id VARCHAR(100),
    emergency_contact_name VARCHAR(200),
    emergency_contact_phone VARCHAR(30),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_staff_tenant (tenant_id),
    INDEX idx_staff_branch (branch_id),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS attendance_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    date DATE NOT NULL,
    clock_in_time TIMESTAMP NULL,
    clock_out_time TIMESTAMP NULL,
    hours_worked DECIMAL(5,2) AS (TIMESTAMPDIFF(MINUTE, clock_in_time, clock_out_time) / 60.0) STORED,
    source ENUM('APP','MANUAL','BIOMETRIC') NOT NULL DEFAULT 'APP',
    notes TEXT,
    recorded_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_attendance_user_date (user_id, date),
    INDEX idx_attendance_tenant (tenant_id),
    INDEX idx_attendance_branch (branch_id),
    INDEX idx_attendance_date (date),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS leave_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    type ENUM('ANNUAL','SICK','MATERNITY','PATERNITY','UNPAID','OTHER') NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    working_days INT NOT NULL DEFAULT 0,
    reason TEXT,
    status ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
    reviewed_by BIGINT,
    reviewed_at TIMESTAMP NULL,
    review_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_leave_tenant (tenant_id),
    INDEX idx_leave_user (user_id),
    INDEX idx_leave_status (status),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================
-- NOTIFICATIONS
-- =============================================================

CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    type ENUM('LOW_STOCK','VOID_ALERT','LARGE_TRANSACTION','PO_APPROVAL_NEEDED','PO_APPROVED','DELIVERY_RECEIVED','CASH_VARIANCE','RETURN_AWAITING_APPROVAL','STOCK_TRANSFER_INCOMING','LOYALTY_TIER_UPGRADE','SYSTEM_ALERT','LEAVE_REQUEST','ATTENDANCE') NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    reference_type VARCHAR(50),
    reference_id BIGINT,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_notifications_user (user_id),
    INDEX idx_notifications_tenant (tenant_id),
    INDEX idx_notifications_read (is_read),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================
-- AUDIT LOG
-- =============================================================

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT,
    user_id BIGINT,
    username VARCHAR(100) NOT NULL,
    branch_id BIGINT,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT,
    entity_ref VARCHAR(100) COMMENT 'Human-readable reference, e.g. receipt number',
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    before_data JSON,
    after_data JSON,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_tenant (tenant_id),
    INDEX idx_audit_user (user_id),
    INDEX idx_audit_action (action),
    INDEX idx_audit_entity (entity_type, entity_id),
    INDEX idx_audit_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================
-- SYSTEM SETTINGS
-- =============================================================

CREATE TABLE IF NOT EXISTS system_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    category VARCHAR(100) NOT NULL,
    setting_key VARCHAR(100) NOT NULL,
    setting_value TEXT NOT NULL,
    description VARCHAR(255),
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_settings_key_tenant (tenant_id, category, setting_key),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================
-- OFFLINE SALE QUEUE (for Flutter sync)
-- =============================================================

CREATE TABLE IF NOT EXISTS offline_sale_queue (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    device_id VARCHAR(100) NOT NULL,
    offline_sale_uuid VARCHAR(36) NOT NULL UNIQUE COMMENT 'UUID generated by Flutter device',
    sale_data JSON NOT NULL,
    status ENUM('PENDING','PROCESSING','SYNCED','FAILED') NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    synced_at TIMESTAMP NULL,
    INDEX idx_offline_queue_tenant (tenant_id),
    INDEX idx_offline_queue_status (status),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================
-- INITIAL DATA
-- =============================================================

INSERT IGNORE INTO saas_plans (name, code, description, price_usd, price_zwg, billing_cycle, max_branches, max_users, max_products, is_active) VALUES
('Starter', 'STARTER', 'Perfect for small single-branch shops', 29.00, 870.00, 'MONTHLY', 1, 5, 500, TRUE),
('Growth', 'GROWTH', 'For growing businesses with multiple branches', 79.00, 2370.00, 'MONTHLY', 5, 25, 5000, TRUE),
('Enterprise', 'ENTERPRISE', 'Unlimited branches and users for large chains', 199.00, 5970.00, 'MONTHLY', 999, 999, 999999, TRUE);

INSERT IGNORE INTO roles (name, description) VALUES
('SUPER_ADMIN', 'Full access to all branches, all modules, all data'),
('BRANCH_MANAGER', 'Full access to assigned branches'),
('INVENTORY_CLERK', 'Manages stock levels and goods receiving'),
('CASHIER', 'Processes sales and manages cash sessions'),
('ACCOUNTANT', 'Read-only access to financial reports'),
('CUSTOMER_SERVICE', 'Customer management and loyalty adjustments');

INSERT IGNORE INTO saas_admins (username, email, password_hash, first_name, last_name, is_active) VALUES
('superadmin', 'admin@retailzw.co.zw', '$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lha', 'Platform', 'Admin', TRUE);
-- Default password: Admin@1234 (BCrypt hash)

SET FOREIGN_KEY_CHECKS = 1;
