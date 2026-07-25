package com.retailzw.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;

public class CloseGasShiftRequest {
    @NotNull(message = "Branch is required")
    private Long branchId;

    private Long shiftId;
    @Valid
    private List<GasTankClosingWeightRequest> closingWeights = new ArrayList<>();

    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public Long getShiftId() { return shiftId; }
    public void setShiftId(Long shiftId) { this.shiftId = shiftId; }
    public List<GasTankClosingWeightRequest> getClosingWeights() { return closingWeights; }
    public void setClosingWeights(List<GasTankClosingWeightRequest> closingWeights) {
        this.closingWeights = closingWeights == null ? new ArrayList<>() : closingWeights;
    }
}
