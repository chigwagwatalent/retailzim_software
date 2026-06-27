package com.retailzw.model;

import com.retailzw.enums.BusinessModule;
import com.retailzw.enums.ModuleAccessStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_enabled_modules",
        uniqueConstraints = @UniqueConstraint(name = "uk_tenant_enabled_module", columnNames = {"tenant_id", "module"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantEnabledModule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BusinessModule module;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ModuleAccessStatus status = ModuleAccessStatus.ENABLED;

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
