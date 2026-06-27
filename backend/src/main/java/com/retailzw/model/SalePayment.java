package com.retailzw.model;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.retailzw.enums.CurrencyCode;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "sale_payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    @JsonIgnore
    private Sale sale;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private CurrencyCode currency;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "amount_usd_equivalent", precision = 15, scale = 2)
    private BigDecimal amountUsdEquivalent;

    @Column(name = "exchange_rate", precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Column(name = "change_given", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal changeGiven = BigDecimal.ZERO;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum PaymentMethod {
        CASH, CARD, ECOCASH, ONEMONEY, INNBUCKS,
        LOYALTY_POINTS, STORE_CREDIT, OTHER
    }
}

