package com.retailzw.repository;

import com.retailzw.model.StaffProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface StaffProfileRepository extends JpaRepository<StaffProfile, Long> {

    Optional<StaffProfile> findByUserId(Long userId);

    List<StaffProfile> findByTenantId(Long tenantId);

    Page<StaffProfile> findByTenantIdAndBranchId(Long tenantId, Long branchId, Pageable pageable);
}

