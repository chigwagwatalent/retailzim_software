package com.retailzw.model;

import com.retailzw.enums.CurrencyCode;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "checkout_id", nullable = false, unique = true)
    private Long checkoutId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "checkout_purpose", nullable = false, length = 30)
    private SmilePayCheckout.CheckoutPurpose checkoutPurpose;

    @Column(name = "billing_months", nullable = false)
    private Integer billingMonths;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private CurrencyCode currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private SmilePayCheckout.PaymentMethod paymentMethod;

    @Column(name = "order_reference", nullable = false, unique = true, length = 80)
    private String orderReference;

    @Column(name = "provider_reference", length = 150)
    private String providerReference;

    @Column(name = "previous_period_end")
    private LocalDateTime previousPeriodEnd;

    @Column(name = "new_period_end", nullable = false)
    private LocalDateTime newPeriodEnd;

    @Column(name = "confirmed_at", nullable = false)
    private LocalDateTime confirmedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
