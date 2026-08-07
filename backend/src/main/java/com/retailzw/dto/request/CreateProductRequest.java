package com.retailzw.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public class CreateProductRequest {

    @NotBlank(message = "Product name is required")
    private String name;

    private String sku;
    private String barcode;
    private String description;
    private Long categoryId;
    private Long uomId;
    private Long branchId;

    @NotNull(message = "Cost price USD is required")
    @PositiveOrZero
    private BigDecimal costPriceUsd;

    @NotNull(message = "Selling price USD is required")
    @PositiveOrZero
    private BigDecimal sellingPriceUsd;

    @PositiveOrZero
    private BigDecimal costPriceZwg;

    @PositiveOrZero
    private BigDecimal sellingPriceZwg;

    @PositiveOrZero
    private BigDecimal taxRate;

    private Boolean isTaxable = true;

    @PositiveOrZero
    private BigDecimal reorderLevel;

    @PositiveOrZero
    private BigDecimal maxStockLevel;

    private String imageUrl;
    private Boolean hasVariants = false;
    private Boolean isService = false;
    private BigDecimal openingStock;
    private Boolean wholesaleEnabled;

    @PositiveOrZero
    private BigDecimal wholesaleMinimumQuantity;

    @PositiveOrZero
    private BigDecimal wholesalePriceUsd;

    @PositiveOrZero
    private BigDecimal wholesalePriceZwg;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Long getUomId() {
        return uomId;
    }

    public void setUomId(Long uomId) {
        this.uomId = uomId;
    }

    public Long getBranchId() {
        return branchId;
    }

    public void setBranchId(Long branchId) {
        this.branchId = branchId;
    }

    public BigDecimal getCostPriceUsd() {
        return costPriceUsd;
    }

    public void setCostPriceUsd(BigDecimal costPriceUsd) {
        this.costPriceUsd = costPriceUsd;
    }

    public BigDecimal getSellingPriceUsd() {
        return sellingPriceUsd;
    }

    public void setSellingPriceUsd(BigDecimal sellingPriceUsd) {
        this.sellingPriceUsd = sellingPriceUsd;
    }

    public BigDecimal getCostPriceZwg() {
        return costPriceZwg;
    }

    public void setCostPriceZwg(BigDecimal costPriceZwg) {
        this.costPriceZwg = costPriceZwg;
    }

    public BigDecimal getSellingPriceZwg() {
        return sellingPriceZwg;
    }

    public void setSellingPriceZwg(BigDecimal sellingPriceZwg) {
        this.sellingPriceZwg = sellingPriceZwg;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate;
    }

    public Boolean getIsTaxable() {
        return isTaxable;
    }

    public void setIsTaxable(Boolean taxable) {
        isTaxable = taxable;
    }

    public BigDecimal getReorderLevel() {
        return reorderLevel;
    }

    public void setReorderLevel(BigDecimal reorderLevel) {
        this.reorderLevel = reorderLevel;
    }

    public BigDecimal getMaxStockLevel() {
        return maxStockLevel;
    }

    public void setMaxStockLevel(BigDecimal maxStockLevel) {
        this.maxStockLevel = maxStockLevel;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Boolean getHasVariants() {
        return hasVariants;
    }

    public void setHasVariants(Boolean hasVariants) {
        this.hasVariants = hasVariants;
    }

    public Boolean getIsService() {
        return isService;
    }

    public void setIsService(Boolean service) {
        isService = service;
    }

    public BigDecimal getOpeningStock() {
        return openingStock;
    }

    public void setOpeningStock(BigDecimal openingStock) {
        this.openingStock = openingStock;
    }

    public Boolean getWholesaleEnabled() {
        return wholesaleEnabled;
    }

    public void setWholesaleEnabled(Boolean wholesaleEnabled) {
        this.wholesaleEnabled = wholesaleEnabled;
    }

    public BigDecimal getWholesaleMinimumQuantity() {
        return wholesaleMinimumQuantity;
    }

    public void setWholesaleMinimumQuantity(BigDecimal wholesaleMinimumQuantity) {
        this.wholesaleMinimumQuantity = wholesaleMinimumQuantity;
    }

    public BigDecimal getWholesalePriceUsd() {
        return wholesalePriceUsd;
    }

    public void setWholesalePriceUsd(BigDecimal wholesalePriceUsd) {
        this.wholesalePriceUsd = wholesalePriceUsd;
    }

    public BigDecimal getWholesalePriceZwg() {
        return wholesalePriceZwg;
    }

    public void setWholesalePriceZwg(BigDecimal wholesalePriceZwg) {
        this.wholesalePriceZwg = wholesalePriceZwg;
    }
}

