package com.retailzw.repository;

import com.retailzw.model.StockTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {

    Optional<StockTransfer> findByTransferNumber(String transferNumber);

    List<StockTransfer> findByTenantIdAndFromBranchId(Long tenantId, Long fromBranchId);

    List<StockTransfer> findByTenantIdAndToBranchId(Long tenantId, Long toBranchId);

    Page<StockTransfer> findByTenantId(Long tenantId, Pageable pageable);

    List<StockTransfer> findByTenantIdAndStatus(Long tenantId, StockTransfer.TransferStatus status);
}

