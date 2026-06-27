package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "quantity_on_hand", precision = 15, scale = 4)
    @Builder.Default
    private BigDecimal quantityOnHand = BigDecimal.ZERO;

    @Column(name = "quantity_reserved", precision = 15, scale = 4)
    @Builder.Default
    private BigDecimal quantityReserved = BigDecimal.ZERO;

    @Column(name = "quantity_on_order", precision = 15, scale = 4)
    @Builder.Default
    private BigDecimal quantityOnOrder = BigDecimal.ZERO;

    @Column(name = "average_cost_usd", precision = 15, scale = 4)
    @Builder.Default
    private BigDecimal averageCostUsd = BigDecimal.ZERO;

    @Column(name = "average_cost_zwg", precision = 15, scale = 4)
    @Builder.Default
    private BigDecimal averageCostZwg = BigDecimal.ZERO;

    @Column(name = "last_counted_at")
    private LocalDateTime lastCountedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

