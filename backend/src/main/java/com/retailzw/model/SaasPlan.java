package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;
import com.retailzw.enums.BusinessModule;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "saas_plans")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaasPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "price_usd", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceUsd;

    @Column(name = "price_zwg", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceZwg;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 20)
    @Builder.Default
    private BillingCycle billingCycle = BillingCycle.MONTHLY;

    @Column(name = "max_branches")
    private Integer maxBranches;

    @Column(name = "max_users")
    private Integer maxUsers;

    @Column(name = "max_products")
    private Integer maxProducts;

    @Column(name = "max_gas_tanks")
    @Builder.Default
    private Integer maxGasTanks = 0;

    @Column(name = "allowed_modules", length = 255)
    @Builder.Default
    private String allowedModules = BusinessModule.SHOP_MODULE.name();

    @Column(name = "allow_mixed_modules")
    @Builder.Default
    private Boolean allowMixedModules = false;

    @Column(name = "gas_reconciliation_enabled")
    @Builder.Default
    private Boolean gasReconciliationEnabled = false;

    @Column(columnDefinition = "JSON")
    private String features;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

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

    public enum BillingCycle {
        MONTHLY, QUARTERLY, ANNUALLY
    }

    public List<BusinessModule> allowedModuleList() {
        if (allowedModules == null || allowedModules.isBlank()) {
            return List.of(BusinessModule.SHOP_MODULE);
        }
        return Arrays.stream(allowedModules.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(BusinessModule::valueOf)
                .collect(Collectors.toList());
    }

    public boolean allowsModule(BusinessModule module) {
        return allowedModuleList().contains(module);
    }
}

