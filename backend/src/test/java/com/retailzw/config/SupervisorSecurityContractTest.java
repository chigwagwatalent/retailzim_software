package com.retailzw.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SupervisorSecurityContractTest {

    @Test
    void supervisorRoutesUseAnExplicitOperationalAllowlist() throws IOException {
        String security = Files.readString(Path.of("src/main/java/com/retailzw/config/SecurityConfig.java"));
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V46__add_branch_supervisor_role.sql"));

        assertTrue(security.contains("\"SUPERVISOR\""));
        assertTrue(security.contains("\"/shop/supervisor\""));
        assertTrue(security.contains("\"/shop/cash/shifts/collect\""));
        assertTrue(security.contains("\"/shop/change/*/collect\""));
        assertTrue(security.contains("\"/shop/purchasing/*/receive\""));
        assertTrue(security.contains("\"/shop/gas/restocking\""));
        assertTrue(security.contains("\"/shop/gas/restocks\""));
        assertTrue(security.contains("\"/shop/gas/change/*/collect\""));
        assertTrue(security.contains(".anyRequest().hasAnyRole(\"SUPER_ADMIN\", \"ACCOUNTANT\")"),
                "Owner-only routes must stay closed to supervisors by default");
        assertTrue(migration.contains("'SUPERVISOR'"));
        assertTrue(migration.contains("ALTER TABLE roles"));
    }
}
