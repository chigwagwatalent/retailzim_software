package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_lots")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryLot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(name = "branch_id", nullable = false)
    private Long branchId;
    @Column(name = "product_id", nullable = false)
    private Long productId;
    @Column(name = "batch_number", length = 100)
    private String batchNumber;
    @Column(name = "serial_number", length = 150)
    private String serialNumber;
    @Column(name = "expiry_date")
    private LocalDate expiryDate;
    @Column(name = "quantity_on_hand", nullable = false, precision = 15, scale = 4)
    private BigDecimal quantityOnHand;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;
    @Column(name = "supplier_id")
    private Long supplierId;
    @Column(name = "purchase_order_id")
    private Long purchaseOrderId;
    @Column(columnDefinition = "TEXT")
    private String notes;
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist void create() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (status == null) status = Status.AVAILABLE;
    }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }

    public enum Status { AVAILABLE, QUARANTINED, EXPIRED, DEPLETED }
}
