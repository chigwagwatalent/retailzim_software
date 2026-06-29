package com.retailzw.dto.request;

import com.retailzw.enums.CurrencyCode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class GasSaleRequest {
    @NotNull(message = "Branch is required")
    private Long branchId;

    @NotNull(message = "Tank is required")
    private Long tankId;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.001", message = "Quantity must be greater than zero")
    private BigDecimal quantityKg;

    @NotNull(message = "Currency is required")
    private CurrencyCode currency = CurrencyCode.USD;

    private String customerName;
    private String customerPhone;
    private String offlineReceiptNumber;
    private String paymentMethod = "CASH";
    private String paymentReference;

    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public Long getTankId() { return tankId; }
    public void setTankId(Long tankId) { this.tankId = tankId; }
    public BigDecimal getQuantityKg() { return quantityKg; }
    public void setQuantityKg(BigDecimal quantityKg) { this.quantityKg = quantityKg; }
    public CurrencyCode getCurrency() { return currency; }
    public void setCurrency(CurrencyCode currency) { this.currency = currency; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
    public String getOfflineReceiptNumber() { return offlineReceiptNumber; }
    public void setOfflineReceiptNumber(String offlineReceiptNumber) { this.offlineReceiptNumber = offlineReceiptNumber; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }
}
