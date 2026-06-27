package com.retailzw.repository;

import com.retailzw.model.SaasAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface SaasAdminRepository extends JpaRepository<SaasAdmin, Long> {

    Optional<SaasAdmin> findByUsername(String username);

    Optional<SaasAdmin> findByEmail(String email);

    Optional<SaasAdmin> findByUsernameIgnoreCase(String username);

    Optional<SaasAdmin> findByEmailIgnoreCase(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}

