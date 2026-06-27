package com.retailzw.repository;

import com.retailzw.model.BorrowerTransaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BorrowerTransactionRepository extends JpaRepository<BorrowerTransaction, Long> {
    Optional<BorrowerTransaction> findByTenantIdAndOfflineReference(Long tenantId, String offlineReference);
    List<BorrowerTransaction> findByTenantIdAndBorrowerIdOrderByCreatedAtDesc(Long tenantId, Long borrowerId, Pageable pageable);
}
