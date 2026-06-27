package com.retailzw.repository;

import com.retailzw.model.OfflineSaleQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface OfflineSaleQueueRepository extends JpaRepository<OfflineSaleQueue, Long> {

    List<OfflineSaleQueue> findByTenantIdAndBranchIdAndSyncStatus(Long tenantId, Long branchId,
                                                                    OfflineSaleQueue.SyncStatus syncStatus);

    Optional<OfflineSaleQueue> findByTenantIdAndOfflineReceiptNumber(Long tenantId, String offlineReceiptNumber);

    boolean existsByTenantIdAndOfflineReceiptNumber(Long tenantId, String offlineReceiptNumber);
}

