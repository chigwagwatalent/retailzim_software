package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "customers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(length = 150)
    private String email;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "loyalty_card_number", unique = true, length = 50)
    private String loyaltyCardNumber;

    @Column(name = "loyalty_points")
    @Builder.Default
    private Integer loyaltyPoints = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "loyalty_tier", length = 20)
    @Builder.Default
    private LoyaltyTier loyaltyTier = LoyaltyTier.BRONZE;

    @Column(name = "total_spent_usd", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalSpentUsd = BigDecimal.ZERO;

    @Column(name = "total_spent_zwg", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalSpentZwg = BigDecimal.ZERO;

    @Column(name = "total_transactions")
    @Builder.Default
    private Integer totalTransactions = 0;

    @Column(name = "store_credit_usd", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal storeCreditUsd = BigDecimal.ZERO;

    @Column(name = "store_credit_zwg", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal storeCreditZwg = BigDecimal.ZERO;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 10)
    private String gender;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "registered_by")
    private Long registeredBy;

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

    public enum LoyaltyTier {
        BRONZE, SILVER, GOLD, PLATINUM
    }
}

