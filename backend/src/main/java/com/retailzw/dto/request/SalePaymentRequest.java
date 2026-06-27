package com.retailzw.dto.request;


import com.retailzw.enums.CurrencyCode;
import com.retailzw.model.SalePayment;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public class SalePaymentRequest {

    @NotNull(message = "Payment method is required")
    private SalePayment.PaymentMethod method;

    @NotNull(message = "Currency is required")
    private CurrencyCode currency;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    private String reference;

    private BigDecimal exchangeRate;

    public SalePayment.PaymentMethod getMethod() {
        return method;
    }

    public void setMethod(SalePayment.PaymentMethod method) {
        this.method = method;
    }

    public CurrencyCode getCurrency() {
        return currency;
    }

    public void setCurrency(CurrencyCode currency) {
        this.currency = currency;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public BigDecimal getExchangeRate() {
        return exchangeRate;
    }

    public void setExchangeRate(BigDecimal exchangeRate) {
        this.exchangeRate = exchangeRate;
    }
}

