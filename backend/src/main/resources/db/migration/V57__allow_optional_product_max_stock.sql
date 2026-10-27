-- Product forms and the JPA model allow an unset maximum stock level.
-- Fresh V1 schemas enforced NOT NULL, unlike Hibernate-updated installations.
ALTER TABLE products MODIFY COLUMN max_stock_level DECIMAL(15,4) NULL DEFAULT NULL;
