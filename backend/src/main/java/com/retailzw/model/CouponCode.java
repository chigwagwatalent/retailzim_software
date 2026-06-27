package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "coupon_codes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "promotion_id")
    private Long promotionId;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 10, scale = 4)
    private BigDecimal discountValue;

    @Column(name = "min_purchase_usd", precision = 10, scale = 2)
    private BigDecimal minPurchaseUsd;

    @Column(name = "max_discount_usd", precision = 10, scale = 2)
    private BigDecimal maxDiscountUsd;

    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "usage_count")
    @Builder.Default
    private Integer usageCount = 0;

    @Column(name = "per_customer_limit")
    @Builder.Default
    private Integer perCustomerLimit = 1;

    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum DiscountType {
        PERCENTAGE, FIXED_AMOUNT
    }
}

