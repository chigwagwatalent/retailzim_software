package com.retailzw.dto.request;


import com.retailzw.enums.CurrencyCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class SaleRequest {

    private Long cashSessionId;

    private Long branchId;

    private Long customerId;

    @NotNull(message = "Currency is required")
    private CurrencyCode currency;

    @NotEmpty(message = "Sale must have at least one item")
    @Valid
    private List<SaleItemRequest> items;

    @NotEmpty(message = "At least one payment is required")
    @Valid
    private List<SalePaymentRequest> payments;

    private String couponCode;

    private String offlineReceiptNumber;

    private String offlineCreatedAt;

    private Long borrowerId;

    private String borrowerOfflineReference;

    private String heldChangeName;

    private String heldChangePhone;

    private java.math.BigDecimal heldChangeAmount;

    private String heldChangeOfflineReference;

    public Long getCashSessionId() {
        return cashSessionId;
    }

    public void setCashSessionId(Long cashSessionId) {
        this.cashSessionId = cashSessionId;
    }

    public Long getBranchId() {
        return branchId;
    }

    public void setBranchId(Long branchId) {
        this.branchId = branchId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public CurrencyCode getCurrency() {
        return currency;
    }

    public void setCurrency(CurrencyCode currency) {
        this.currency = currency;
    }

    public List<SaleItemRequest> getItems() {
        return items;
    }

    public void setItems(List<SaleItemRequest> items) {
        this.items = items;
    }

    public List<SalePaymentRequest> getPayments() {
        return payments;
    }

    public void setPayments(List<SalePaymentRequest> payments) {
        this.payments = payments;
    }

    public String getCouponCode() {
        return couponCode;
    }

    public void setCouponCode(String couponCode) {
        this.couponCode = couponCode;
    }

    public String getOfflineReceiptNumber() {
        return offlineReceiptNumber;
    }

    public void setOfflineReceiptNumber(String offlineReceiptNumber) {
        this.offlineReceiptNumber = offlineReceiptNumber;
    }

    public String getOfflineCreatedAt() {
        return offlineCreatedAt;
    }

    public void setOfflineCreatedAt(String offlineCreatedAt) {
        this.offlineCreatedAt = offlineCreatedAt;
    }

    public Long getBorrowerId() {
        return borrowerId;
    }

    public void setBorrowerId(Long borrowerId) {
        this.borrowerId = borrowerId;
    }

    public String getBorrowerOfflineReference() {
        return borrowerOfflineReference;
    }

    public void setBorrowerOfflineReference(String borrowerOfflineReference) {
        this.borrowerOfflineReference = borrowerOfflineReference;
    }

    public String getHeldChangeName() {
        return heldChangeName;
    }

    public void setHeldChangeName(String heldChangeName) {
        this.heldChangeName = heldChangeName;
    }

    public String getHeldChangePhone() {
        return heldChangePhone;
    }

    public void setHeldChangePhone(String heldChangePhone) {
        this.heldChangePhone = heldChangePhone;
    }

    public java.math.BigDecimal getHeldChangeAmount() {
        return heldChangeAmount;
    }

    public void setHeldChangeAmount(java.math.BigDecimal heldChangeAmount) {
        this.heldChangeAmount = heldChangeAmount;
    }

    public String getHeldChangeOfflineReference() {
        return heldChangeOfflineReference;
    }

    public void setHeldChangeOfflineReference(String heldChangeOfflineReference) {
        this.heldChangeOfflineReference = heldChangeOfflineReference;
    }
}

