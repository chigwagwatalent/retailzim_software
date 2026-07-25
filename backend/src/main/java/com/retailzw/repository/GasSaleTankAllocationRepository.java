package com.retailzw.repository;

import com.retailzw.model.GasSaleTankAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GasSaleTankAllocationRepository extends JpaRepository<GasSaleTankAllocation, Long> {
    List<GasSaleTankAllocation> findByGasSaleIdOrderByTankId(Long gasSaleId);
}
