package com.retailzw.model;

import com.retailzw.enums.CurrencyCode;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "smile_pay_checkouts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmilePayCheckout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "checkout_purpose", nullable = false, length = 30)
    @Builder.Default
    private CheckoutPurpose checkoutPurpose = CheckoutPurpose.SIGNUP_ACTIVATION;

    @Column(name = "billing_months", nullable = false)
    @Builder.Default
    private Integer billingMonths = 1;

    @Column(name = "unit_price", precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "previous_period_end")
    private LocalDateTime previousPeriodEnd;

    @Column(name = "new_period_end")
    private LocalDateTime newPeriodEnd;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "order_reference", nullable = false, unique = true, length = 80)
    private String orderReference;

    @Column(name = "access_token", nullable = false, unique = true, length = 96)
    private String accessToken;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private CurrencyCode currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "customer_mobile", length = 30)
    private String customerMobile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CheckoutStatus status = CheckoutStatus.PENDING;

    @Column(name = "payment_url", length = 1000)
    private String paymentUrl;

    @Column(name = "provider_reference", length = 150)
    private String providerReference;

    @Column(name = "provider_status", length = 60)
    private String providerStatus;

    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "initiated_at")
    private LocalDateTime initiatedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "invoice_sent_at")
    private LocalDateTime invoiceSentAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt;

    @Column(name = "next_check_at")
    private LocalDateTime nextCheckAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    protected void onCreate() {
        if (accessToken == null || accessToken.isBlank()) {
            accessToken = UUID.randomUUID().toString().replace("-", "")
                    + UUID.randomUUID().toString().replace("-", "");
        }
        if (unitPrice == null) {
            unitPrice = amount;
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum CheckoutStatus {
        PENDING, AWAITING_OTP, PROCESSING, PAID, FAILED, CANCELLED
    }

    public enum CheckoutPurpose {
        SIGNUP_ACTIVATION,
        SUBSCRIPTION_RENEWAL,
        PLAN_CHANGE
    }

    public enum PaymentMethod {
        ECOCASH("EcoCash", "ecocash", true, false),
        INNBUCKS("InnBucks", "innbucks", false, false),
        SMILECASH("SmileCash", "zb-payment", true, true),
        OMARI("Omari", "omari", true, true),
        ONEMONEY("OneMoney", "onemoney", true, false),
        CARD("Visa / Mastercard", "mpgs", false, false);

        private final String label;
        private final String endpoint;
        private final boolean mobileRequired;
        private final boolean otpRequired;

        PaymentMethod(String label, String endpoint, boolean mobileRequired, boolean otpRequired) {
            this.label = label;
            this.endpoint = endpoint;
            this.mobileRequired = mobileRequired;
            this.otpRequired = otpRequired;
        }

        public String getLabel() {
            return label;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public boolean isMobileRequired() {
            return mobileRequired;
        }

        public boolean isOtpRequired() {
            return otpRequired;
        }
    }
}
