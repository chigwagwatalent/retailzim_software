package com.retailzw.repository;

import com.retailzw.model.SaasPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SaasPlanRepository extends JpaRepository<SaasPlan, Long> {

    Optional<SaasPlan> findByCode(String code);

    List<SaasPlan> findByIsActiveTrue();

    boolean existsByCode(String code);
}

