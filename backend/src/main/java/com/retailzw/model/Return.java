package com.retailzw.model;


import com.retailzw.enums.CurrencyCode;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "returns")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Return {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "return_number", unique = true, nullable = false, length = 50)
    private String returnNumber;

    @Column(name = "original_sale_id", nullable = false)
    private Long originalSaleId;

    @Column(name = "original_receipt_number", nullable = false, length = 50)
    private String originalReceiptNumber;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "cashier_id", nullable = false)
    private Long cashierId;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReturnReason reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_method", nullable = false, length = 30)
    private RefundMethod refundMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private CurrencyCode currency;

    @Column(name = "total_refund", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalRefund;

    @Column(name = "loyalty_points_reversed")
    @Builder.Default
    private Integer loyaltyPointsReversed = 0;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "requires_approval")
    @Builder.Default
    private Boolean requiresApproval = false;

    @Column(name = "is_approved")
    @Builder.Default
    private Boolean isApproved = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "returnRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReturnItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum ReturnReason {
        DEFECTIVE, WRONG_ITEM, CUSTOMER_CHANGED_MIND, DAMAGED,
        EXPIRED, OVERCHARGED, OTHER
    }

    public enum RefundMethod {
        CASH, CARD, ECOCASH, ONEMONEY, INNBUCKS, STORE_CREDIT, LOYALTY_POINTS, EXCHANGE
    }
}

