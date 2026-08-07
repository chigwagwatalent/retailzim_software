package com.retailzw.repository;

import com.retailzw.model.TenantExchangeRate;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantExchangeRateRepository extends JpaRepository<TenantExchangeRate, Long> {

    Optional<TenantExchangeRate> findFirstByTenantIdAndIsActiveTrueOrderByEffectiveFromDescIdDesc(Long tenantId);

    List<TenantExchangeRate> findByTenantIdOrderByEffectiveFromDescIdDesc(Long tenantId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT rate
            FROM TenantExchangeRate rate
            WHERE rate.tenantId = :tenantId
              AND rate.isActive = true
            ORDER BY rate.effectiveFrom DESC, rate.id DESC
            """)
    List<TenantExchangeRate> lockActiveRates(@Param("tenantId") Long tenantId);
}
