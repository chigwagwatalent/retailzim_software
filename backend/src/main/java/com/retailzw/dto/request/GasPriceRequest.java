package com.retailzw.dto.request;

import com.retailzw.enums.CurrencyCode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class GasPriceRequest {
    @NotNull(message = "Branch is required")
    private Long branchId;

    @NotNull(message = "Currency is required")
    private CurrencyCode currency = CurrencyCode.USD;

    @NotNull(message = "Price per kg is required")
    @DecimalMin(value = "0.0001", message = "Price must be greater than zero")
    private BigDecimal pricePerKg;

    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public CurrencyCode getCurrency() { return currency; }
    public void setCurrency(CurrencyCode currency) { this.currency = currency; }
    public BigDecimal getPricePerKg() { return pricePerKg; }
    public void setPricePerKg(BigDecimal pricePerKg) { this.pricePerKg = pricePerKg; }
}
