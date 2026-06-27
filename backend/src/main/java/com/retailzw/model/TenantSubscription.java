package com.retailzw.model;


import com.retailzw.enums.CurrencyCode;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_subscriptions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", insertable = false, updatable = false)
    private SaasPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private CurrencyCode currency;

    @Column(name = "amount_paid", precision = 15, scale = 2)
    private BigDecimal amountPaid;

    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        syncLegacyAmount();
    }

    @PreUpdate
    protected void onUpdate() {
        syncLegacyAmount();
    }

    private void syncLegacyAmount() {
        if (amountPaid == null && amount != null) {
            amountPaid = amount;
        }
        if (amount == null && amountPaid != null) {
            amount = amountPaid;
        }
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
    }

    public enum SubscriptionStatus {
        ACTIVE, EXPIRED, CANCELLED, TRIAL
    }
}

