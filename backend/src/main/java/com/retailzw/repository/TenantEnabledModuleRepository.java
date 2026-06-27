package com.retailzw.repository;

import com.retailzw.enums.BusinessModule;
import com.retailzw.enums.ModuleAccessStatus;
import com.retailzw.model.TenantEnabledModule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantEnabledModuleRepository extends JpaRepository<TenantEnabledModule, Long> {
    List<TenantEnabledModule> findByTenantId(Long tenantId);
    List<TenantEnabledModule> findByTenantIdAndStatus(Long tenantId, ModuleAccessStatus status);
    Optional<TenantEnabledModule> findByTenantIdAndModule(Long tenantId, BusinessModule module);
    boolean existsByTenantIdAndModuleAndStatus(Long tenantId, BusinessModule module, ModuleAccessStatus status);
}
