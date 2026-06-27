package com.retailzw.repository;

import com.retailzw.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameAndTenantId(String username, Long tenantId);

    Optional<User> findByEmailAndTenantId(String email, Long tenantId);

    List<User> findByEmailIgnoreCase(String email);

    /**
     * Only safe when tenantId is also provided — username is NOT globally unique.
     * For Spring Security auth use findAllByUsernameForMobileLogin() instead.
     */
    Optional<User> findByUsername(String username);

    /**
     * Returns all users with this username across every tenant.
     * Used by UserDetailsServiceImpl to avoid NonUniqueResultException
     * in multi-tenant environments where two tenants share the same username.
     */
    @Query("SELECT u FROM User u JOIN FETCH u.role WHERE LOWER(u.username) = LOWER(:username)")
    List<User> findAllByUsernameForMobileLogin(@Param("username") String username);

    List<User> findByTenantId(Long tenantId);

    Page<User> findByTenantId(Long tenantId, Pageable pageable);

    List<User> findByBranchId(Long branchId);

    Page<User> findByTenantIdAndBranchId(Long tenantId, Long branchId, Pageable pageable);

    boolean existsByUsernameAndTenantId(String username, Long tenantId);

    boolean existsByEmailAndTenantId(String email, Long tenantId);

    @Query("SELECT u FROM User u WHERE u.tenantId = :tenantId AND " +
           "(:search IS NULL OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> searchUsers(@Param("tenantId") Long tenantId, @Param("search") String search, Pageable pageable);

    List<User> findByTenantIdAndIsActive(Long tenantId, Boolean isActive);
}
