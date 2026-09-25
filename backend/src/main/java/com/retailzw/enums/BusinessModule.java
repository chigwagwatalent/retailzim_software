package com.retailzw.enums;

public enum BusinessModule {
    SHOP_MODULE("Retail Shop"),
    GAS_MODULE("Gas"),
    FUEL_MODULE("Fuel Station"),
    RESTAURANT_MODULE("Restaurant");

    private final String displayName;

    BusinessModule(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
