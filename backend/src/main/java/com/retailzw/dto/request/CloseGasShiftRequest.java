package com.retailzw.dto.request;

import jakarta.validation.constraints.NotNull;

public class CloseGasShiftRequest {
    @NotNull(message = "Branch is required")
    private Long branchId;

    private Long shiftId;

    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public Long getShiftId() { return shiftId; }
    public void setShiftId(Long shiftId) { this.shiftId = shiftId; }
}
