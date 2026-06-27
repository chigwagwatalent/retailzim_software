package com.retailzw.repository;

import com.retailzw.model.ProductBranchPricing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductBranchPricingRepository extends JpaRepository<ProductBranchPricing, Long> {

    Optional<ProductBranchPricing> findByTenantIdAndBranchIdAndProductId(Long tenantId, Long branchId, Long productId);

    List<ProductBranchPricing> findByTenantIdAndProductId(Long tenantId, Long productId);

    List<ProductBranchPricing> findByTenantIdAndBranchId(Long tenantId, Long branchId);
}

