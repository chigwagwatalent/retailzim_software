package com.retailzw.dto.request;

import com.retailzw.model.Return;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateReturnRequest {

    @NotBlank(message = "Original receipt number is required")
    private String originalReceiptNumber;

    @NotEmpty(message = "Return must have at least one item")
    @Valid
    private List<ReturnItemRequest> items;

    @NotNull(message = "Reason is required")
    private Return.ReturnReason reason;

    @NotNull(message = "Refund method is required")
    private Return.RefundMethod refundMethod;

    private String notes;

    @Data
    public static class ReturnItemRequest {
        @NotNull
        private Long productId;

        @NotNull
        private BigDecimal quantity;

        private Boolean restockItem = true;
        private String notes;
    }
}

