package com.retailzw.repository;

import com.retailzw.model.HeldChange;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface HeldChangeRepository extends JpaRepository<HeldChange, Long> {
    Optional<HeldChange> findByTenantIdAndOfflineReference(Long tenantId, String offlineReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from HeldChange c where c.tenantId = :tenantId and c.offlineReference = :reference")
    Optional<HeldChange> lockByOfflineReference(@Param("tenantId") Long tenantId, @Param("reference") String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from HeldChange c where c.id = :id and c.tenantId = :tenantId")
    Optional<HeldChange> lockById(@Param("tenantId") Long tenantId, @Param("id") Long id);

    @Query("select c from HeldChange c where c.tenantId = :tenantId and " +
            "(:status is null or c.status = :status) and " +
            "(:search is null or lower(c.customerName) like lower(concat('%', :search, '%')) or " +
            "lower(c.phone) like lower(concat('%', :search, '%')) or " +
            "lower(c.referenceNumber) like lower(concat('%', :search, '%'))) order by c.createdAt desc")
    Page<HeldChange> search(@Param("tenantId") Long tenantId,
                            @Param("status") HeldChange.Status status,
                            @Param("search") String search,
                            Pageable pageable);

    long countByTenantIdAndStatus(Long tenantId, HeldChange.Status status);
}
