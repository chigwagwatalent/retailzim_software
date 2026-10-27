package com.retailzw.web;

import com.retailzw.dto.request.FuelStationRequests.*;
import com.retailzw.enums.*;
import com.retailzw.model.*;
import com.retailzw.repository.*;
import com.retailzw.security.CustomUserDetails;
import com.retailzw.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Upgrade-only checks on a restored disposable copy; real entitlement and database services. */
@EnabledIfSystemProperty(named="retailzw.fuelUpgrade",matches="true")
@SpringBootTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=none"})
@AutoConfigureMockMvc
@Transactional
class FuelUpgradeIntegrationTest {
    @MockBean(name="seedData") CommandLineRunner seedData;
    @MockBean SmilePayReconciliationService reconciliation;
    @MockBean PaymentNotificationWorker notificationWorker;
    @MockBean BillingAutomationService billing;
    @MockBean EmailService email;
    @Autowired TenantRepository tenants;
    @Autowired SaasPlanRepository plans;
    @Autowired TenantSubscriptionRepository subscriptions;
    @Autowired BranchRepository branches;
    @Autowired RoleRepository roles;
    @Autowired UserRepository users;
    @Autowired PackageModuleAccessService modules;
    @Autowired FuelStationService fuel;
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    long tenant,branch,actor;
    User admin;
    static BigDecimal n(String value){return new BigDecimal(value);}
    @BeforeEach void fixtures(){
        String databaseUrl=db.execute((java.sql.Connection c)->c.getMetaData().getURL());
        assertThat(databaseUrl).contains("retailzim_audit_");
        var plan=plans.findByCode("FUEL_GROWTH").orElseThrow();
        String id=UUID.randomUUID().toString().substring(0,8);
        var company=tenants.saveAndFlush(Tenant.builder().tenantCode("FUELAUD"+id).companyName("Fuel upgrade audit").email(id+"@example.invalid").planId(plan.getId()).status(Tenant.TenantStatus.ACTIVE).subscriptionStart(LocalDateTime.now()).subscriptionEnd(LocalDateTime.now().plusMonths(1)).build());
        tenant=company.getId();
        subscriptions.saveAndFlush(TenantSubscription.builder().tenantId(tenant).planId(plan.getId()).status(TenantSubscription.SubscriptionStatus.ACTIVE).startsAt(LocalDateTime.now()).endsAt(LocalDateTime.now().plusMonths(1)).currency(CurrencyCode.USD).amountPaid(n("49")).build());
        branch=branches.saveAndFlush(Branch.builder().tenantId(tenant).branchCode("FUELAUD"+id).name("Fuel audit station").moduleType(BusinessModule.FUEL_MODULE).build()).getId();
        admin=users.saveAndFlush(User.builder().tenantId(tenant).username("audit"+id).email("actor"+id+"@example.invalid").firstName("Audit").lastName("Operator").passwordHash("unused-test-principal").role(roles.findByName(UserRole.SUPER_ADMIN).orElseThrow()).build());actor=admin.getId();
        assertThat(modules.syncAndGetEnabledModules(tenant)).containsExactly(BusinessModule.FUEL_MODULE);
    }
    @Test void entitledFuelAdminPagesAndCashierApiRender() throws Exception {
        var principal=new CustomUserDetails(admin);
        for(String section:List.of("","/setup","/deliveries","/pricing","/shifts","/reports","/sales","/stock","/expenses"))
            mvc.perform(get("/shop/fuel"+section).param("branchId",Long.toString(branch)).with(user(principal))).andExpect(status().isOk());
        mvc.perform(get("/api/fuel/pos/bootstrap").param("branchId",Long.toString(branch)).with(user(principal))).andExpect(status().isForbidden());
        var cashier=User.builder().id(actor).tenantId(tenant).branchId(branch).username("auditcashier").firstName("Audit").lastName("Cashier").passwordHash("unused").role(roles.findByName(UserRole.CASHIER).orElseThrow()).build();
        mvc.perform(get("/shop/fuel/setup").with(user(new CustomUserDetails(cashier)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/fuel/pos/bootstrap").param("branchId",Long.toString(branch)).with(user(new CustomUserDetails(cashier)))).andExpect(status().isOk());
    }
    @Test void fuelShiftSeparatesCardFromCashAndPreservesStockOnReplay(){
        long grade=fuel.addGrade(tenant,"DIESEL","Diesel","#123456");
        long tank=fuel.addTank(tenant,null,branch,grade,"T1",n("1000"),n("100"),n("10"));
        long pump=fuel.addPump(tenant,null,branch,"P1");long nozzle=fuel.addNozzle(tenant,null,branch,pump,tank,grade,"N1",n("0"));
        fuel.setPrice(tenant,actor,null,branch,grade,"USD",n("2"));
        fuel.delivery(tenant,actor,null,new Delivery(branch,tank,"Audit supplier","AUDDEL",n("100"),n("100"),n("100"),n("200"),n("100"),"USD"));
        long shift=fuel.openShift(tenant,actor,null,new OpenShift(branch,n("5"),n("0")));
        var sale=new com.retailzw.dto.request.FuelStationRequests.Sale(branch,shift,nozzle,n("5"),"USD",List.of(new Payment("CASH","USD",n("4"),null),new Payment("CARD","USD",n("6"),"TEST-CARD")),"AUD-REPLAY",null,null);
        long first=fuel.sale(tenant,actor,null,sale);assertThat(fuel.sale(tenant,actor,null,sale)).isEqualTo(first);
        assertThat(db.queryForObject("select book_stock_l from fuel_tanks where id=?",BigDecimal.class,tank)).isEqualByComparingTo("195");
        fuel.closeShift(tenant,actor,null,new CloseShift(branch,shift,n("9"),n("0"),List.of(new MeterReading(nozzle,n("5"))),List.of(new DipReading(tank,n("195"),n("0"))),"upgrade acceptance"));
        var values=db.queryForMap("select expected_usd,cash_variance_usd,meter_variance_l,wet_variance_l from fuel_shifts where id=?",shift);
        assertThat((BigDecimal)values.remove("expected_usd")).isEqualByComparingTo("9");
        for(Object value:values.values())assertThat((BigDecimal)value).isEqualByComparingTo("0");
        fuel.expense(tenant,actor,null,branch,"Maintenance","Audit maintenance",n("3"),"USD","AUD-EXP",java.time.LocalDate.now());
        var summary=fuel.financialSummary(tenant,null,branch);
        assertThat(summary).hasSize(1);
        assertThat((BigDecimal)summary.get(0).get("Revenue")).isEqualByComparingTo("10");
        assertThat((BigDecimal)summary.get(0).get("Fuel_purchases")).isEqualByComparingTo("100");
        assertThat((BigDecimal)summary.get(0).get("Expenses")).isEqualByComparingTo("3");
        assertThatThrownBy(()->fuel.dashboard(tenant,null,Long.MAX_VALUE)).isInstanceOf(IllegalArgumentException.class);
    }
}
