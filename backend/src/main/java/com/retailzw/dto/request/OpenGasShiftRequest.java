package com.retailzw.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

public class OpenGasShiftRequest {
    @NotNull(message = "Branch is required")
    private Long branchId;
    private List<Long> tankIds = new ArrayList<>();

    public Long getBranchId() {
        return branchId;
    }

    public void setBranchId(Long branchId) {
        this.branchId = branchId;
    }

    public List<Long> getTankIds() { return tankIds; }
    public void setTankIds(List<Long> tankIds) { this.tankIds = tankIds == null ? new ArrayList<>() : tankIds; }
}
