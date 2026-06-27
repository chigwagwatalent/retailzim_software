-- The current SaleItem entity stores the sold SKU snapshot in product_sku.
-- Older schemas still have a legacy sku column marked NOT NULL, which blocks POS sales.
ALTER TABLE sale_items
    MODIFY COLUMN sku VARCHAR(100) NULL;
