package com.retailzw.repository;

import com.retailzw.model.CashDrawer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CashDrawerRepository extends JpaRepository<CashDrawer, Long> {

    List<CashDrawer> findByTenantIdAndBranchIdAndIsActiveTrue(Long tenantId, Long branchId);

    List<CashDrawer> findByTenantIdAndBranchId(Long tenantId, Long branchId);

    List<CashDrawer> findByBranchId(Long branchId);
}

