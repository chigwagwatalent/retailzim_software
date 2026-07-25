package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "gas_sale_payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GasSalePayment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(name = "branch_id", nullable = false)
    private Long branchId;
    @Column(name = "gas_sale_id", nullable = false)
    private Long gasSaleId;
    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
    @Column(length = 120)
    private String reference;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
