package com.retailzw.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public class SaleItemRequest {

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private BigDecimal quantity;

    private BigDecimal unitPrice;

    private BigDecimal discountAmount;

    private String pricingTier;

    private Long pricingVersion;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public String getPricingTier() {
        return pricingTier;
    }

    public void setPricingTier(String pricingTier) {
        this.pricingTier = pricingTier;
    }

    public Long getPricingVersion() {
        return pricingVersion;
    }

    public void setPricingVersion(Long pricingVersion) {
        this.pricingVersion = pricingVersion;
    }
}

