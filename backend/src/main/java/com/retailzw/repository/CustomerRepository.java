package com.retailzw.repository;

import com.retailzw.model.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByTenantIdAndLoyaltyCardNumber(Long tenantId, String loyaltyCardNumber);

    Optional<Customer> findByTenantIdAndPhone(Long tenantId, String phone);

    Optional<Customer> findByTenantIdAndEmail(Long tenantId, String email);

    Page<Customer> findByTenantId(Long tenantId, Pageable pageable);

    @Query("SELECT c FROM Customer c WHERE c.tenantId = :tenantId AND " +
           "(:search IS NULL OR LOWER(c.phone) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(c.loyaltyCardNumber) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(c.firstName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(c.lastName) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Customer> searchCustomers(@Param("tenantId") Long tenantId,
                                   @Param("search") String search,
                                   Pageable pageable);

    boolean existsByTenantIdAndPhone(Long tenantId, String phone);

    boolean existsByTenantIdAndEmail(Long tenantId, String email);

    boolean existsByLoyaltyCardNumber(String loyaltyCardNumber);

    long countByTenantId(Long tenantId);
}

