package com.retailzw.repository;

import com.retailzw.model.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Long> {

    List<Branch> findByTenantId(Long tenantId);

    List<Branch> findByTenantIdAndIsActiveTrue(Long tenantId);

    Optional<Branch> findByTenantIdAndBranchCode(Long tenantId, String branchCode);

    boolean existsByTenantIdAndBranchCode(Long tenantId, String branchCode);

    long countByTenantId(Long tenantId);
}

