package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "promotions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "branch_id")
    private Long branchId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PromotionType type;

    @Column(name = "discount_value", precision = 10, scale = 4)
    private BigDecimal discountValue;

    @Column(name = "min_purchase_usd", precision = 10, scale = 2)
    private BigDecimal minPurchaseUsd;

    @Column(name = "min_purchase_zwg", precision = 10, scale = 2)
    private BigDecimal minPurchaseZwg;

    @Column(name = "buy_quantity")
    private Integer buyQuantity;

    @Column(name = "get_quantity")
    private Integer getQuantity;

    @Column(name = "max_discount_usd", precision = 10, scale = 2)
    private BigDecimal maxDiscountUsd;

    @Column(name = "applies_to_category_id")
    private Long appliesToCategoryId;

    @Column(name = "applies_to_product_id")
    private Long appliesToProductId;

    @Column(name = "happy_hour_start", length = 10)
    private String happyHourStart;

    @Column(name = "happy_hour_end", length = 10)
    private String happyHourEnd;

    @Column(name = "happy_hour_days", length = 20)
    private String happyHourDays;

    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 0;

    @Column(name = "is_combinable")
    @Builder.Default
    private Boolean isCombinable = false;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum PromotionType {
        PERCENTAGE, FIXED_AMOUNT, BUY_X_GET_Y, BUNDLE, HAPPY_HOUR
    }
}

