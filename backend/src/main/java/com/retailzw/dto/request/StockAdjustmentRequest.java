package com.retailzw.dto.request;

import com.retailzw.model.InventoryAdjustment;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class StockAdjustmentRequest {

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Quantity change is required")
    private BigDecimal quantityChange;

    @NotNull(message = "Reason is required")
    private InventoryAdjustment.AdjustmentReason reason;

    private String notes;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public BigDecimal getQuantityChange() {
        return quantityChange;
    }

    public void setQuantityChange(BigDecimal quantityChange) {
        this.quantityChange = quantityChange;
    }

    public InventoryAdjustment.AdjustmentReason getReason() {
        return reason;
    }

    public void setReason(InventoryAdjustment.AdjustmentReason reason) {
        this.reason = reason;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

