package com.retailzw.model;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.retailzw.enums.CurrencyCode;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sales")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "receipt_number", unique = true, nullable = false, length = 50)
    private String receiptNumber;

    @Column(name = "cash_session_id")
    private Long cashSessionId;

    @Column(name = "cashier_id", nullable = false)
    private Long cashierId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "borrower_id")
    private Long borrowerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sale_type", nullable = false, length = 20)
    @Builder.Default
    private SaleType saleType = SaleType.STANDARD;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SaleStatus status = SaleStatus.COMPLETED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private CurrencyCode currency;

    @Column(name = "subtotal", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_amount", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "grand_total", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal grandTotal = BigDecimal.ZERO;

    @Column(name = "total_cost", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalCost = BigDecimal.ZERO;

    @Column(name = "gross_profit", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal grossProfit = BigDecimal.ZERO;

    @Column(name = "loyalty_points_earned")
    @Builder.Default
    private Integer loyaltyPointsEarned = 0;

    @Column(name = "loyalty_points_redeemed")
    @Builder.Default
    private Integer loyaltyPointsRedeemed = 0;

    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    @Column(name = "coupon_discount", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal couponDiscount = BigDecimal.ZERO;

    @Column(name = "is_offline_sale")
    @Builder.Default
    private Boolean isOfflineSale = false;

    @Column(name = "offline_receipt_number", unique = true, length = 80)
    private String offlineReceiptNumber;

    @Column(name = "offline_created_at")
    private LocalDateTime offlineCreatedAt;

    @Column(name = "void_reason", columnDefinition = "TEXT")
    private String voidReason;

    @Column(name = "voided_by")
    private Long voidedBy;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SaleItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @JsonIgnore
    private List<SalePayment> payments = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum SaleStatus {
        COMPLETED, VOIDED, REFUNDED, PARTIAL_REFUND, PENDING
    }

    public enum SaleType {
        STANDARD, BORROWER_CREDIT
    }
}

