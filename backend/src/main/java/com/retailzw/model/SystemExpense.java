package com.retailzw.model;

import com.retailzw.enums.CurrencyCode;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "system_expenses")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 180)
    private String description;

    @Column(length = 150)
    private String vendor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ExpenseCategory category;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private CurrencyCode currency;

    @Column(name = "incurred_on", nullable = false)
    private LocalDate incurredOn;

    @Column(name = "payment_reference", length = 120)
    private String paymentReference;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false)
    @Builder.Default
    private boolean recurring = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ExpenseStatus status = ExpenseStatus.POSTED;

    @Column(name = "created_by", nullable = false, length = 80, updatable = false)
    private String createdBy;

    @Column(name = "updated_by", nullable = false, length = 80)
    private String updatedBy;

    @Column(name = "voided_by", length = 80)
    private String voidedBy;

    @Column(name = "void_reason", length = 300)
    private String voidReason;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum ExpenseCategory {
        INFRASTRUCTURE("Infrastructure"),
        SOFTWARE("Software & licences"),
        PAYROLL("Payroll"),
        MARKETING("Marketing"),
        PROFESSIONAL_SERVICES("Professional services"),
        OFFICE("Office & operations"),
        BANK_FEES("Bank & payment fees"),
        TAXES("Taxes"),
        OTHER("Other");

        private final String label;

        ExpenseCategory(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum ExpenseStatus {
        POSTED,
        VOIDED
    }
}
