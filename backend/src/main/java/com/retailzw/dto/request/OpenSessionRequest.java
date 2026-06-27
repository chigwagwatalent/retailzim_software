package com.retailzw.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public class OpenSessionRequest {

    @NotNull(message = "Drawer ID is required")
    private Long drawerId;

    @PositiveOrZero
    private BigDecimal openingFloatUsd = BigDecimal.ZERO;

    @PositiveOrZero
    private BigDecimal openingFloatZwg = BigDecimal.ZERO;

    public Long getDrawerId() {
        return drawerId;
    }

    public void setDrawerId(Long drawerId) {
        this.drawerId = drawerId;
    }

    public BigDecimal getOpeningFloatUsd() {
        return openingFloatUsd;
    }

    public void setOpeningFloatUsd(BigDecimal openingFloatUsd) {
        this.openingFloatUsd = openingFloatUsd;
    }

    public BigDecimal getOpeningFloatZwg() {
        return openingFloatZwg;
    }

    public void setOpeningFloatZwg(BigDecimal openingFloatZwg) {
        this.openingFloatZwg = openingFloatZwg;
    }
}

