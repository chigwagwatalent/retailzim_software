package com.retailzw.repository;

import com.retailzw.model.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findByTenantCode(String tenantCode);

    Optional<Tenant> findByEmail(String email);

    Optional<Tenant> findByEmailIgnoreCase(String email);

    boolean existsByTenantCode(String tenantCode);

    boolean existsByEmail(String email);

    List<Tenant> findByStatus(Tenant.TenantStatus status);

    Page<Tenant> findByStatus(Tenant.TenantStatus status, Pageable pageable);

    @Query("SELECT t FROM Tenant t WHERE " +
           "(:search IS NULL OR LOWER(t.companyName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(t.email) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(t.tenantCode) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Tenant> searchTenants(@Param("search") String search, Pageable pageable);

    @Query("SELECT COUNT(t) FROM Tenant t WHERE t.status = :status")
    long countByStatus(@Param("status") Tenant.TenantStatus status);

    @Query("SELECT t FROM Tenant t WHERE t.planId = :planId")
    List<Tenant> findByPlanId(@Param("planId") Long planId);
}

