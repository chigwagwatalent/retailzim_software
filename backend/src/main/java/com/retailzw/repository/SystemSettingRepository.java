package com.retailzw.repository;

import com.retailzw.model.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SystemSettingRepository extends JpaRepository<SystemSetting, Long> {

    Optional<SystemSetting> findByTenantIdAndSettingKey(Long tenantId, String settingKey);

    List<SystemSetting> findByTenantId(Long tenantId);

    List<SystemSetting> findByTenantIdAndSettingGroup(Long tenantId, String settingGroup);
}

