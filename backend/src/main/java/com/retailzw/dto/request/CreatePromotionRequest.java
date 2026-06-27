package com.retailzw.dto.request;

import com.retailzw.model.Promotion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreatePromotionRequest {

    @NotBlank(message = "Promotion name is required")
    private String name;

    private String description;

    @NotNull(message = "Promotion type is required")
    private Promotion.PromotionType type;

    @NotNull(message = "Discount value is required")
    private BigDecimal discountValue;

    private BigDecimal minPurchaseUsd;
    private BigDecimal minPurchaseZwg;
    private BigDecimal maxDiscountUsd;
    private Integer buyQuantity;
    private Integer getQuantity;
    private Long appliesToCategoryId;
    private Long appliesToProductId;
    private String happyHourStart;
    private String happyHourEnd;
    private String happyHourDays;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private Boolean isCombinable = false;
    private Integer priority = 0;
    private Long branchId;
}

