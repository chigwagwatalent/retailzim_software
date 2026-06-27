package com.retailzw.enums;

public enum CurrencyCode {
    USD("$", "US Dollar"),
    ZWG("ZWG", "Zimbabwe Gold");

    private final String symbol;
    private final String displayName;

    CurrencyCode(String symbol, String displayName) {
        this.symbol = symbol;
        this.displayName = displayName;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getDisplayName() {
        return displayName;
    }
}

