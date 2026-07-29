package com.retailzw.dto.request;

import com.retailzw.enums.CurrencyCode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class GasRestockRequest {
    @NotNull(message = "Branch is required")
    private Long branchId;

    @NotNull(message = "Tank is required")
    private Long tankId;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.001", message = "Quantity must be greater than zero")
    private BigDecimal quantityKg;

    @NotNull(message = "Currency is required")
    private CurrencyCode currency = CurrencyCode.USD;

    @DecimalMin(value = "0.00", message = "Unit cost cannot be negative")
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Size(max = 120, message = "Supplier name must be 120 characters or fewer")
    private String supplierName;

    @Size(max = 120, message = "Invoice or reference must be 120 characters or fewer")
    private String supplierInvoice;

    @Size(max = 500, message = "Notes must be 500 characters or fewer")
    private String notes;

    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public Long getTankId() { return tankId; }
    public void setTankId(Long tankId) { this.tankId = tankId; }
    public BigDecimal getQuantityKg() { return quantityKg; }
    public void setQuantityKg(BigDecimal quantityKg) { this.quantityKg = quantityKg; }
    public CurrencyCode getCurrency() { return currency; }
    public void setCurrency(CurrencyCode currency) { this.currency = currency; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    public String getSupplierInvoice() { return supplierInvoice; }
    public void setSupplierInvoice(String supplierInvoice) { this.supplierInvoice = supplierInvoice; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
