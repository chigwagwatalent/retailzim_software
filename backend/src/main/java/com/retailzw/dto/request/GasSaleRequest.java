package com.retailzw.dto.request;

import com.retailzw.enums.CurrencyCode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class GasSaleRequest {
    @NotNull(message = "Branch is required")
    private Long branchId;

    private Long tankId;
    @Valid
    private List<GasSaleTankRequest> tanks = new ArrayList<>();

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
    @Valid
    private List<GasSalePaymentRequest> payments = new ArrayList<>();
    private BigDecimal amountReceived;
    private Boolean holdChange = false;
    private String heldChangeName;
    private String heldChangePhone;
    private String heldChangeOfflineReference;
    private LocalDateTime offlineCreatedAt;

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
    public List<GasSaleTankRequest> getTanks() { return tanks; }
    public void setTanks(List<GasSaleTankRequest> tanks) { this.tanks = tanks == null ? new ArrayList<>() : tanks; }
    public List<GasSalePaymentRequest> getPayments() { return payments; }
    public void setPayments(List<GasSalePaymentRequest> payments) { this.payments = payments == null ? new ArrayList<>() : payments; }
    public BigDecimal getAmountReceived() { return amountReceived; }
    public void setAmountReceived(BigDecimal amountReceived) { this.amountReceived = amountReceived; }
    public Boolean getHoldChange() { return holdChange; }
    public void setHoldChange(Boolean holdChange) { this.holdChange = holdChange; }
    public String getHeldChangeName() { return heldChangeName; }
    public void setHeldChangeName(String heldChangeName) { this.heldChangeName = heldChangeName; }
    public String getHeldChangePhone() { return heldChangePhone; }
    public void setHeldChangePhone(String heldChangePhone) { this.heldChangePhone = heldChangePhone; }
    public String getHeldChangeOfflineReference() { return heldChangeOfflineReference; }
    public void setHeldChangeOfflineReference(String heldChangeOfflineReference) { this.heldChangeOfflineReference = heldChangeOfflineReference; }
    public LocalDateTime getOfflineCreatedAt() { return offlineCreatedAt; }
    public void setOfflineCreatedAt(LocalDateTime offlineCreatedAt) { this.offlineCreatedAt = offlineCreatedAt; }
}
