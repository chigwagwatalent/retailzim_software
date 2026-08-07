package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_wholesale_pricing",
        uniqueConstraints = @UniqueConstraint(name = "uk_wholesale_tenant_product",
                columnNames = {"tenant_id", "product_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductWholesalePricing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private Boolean isEnabled = false;

    @Column(name = "minimum_quantity", precision = 15, scale = 4)
    private BigDecimal minimumQuantity;

    @Column(name = "price_usd", precision = 15, scale = 4)
    private BigDecimal priceUsd;

    @Column(name = "price_zwg", precision = 15, scale = 4)
    private BigDecimal priceZwg;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_mode", nullable = false, length = 20)
    @Builder.Default
    private Product.PricingMode pricingMode = Product.PricingMode.MANUAL;

    @Column(name = "exchange_rate_id")
    private Long exchangeRateId;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
