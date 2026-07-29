package com.retailzw.repository;

import com.retailzw.enums.CurrencyCode;
import com.retailzw.model.HeldChange;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

public interface HeldChangeRepository extends JpaRepository<HeldChange, Long> {
    Optional<HeldChange> findByTenantIdAndOfflineReference(Long tenantId, String offlineReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from HeldChange c where c.tenantId = :tenantId and c.offlineReference = :reference")
    Optional<HeldChange> lockByOfflineReference(@Param("tenantId") Long tenantId, @Param("reference") String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from HeldChange c where c.id = :id and c.tenantId = :tenantId")
    Optional<HeldChange> lockById(@Param("tenantId") Long tenantId, @Param("id") Long id);

    @Query("select c from HeldChange c where c.tenantId = :tenantId and " +
            "(:status is null or c.status = :status) and " +
            "(:search is null or lower(c.customerName) like lower(concat('%', :search, '%')) or " +
            "lower(c.phone) like lower(concat('%', :search, '%')) or " +
            "lower(c.referenceNumber) like lower(concat('%', :search, '%'))) order by c.createdAt desc")
    Page<HeldChange> search(@Param("tenantId") Long tenantId,
                            @Param("status") HeldChange.Status status,
                            @Param("search") String search,
                            Pageable pageable);

    @Query("select c from HeldChange c where c.tenantId = :tenantId and " +
            "(:status is null or c.status = :status) and " +
            "(:fromDate is null or c.createdAt >= :fromDate) and " +
            "(:toDate is null or c.createdAt < :toDate) and " +
            "(:search is null or lower(c.customerName) like lower(concat('%', :search, '%')) or " +
            "lower(c.phone) like lower(concat('%', :search, '%')) or " +
            "lower(c.referenceNumber) like lower(concat('%', :search, '%')) or " +
            "lower(c.offlineReference) like lower(concat('%', :search, '%'))) order by c.createdAt desc")
    Page<HeldChange> searchWithDates(@Param("tenantId") Long tenantId,
                                      @Param("status") HeldChange.Status status,
                                      @Param("search") String search,
                                      @Param("fromDate") LocalDateTime fromDate,
                                      @Param("toDate") LocalDateTime toDate,
                                      Pageable pageable);

    @Query("select coalesce(sum(c.amount), 0) from HeldChange c where c.tenantId = :tenantId and " +
            "c.currency = :currency and " +
            "(:status is null or c.status = :status) and " +
            "(:fromDate is null or c.createdAt >= :fromDate) and " +
            "(:toDate is null or c.createdAt < :toDate) and " +
            "(:search is null or lower(c.customerName) like lower(concat('%', :search, '%')) or " +
            "lower(c.phone) like lower(concat('%', :search, '%')) or " +
            "lower(c.referenceNumber) like lower(concat('%', :search, '%')) or " +
            "lower(c.offlineReference) like lower(concat('%', :search, '%')))")
    java.math.BigDecimal sumSearchWithDates(@Param("tenantId") Long tenantId,
                                            @Param("status") HeldChange.Status status,
                                            @Param("search") String search,
                                            @Param("fromDate") LocalDateTime fromDate,
                                            @Param("toDate") LocalDateTime toDate,
                                            @Param("currency") CurrencyCode currency);

    long countByTenantIdAndStatus(Long tenantId, HeldChange.Status status);

    List<HeldChange> findTop100ByTenantIdAndBranchIdAndGasShiftIdIsNotNullAndStatusOrderByCreatedAtDesc(
            Long tenantId, Long branchId, HeldChange.Status status);

    long countByTenantIdAndBranchIdAndGasShiftIdIsNotNullAndStatus(
            Long tenantId, Long branchId, HeldChange.Status status);

    @Query("""
            select c from HeldChange c
            where c.tenantId = :tenantId
              and c.branchId = :branchId
              and c.gasShiftId is not null
              and (:status is null or c.status = :status)
              and (:fromDate is null or c.createdAt >= :fromDate)
              and (:toDate is null or c.createdAt < :toDate)
              and (:search is null
                   or lower(c.customerName) like lower(concat('%', :search, '%'))
                   or lower(c.phone) like lower(concat('%', :search, '%'))
                   or lower(c.referenceNumber) like lower(concat('%', :search, '%'))
                   or lower(c.offlineReference) like lower(concat('%', :search, '%')))
            order by c.createdAt desc, c.id desc
            """)
    Page<HeldChange> searchGas(@Param("tenantId") Long tenantId,
                               @Param("branchId") Long branchId,
                               @Param("status") HeldChange.Status status,
                               @Param("search") String search,
                               @Param("fromDate") LocalDateTime fromDate,
                               @Param("toDate") LocalDateTime toDate,
                               Pageable pageable);

    @Query("""
            select coalesce(sum(c.amount), 0) from HeldChange c
            where c.tenantId = :tenantId
              and c.branchId = :branchId
              and c.gasShiftId is not null
              and c.currency = :currency
              and (:status is null or c.status = :status)
              and (:fromDate is null or c.createdAt >= :fromDate)
              and (:toDate is null or c.createdAt < :toDate)
              and (:search is null
                   or lower(c.customerName) like lower(concat('%', :search, '%'))
                   or lower(c.phone) like lower(concat('%', :search, '%'))
                   or lower(c.referenceNumber) like lower(concat('%', :search, '%'))
                   or lower(c.offlineReference) like lower(concat('%', :search, '%')))
            """)
    java.math.BigDecimal sumGas(@Param("tenantId") Long tenantId,
                                @Param("branchId") Long branchId,
                                @Param("status") HeldChange.Status status,
                                @Param("search") String search,
                                @Param("fromDate") LocalDateTime fromDate,
                                @Param("toDate") LocalDateTime toDate,
                                @Param("currency") CurrencyCode currency);
}
