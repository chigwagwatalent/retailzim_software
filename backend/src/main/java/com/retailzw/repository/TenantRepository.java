package com.retailzw.repository;

import com.retailzw.model.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;
import jakarta.persistence.LockModeType;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT tenant FROM Tenant tenant WHERE tenant.id = :id")
    Optional<Tenant> findLockedById(@Param("id") Long id);

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

    @Query("""
            SELECT tenant
            FROM Tenant tenant
            WHERE (:search IS NULL
                   OR LOWER(tenant.companyName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(tenant.email) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(tenant.tenantCode) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:status IS NULL OR tenant.status = :status)
              AND (:planId IS NULL OR tenant.planId = :planId)
            ORDER BY tenant.createdAt DESC, tenant.id DESC
            """)
    Page<Tenant> findTenants(@Param("search") String search,
                             @Param("status") Tenant.TenantStatus status,
                             @Param("planId") Long planId,
                             Pageable pageable);

    @Query("SELECT COUNT(t) FROM Tenant t WHERE t.status = :status")
    long countByStatus(@Param("status") Tenant.TenantStatus status);

    @Query("SELECT t FROM Tenant t WHERE t.planId = :planId")
    List<Tenant> findByPlanId(@Param("planId") Long planId);
}

