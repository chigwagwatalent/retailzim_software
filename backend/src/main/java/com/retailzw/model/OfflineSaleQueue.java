package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "offline_sale_queue")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfflineSaleQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "cashier_id", nullable = false)
    private Long cashierId;

    @Column(name = "offline_receipt_number", nullable = false, length = 100)
    private String offlineReceiptNumber;

    @Column(name = "sale_data", nullable = false, columnDefinition = "JSON")
    private String saleData;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SyncStatus syncStatus = SyncStatus.PENDING;

    @Column(name = "processed_sale_id")
    private Long processedSaleId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "offline_created_at")
    private LocalDateTime offlineCreatedAt;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum SyncStatus {
        PENDING, PROCESSING, SYNCED, FAILED, DUPLICATE
    }
}

