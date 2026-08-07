ALTER TABLE roles
    MODIFY COLUMN name ENUM(
        'SUPER_ADMIN',
        'SUPERVISOR',
        'BRANCH_MANAGER',
        'INVENTORY_CLERK',
        'CASHIER',
        'ACCOUNTANT',
        'CUSTOMER_SERVICE'
    ) NOT NULL;

INSERT IGNORE INTO roles (name, description, is_system_role)
VALUES (
    'SUPERVISOR',
    'Branch-scoped cash collection, sales oversight, stock receiving, returns history and held change',
    TRUE
);
