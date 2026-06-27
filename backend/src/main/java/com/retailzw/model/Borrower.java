package com.retailzw.model;

import com.retailzw.enums.CurrencyCode;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "borrowers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Borrower {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "account_number", nullable = false, length = 50)
    private String accountNumber;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(nullable = false, length = 40)
    private String phone;

    @Column(name = "national_id", length = 80)
    private String nationalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    @Builder.Default
    private CurrencyCode currency = CurrencyCode.USD;

    @Column(name = "credit_limit", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal creditLimit = BigDecimal.ZERO;

    @Column(name = "current_balance", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Transient
    public BigDecimal getAvailableCredit() {
        return (creditLimit == null ? BigDecimal.ZERO : creditLimit)
                .subtract(currentBalance == null ? BigDecimal.ZERO : currentBalance);
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
