package com.retailzw.repository;


import com.retailzw.enums.UserRole;
import com.retailzw.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(UserRole name);

    boolean existsByName(UserRole name);
}

