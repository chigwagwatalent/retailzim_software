package com.retailzw.dto.request;

import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public class CloseSessionRequest {

    private Long sessionId;

    @PositiveOrZero
    private BigDecimal actualUsd = BigDecimal.ZERO;

    @PositiveOrZero
    private BigDecimal actualZwg = BigDecimal.ZERO;

    private String closingNotes;

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public BigDecimal getActualUsd() {
        return actualUsd;
    }

    public void setActualUsd(BigDecimal actualUsd) {
        this.actualUsd = actualUsd;
    }

    public BigDecimal getActualZwg() {
        return actualZwg;
    }

    public void setActualZwg(BigDecimal actualZwg) {
        this.actualZwg = actualZwg;
    }

    public String getClosingNotes() {
        return closingNotes;
    }

    public void setClosingNotes(String closingNotes) {
        this.closingNotes = closingNotes;
    }
}

