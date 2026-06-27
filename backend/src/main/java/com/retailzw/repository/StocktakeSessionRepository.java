package com.retailzw.repository;

import com.retailzw.model.StocktakeSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface StocktakeSessionRepository extends JpaRepository<StocktakeSession, Long> {

    Page<StocktakeSession> findByTenantIdAndBranchId(Long tenantId, Long branchId, Pageable pageable);

    Optional<StocktakeSession> findBySessionNumber(String sessionNumber);

    List<StocktakeSession> findByTenantIdAndBranchIdAndStatus(Long tenantId, Long branchId,
                                                               StocktakeSession.StocktakeStatus status);
}

