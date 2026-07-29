package com.retailzw.dto.request;

import com.retailzw.enums.CurrencyCode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class GasExpenseRequest {
    @NotNull(message = "Branch is required")
    private Long branchId;

    @NotBlank(message = "Expense category is required")
    @Size(max = 80, message = "Expense category must be 80 characters or fewer")
    private String category;

    @NotBlank(message = "Expense description is required")
    @Size(max = 255, message = "Expense description must be 255 characters or fewer")
    private String description;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    @NotNull(message = "Currency is required")
    private CurrencyCode currency = CurrencyCode.USD;

    @Size(max = 30, message = "Payment method must be 30 characters or fewer")
    private String paymentMethod = "CASH";

    @Size(max = 120, message = "Reference must be 120 characters or fewer")
    private String reference;

    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public CurrencyCode getCurrency() { return currency; }
    public void setCurrency(CurrencyCode currency) { this.currency = currency; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
}
