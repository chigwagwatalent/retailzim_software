package com.retailzw.repository;

import com.retailzw.model.ProductSupplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductSupplierRepository extends JpaRepository<ProductSupplier, Long> {

    List<ProductSupplier> findByProductId(Long productId);

    List<ProductSupplier> findBySupplierId(Long supplierId);

    Optional<ProductSupplier> findByProductIdAndSupplierId(Long productId, Long supplierId);

    Optional<ProductSupplier> findByProductIdAndIsPreferredTrue(Long productId);

    List<ProductSupplier> findByTenantIdOrderByProductIdAscCostPriceUsdAsc(Long tenantId);
}

