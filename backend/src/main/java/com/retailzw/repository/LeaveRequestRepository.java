package com.retailzw.repository;

import com.retailzw.model.LeaveRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findByTenantIdAndBranchIdAndStatus(Long tenantId, Long branchId, LeaveRequest.LeaveStatus status);

    Page<LeaveRequest> findByTenantIdAndBranchId(Long tenantId, Long branchId, Pageable pageable);

    List<LeaveRequest> findByUserId(Long userId);

    Page<LeaveRequest> findByTenantIdAndStatus(Long tenantId, LeaveRequest.LeaveStatus status, Pageable pageable);
}

