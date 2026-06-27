package com.retailzw.repository;

import com.retailzw.model.InventoryLot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface InventoryLotRepository extends JpaRepository<InventoryLot, Long> {
    List<InventoryLot> findByTenantIdAndBranchIdOrderByExpiryDateAsc(Long tenantId, Long branchId);
    List<InventoryLot> findByTenantIdAndBranchIdAndExpiryDateBeforeAndStatus(
            Long tenantId, Long branchId, LocalDate date, InventoryLot.Status status);
}
