package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_variance_investigations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockVarianceInvestigation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(name = "branch_id", nullable = false)
    private Long branchId;
    @Column(name = "stocktake_session_id")
    private Long stocktakeSessionId;
    @Column(name = "stocktake_item_id")
    private Long stocktakeItemId;
    @Column(name = "product_id", nullable = false)
    private Long productId;
    @Column(name = "system_quantity", nullable = false, precision = 15, scale = 4)
    private BigDecimal systemQuantity;
    @Column(name = "counted_quantity", nullable = false, precision = 15, scale = 4)
    private BigDecimal countedQuantity;
    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal variance;
    @Column(length = 50)
    private String reason;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;
    @Column(name = "assigned_to")
    private Long assignedTo;
    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;
    @Column(name = "created_by")
    private Long createdBy;
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "resolved_by")
    private Long resolvedBy;
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
    @PrePersist void create() {
        createdAt = LocalDateTime.now();
        if (status == null) status = Status.OPEN;
    }
    public enum Status { OPEN, UNDER_REVIEW, RESOLVED, WRITTEN_OFF }
}
