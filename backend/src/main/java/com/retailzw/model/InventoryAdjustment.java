package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_adjustments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "adjustment_number", length = 50)
    private String adjustmentNumber;

    @Column(name = "quantity_change", nullable = false, precision = 15, scale = 4)
    private BigDecimal quantityChange;

    @Column(name = "quantity_before", precision = 15, scale = 4)
    private BigDecimal quantityBefore;

    @Column(name = "quantity_after", precision = 15, scale = 4)
    private BigDecimal quantityAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AdjustmentReason reason;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum AdjustmentReason {
        DAMAGED, EXPIRED, THEFT, FOUND, WRITE_OFF, CORRECTION, OPENING_STOCK, OTHER
    }
}

