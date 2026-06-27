package com.retailzw.dto.request;

import jakarta.validation.constraints.NotNull;

public class OpenGasShiftRequest {
    @NotNull(message = "Branch is required")
    private Long branchId;

    public Long getBranchId() {
        return branchId;
    }

    public void setBranchId(Long branchId) {
        this.branchId = branchId;
    }
}
