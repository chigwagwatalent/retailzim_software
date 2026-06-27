package com.retailzw.repository;

import com.retailzw.model.TenantAnnouncement;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TenantAnnouncementRepository extends JpaRepository<TenantAnnouncement, Long> {
    List<TenantAnnouncement> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
