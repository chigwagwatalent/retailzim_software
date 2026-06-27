package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_branch_pricing")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductBranchPricing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "selling_price_usd", precision = 15, scale = 4)
    private BigDecimal sellingPriceUsd;

    @Column(name = "selling_price_zwg", precision = 15, scale = 4)
    private BigDecimal sellingPriceZwg;

    @Column(name = "cost_price_usd", precision = 15, scale = 4)
    private BigDecimal costPriceUsd;

    @Column(name = "cost_price_zwg", precision = 15, scale = 4)
    private BigDecimal costPriceZwg;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

