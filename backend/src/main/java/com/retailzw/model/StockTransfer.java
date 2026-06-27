package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "stock_transfers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "transfer_number", unique = true, nullable = false, length = 50)
    private String transferNumber;

    @Column(name = "from_branch_id", nullable = false)
    private Long fromBranchId;

    @Column(name = "to_branch_id", nullable = false)
    private Long toBranchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TransferStatus status = TransferStatus.PENDING;

    @Column(name = "initiated_by", nullable = false)
    private Long initiatedBy;

    @Column(name = "received_by")
    private Long receivedBy;

    @Column(name = "initiated_at")
    private LocalDateTime initiatedAt;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "stockTransfer", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StockTransferItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (initiatedAt == null) initiatedAt = LocalDateTime.now();
    }

    public enum TransferStatus {
        PENDING, IN_TRANSIT, RECEIVED, CANCELLED
    }
}

