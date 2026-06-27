package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;
import com.retailzw.enums.TenantBusinessMode;
import com.retailzw.enums.CurrencyCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tenants")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_code", unique = true, nullable = false, length = 20)
    private String tenantCode;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(name = "registration_number", length = 50)
    private String registrationNumber;

    @Column(name = "vat_number", length = 50)
    private String vatNumber;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String country;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(length = 200)
    private String website;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TenantStatus status = TenantStatus.PENDING;

    @Column(name = "plan_id")
    private Long planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_mode", nullable = false, length = 30)
    @Builder.Default
    private TenantBusinessMode businessMode = TenantBusinessMode.SINGLE_MODULE;

    @Column(name = "subscription_start")
    private LocalDateTime subscriptionStart;

    @Column(name = "subscription_end")
    private LocalDateTime subscriptionEnd;

    @Column(name = "trial_end")
    private LocalDateTime trialEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_currency", nullable = false, length = 5)
    @Builder.Default
    private CurrencyCode defaultCurrency = CurrencyCode.USD;

    @Enumerated(EnumType.STRING)
    @Column(name = "secondary_currency", length = 5)
    private CurrencyCode secondaryCurrency;

    @Column(length = 50)
    @Builder.Default
    private String timezone = "Africa/Harare";

    @Column(name = "default_tax_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal defaultTaxRate = BigDecimal.ZERO;

    @Column(name = "receipt_footer", length = 500)
    private String receiptFooter;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum TenantStatus {
        PENDING, ACTIVE, SUSPENDED, CANCELLED
    }

}

