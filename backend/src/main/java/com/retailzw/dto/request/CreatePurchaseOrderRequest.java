package com.retailzw.dto.request;


import com.retailzw.enums.CurrencyCode;
import com.retailzw.model.Tenant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class CreatePurchaseOrderRequest {

    @NotNull(message = "Supplier ID is required")
    private Long supplierId;

    @NotEmpty(message = "Purchase order must have at least one item")
    @Valid
    private List<PoItemRequest> items;

    private CurrencyCode currency;
    private String notes;
    private LocalDate expectedDeliveryDate;

    @Data
    public static class PoItemRequest {
        @NotNull
        private Long productId;

        @NotNull
        private BigDecimal quantity;

        private BigDecimal unitCostUsd;
        private BigDecimal unitCostZwg;
        private BigDecimal taxRate;
        private String notes;
    }
}

