package com.retailzw.repository;

import com.retailzw.model.SaasAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface SaasAdminRepository extends JpaRepository<SaasAdmin, Long> {
    long countByIsActiveTrue();

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from SaasAdmin a order by a.id")
    java.util.List<SaasAdmin> lockAccounts();

    Optional<SaasAdmin> findByUsername(String username);

    Optional<SaasAdmin> findByEmail(String email);

    Optional<SaasAdmin> findByUsernameIgnoreCase(String username);

    Optional<SaasAdmin> findByEmailIgnoreCase(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}

