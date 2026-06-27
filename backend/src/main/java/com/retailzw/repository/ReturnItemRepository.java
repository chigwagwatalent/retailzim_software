package com.retailzw.repository;

import com.retailzw.model.ReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReturnItemRepository extends JpaRepository<ReturnItem, Long> {

    List<ReturnItem> findByReturnRecordId(Long returnId);
}
