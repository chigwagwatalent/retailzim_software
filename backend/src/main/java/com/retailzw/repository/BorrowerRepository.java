package com.retailzw.repository;

import com.retailzw.model.Borrower;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BorrowerRepository extends JpaRepository<Borrower, Long> {
    List<Borrower> findByTenantIdAndIsActiveTrueOrderByFullNameAsc(Long tenantId);
    Optional<Borrower> findByTenantIdAndAccountNumber(Long tenantId, String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Borrower b where b.id = :id and b.tenantId = :tenantId")
    Optional<Borrower> lockById(@Param("tenantId") Long tenantId, @Param("id") Long id);

    @Query("select b from Borrower b where b.tenantId = :tenantId and " +
            "(:search is null or lower(b.fullName) like lower(concat('%', :search, '%')) or " +
            "lower(b.phone) like lower(concat('%', :search, '%')) or " +
            "lower(b.accountNumber) like lower(concat('%', :search, '%'))) order by b.fullName")
    Page<Borrower> search(@Param("tenantId") Long tenantId, @Param("search") String search, Pageable pageable);
}
