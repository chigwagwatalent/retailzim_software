package com.retailzw.enums;

public enum UserRole {
    SUPER_ADMIN("Super Admin"),
    BRANCH_MANAGER("Branch Manager"),
    INVENTORY_CLERK("Inventory Clerk"),
    CASHIER("Cashier"),
    ACCOUNTANT("Accountant"),
    CUSTOMER_SERVICE("Customer Service");

    private final String displayName;

    UserRole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

