package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "gas_stock_adjustments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GasStockAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "tank_id", nullable = false)
    private Long tankId;

    @Column(name = "quantity_before_kg", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantityBeforeKg;

    @Column(name = "counted_kg", nullable = false, precision = 12, scale = 3)
    private BigDecimal countedKg;

    @Column(name = "variance_kg", nullable = false, precision = 12, scale = 3)
    private BigDecimal varianceKg;

    @Column(nullable = false, length = 40)
    private String reason;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
