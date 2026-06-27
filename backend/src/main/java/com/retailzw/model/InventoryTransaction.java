package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionType type;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal quantity;

    @Column(name = "quantity_before", precision = 15, scale = 4)
    private BigDecimal quantityBefore;

    @Column(name = "quantity_after", precision = 15, scale = 4)
    private BigDecimal quantityAfter;

    @Column(name = "unit_cost_usd", precision = 15, scale = 4)
    private BigDecimal unitCostUsd;

    @Column(name = "unit_cost_zwg", precision = 15, scale = 4)
    private BigDecimal unitCostZwg;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum TransactionType {
        SALE, PURCHASE, RETURN, ADJUSTMENT, TRANSFER_OUT, TRANSFER_IN,
        STOCKTAKE, WRITE_OFF, OPENING
    }
}

