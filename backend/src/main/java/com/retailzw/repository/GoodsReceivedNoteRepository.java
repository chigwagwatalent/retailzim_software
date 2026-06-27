package com.retailzw.repository;

import com.retailzw.model.GoodsReceivedNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface GoodsReceivedNoteRepository extends JpaRepository<GoodsReceivedNote, Long> {

    Optional<GoodsReceivedNote> findByGrnNumber(String grnNumber);

    List<GoodsReceivedNote> findByTenantIdAndBranchId(Long tenantId, Long branchId);

    List<GoodsReceivedNote> findByPurchaseOrderId(Long purchaseOrderId);
}

