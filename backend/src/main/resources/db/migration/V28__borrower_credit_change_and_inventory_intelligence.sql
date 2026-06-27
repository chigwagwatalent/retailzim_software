CREATE TABLE IF NOT EXISTS borrowers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    account_number VARCHAR(50) NOT NULL,
    full_name VARCHAR(200) NOT NULL,
    phone VARCHAR(40) NOT NULL,
    national_id VARCHAR(80),
    currency VARCHAR(5) NOT NULL DEFAULT 'USD',
    credit_limit DECIMAL(15,2) NOT NULL DEFAULT 0,
    current_balance DECIMAL(15,2) NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    notes TEXT,
    created_by BIGINT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_borrowers_tenant_account UNIQUE (tenant_id, account_number),
    INDEX idx_borrowers_tenant_phone (tenant_id, phone),
    INDEX idx_borrowers_tenant_active (tenant_id, is_active),
    CONSTRAINT fk_borrowers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS borrower_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    branch_id BIGINT,
    borrower_id BIGINT NOT NULL,
    sale_id BIGINT,
    cash_session_id BIGINT,
    transaction_type VARCHAR(20) NOT NULL,
    currency VARCHAR(5) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    balance_before DECIMAL(15,2) NOT NULL,
    balance_after DECIMAL(15,2) NOT NULL,
    offline_reference VARCHAR(100),
    notes TEXT,
    created_by BIGINT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_borrower_tx_offline UNIQUE (tenant_id, offline_reference),
    INDEX idx_borrower_tx_account (tenant_id, borrower_id, created_at),
    CONSTRAINT fk_borrower_tx_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_borrower_tx_borrower FOREIGN KEY (borrower_id) REFERENCES borrowers(id),
    CONSTRAINT fk_borrower_tx_sale FOREIGN KEY (sale_id) REFERENCES sales(id)
);

CREATE TABLE IF NOT EXISTS held_change (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    cash_session_id BIGINT,
    sale_id BIGINT,
    reference_number VARCHAR(60) NOT NULL,
    customer_name VARCHAR(200) NOT NULL,
    phone VARCHAR(40) NOT NULL,
    currency VARCHAR(5) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    offline_reference VARCHAR(100),
    notes TEXT,
    created_by BIGINT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    collected_by BIGINT,
    collected_cash_session_id BIGINT,
    collected_at DATETIME(6),
    cancelled_by BIGINT,
    cancelled_at DATETIME(6),
    CONSTRAINT uk_held_change_reference UNIQUE (reference_number),
    CONSTRAINT uk_held_change_offline UNIQUE (tenant_id, offline_reference),
    INDEX idx_held_change_search (tenant_id, status, phone),
    CONSTRAINT fk_held_change_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_held_change_sale FOREIGN KEY (sale_id) REFERENCES sales(id)
);

ALTER TABLE sales
    ADD COLUMN borrower_id BIGINT NULL AFTER customer_id,
    ADD COLUMN sale_type VARCHAR(20) NOT NULL DEFAULT 'STANDARD' AFTER borrower_id;

ALTER TABLE products
    ADD COLUMN tracking_mode VARCHAR(20) NOT NULL DEFAULT 'NONE' AFTER is_service,
    ADD COLUMN expiry_tracking BOOLEAN NOT NULL DEFAULT FALSE AFTER tracking_mode;

CREATE TABLE IF NOT EXISTS inventory_lots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    batch_number VARCHAR(100),
    serial_number VARCHAR(150),
    expiry_date DATE,
    quantity_on_hand DECIMAL(15,4) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    supplier_id BIGINT,
    purchase_order_id BIGINT,
    notes TEXT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_inventory_lots_lookup (tenant_id, branch_id, product_id, status),
    INDEX idx_inventory_lots_expiry (tenant_id, expiry_date, status),
    CONSTRAINT fk_inventory_lots_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_inventory_lots_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS stock_variance_investigations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    stocktake_session_id BIGINT,
    stocktake_item_id BIGINT,
    product_id BIGINT NOT NULL,
    system_quantity DECIMAL(15,4) NOT NULL,
    counted_quantity DECIMAL(15,4) NOT NULL,
    variance DECIMAL(15,4) NOT NULL,
    reason VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    assigned_to BIGINT,
    resolution_notes TEXT,
    created_by BIGINT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    resolved_by BIGINT,
    resolved_at DATETIME(6),
    INDEX idx_variance_tenant_status (tenant_id, branch_id, status),
    CONSTRAINT fk_variance_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_variance_product FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE TABLE IF NOT EXISTS purchase_order_approvals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    purchase_order_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    comments TEXT,
    acted_by BIGINT NOT NULL,
    acted_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_po_approvals_order (tenant_id, purchase_order_id, acted_at),
    CONSTRAINT fk_po_approvals_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_po_approvals_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id) ON DELETE CASCADE
);
