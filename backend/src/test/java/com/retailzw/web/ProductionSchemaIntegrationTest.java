package com.retailzw.web;

import com.retailzw.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

/** Validate Flyway-only schema against every JPA mapping without creating fixtures. */
@EnabledIfSystemProperty(named="retailzw.schemaAudit",matches="true")
@SpringBootTest(properties={"spring.flyway.enabled=true","spring.flyway.baseline-on-migrate=false","spring.jpa.hibernate.ddl-auto=validate"})
class ProductionSchemaIntegrationTest {
    @MockBean(name="seedData") CommandLineRunner seedData;
    @MockBean SmilePayReconciliationService reconciliation;
    @MockBean PaymentNotificationWorker notifications;
    @MockBean BillingAutomationService billing;
    @Test void flywaySchemaMatchesJpaMappings() {}
}
