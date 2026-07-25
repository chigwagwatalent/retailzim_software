package com.retailzw.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class GasSaleTankRequest {
    @NotNull(message = "Tank is required")
    private Long tankId;
    @DecimalMin(value = "0.001", message = "Tank allocation must be greater than zero")
    private BigDecimal quantityKg;

    public Long getTankId() { return tankId; }
    public void setTankId(Long tankId) { this.tankId = tankId; }
    public BigDecimal getQuantityKg() { return quantityKg; }
    public void setQuantityKg(BigDecimal quantityKg) { this.quantityKg = quantityKg; }
}
