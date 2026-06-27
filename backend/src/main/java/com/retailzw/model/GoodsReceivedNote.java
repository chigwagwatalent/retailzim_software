package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "goods_received_notes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoodsReceivedNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "grn_number", unique = true, nullable = false, length = 50)
    private String grnNumber;

    @Column(name = "purchase_order_id", nullable = false)
    private Long purchaseOrderId;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "received_by", nullable = false)
    private Long receivedBy;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

