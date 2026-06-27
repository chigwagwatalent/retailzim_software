package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;
import com.retailzw.enums.BusinessModule;
import java.time.LocalDateTime;

@Entity
@Table(name = "branches")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "branch_code", nullable = false, length = 20)
    private String branchCode;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "module_type", nullable = false, length = 30)
    @Builder.Default
    private BusinessModule moduleType = BusinessModule.SHOP_MODULE;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 100)
    private String city;

    @Column(length = 20)
    private String phone;

    @Column(length = 150)
    private String email;

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
}

