package com.retailzw.model;


import com.retailzw.enums.CurrencyCode;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "purchase_orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "po_number", unique = true, length = 50)
    private String poNumber;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", insertable = false, updatable = false)
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PoStatus status = PoStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(length = 5)
    private CurrencyCode currency;

    @Column(name = "subtotal_usd", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal subtotalUsd = BigDecimal.ZERO;

    @Column(name = "tax_amount_usd", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal taxAmountUsd = BigDecimal.ZERO;

    @Column(name = "total_usd", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalUsd = BigDecimal.ZERO;

    @Column(name = "subtotal_zwg", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal subtotalZwg = BigDecimal.ZERO;

    @Column(name = "total_zwg", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalZwg = BigDecimal.ZERO;

    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "sent_to_supplier_at")
    private LocalDateTime sentToSupplierAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PurchaseOrderItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum PoStatus {
        DRAFT, SUBMITTED, APPROVED, REJECTED, ORDERED, PARTIAL, RECEIVED, CANCELLED
    }
}

