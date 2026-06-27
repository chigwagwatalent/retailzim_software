package com.retailzw.repository;

import com.retailzw.model.UnitOfMeasure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface UnitOfMeasureRepository extends JpaRepository<UnitOfMeasure, Long> {

    List<UnitOfMeasure> findByTenantId(Long tenantId);

    boolean existsByTenantIdAndAbbreviation(Long tenantId, String abbreviation);
}

