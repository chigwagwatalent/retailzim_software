package com.retailzw.repository;

import com.retailzw.model.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    List<Supplier> findByTenantIdAndIsActiveTrue(Long tenantId);

    Page<Supplier> findByTenantId(Long tenantId, Pageable pageable);

    @Query("SELECT s FROM Supplier s WHERE s.tenantId = :tenantId AND " +
           "(:search IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(s.contactPerson) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(s.email) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(s.phone) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(s.mobile) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(s.city) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(s.country) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(s.taxNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Supplier> searchSuppliers(@Param("tenantId") Long tenantId,
                                   @Param("search") String search,
                                   Pageable pageable);
}

