package com.retailzw.repository;

import com.retailzw.model.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    List<ProductCategory> findByTenantIdAndIsActiveTrueOrderBySortOrderAsc(Long tenantId);

    List<ProductCategory> findByTenantIdOrderBySortOrderAsc(Long tenantId);

    List<ProductCategory> findByTenantIdAndParentIdIsNullAndIsActiveTrueOrderBySortOrderAsc(Long tenantId);

    List<ProductCategory> findByTenantIdAndParentId(Long tenantId, Long parentId);

    Optional<ProductCategory> findByTenantIdAndCode(Long tenantId, String code);

    boolean existsByTenantIdAndCode(Long tenantId, String code);
}

