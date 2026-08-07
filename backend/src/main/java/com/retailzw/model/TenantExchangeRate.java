package com.retailzw.model;

import com.retailzw.enums.CurrencyCode;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_exchange_rates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_currency", nullable = false, length = 5)
    private CurrencyCode baseCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "quote_currency", nullable = false, length = 5)
    private CurrencyCode quoteCurrency;

    @Column(name = "usd_to_zwg_rate", nullable = false, precision = 19, scale = 6)
    private BigDecimal usdToZwgRate;

    @Column(name = "price_scale", nullable = false)
    @Builder.Default
    private Integer priceScale = 2;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "effective_from", nullable = false)
    private LocalDateTime effectiveFrom;

    @Column(name = "effective_to")
    private LocalDateTime effectiveTo;

    @Column(name = "change_reason", length = 500)
    private String changeReason;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (effectiveFrom == null) {
            effectiveFrom = now;
        }
        createdAt = now;
    }
}
