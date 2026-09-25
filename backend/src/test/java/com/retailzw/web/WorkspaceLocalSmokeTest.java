package com.retailzw.web;

import com.retailzw.enums.UserRole;
import com.retailzw.model.User;
import com.retailzw.model.Role;
import com.retailzw.repository.*;
import com.retailzw.security.CustomUserDetails;
import com.retailzw.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

/** Opt-in smoke test against the configured LOCAL database. No fixture writes; model updates roll back. */
@EnabledIfSystemProperty(named="retailzw.localSmoke",matches="true")
@SpringBootTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=none"})
@AutoConfigureMockMvc
@Transactional
class WorkspaceLocalSmokeTest {
    @MockBean(name="seedData") CommandLineRunner seedData;
    @MockBean SmilePayReconciliationService reconciliation;
    @MockBean PaymentNotificationWorker notificationWorker;
    @MockBean BillingAutomationService billingAutomation;
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired SaasAdminRepository admins;
    @Autowired BranchRepository branches;
    @Autowired BranchActivityService activity;

    @Test void tenantPagesRenderAcrossAllAndSelectedBranches() throws Exception {
        User original=users.findAllByUsernameForMobileLogin("accountant").stream().filter(u -> Boolean.TRUE.equals(u.getIsActive())).findFirst().orElseThrow();
        // Detached test principal represents a tenant-wide accountant; never modify the stored user.
        User account=new User();account.setId(original.getId());account.setUsername(original.getUsername());
        account.setTenantId(original.getTenantId());account.setPasswordHash(original.getPasswordHash());account.setIsActive(true);
        Role role=new Role();role.setName(UserRole.ACCOUNTANT);account.setRole(role);
        var principal=new CustomUserDetails(account);
        mvc.perform(get("/shop/categories").with(user(new CustomUserDetails(original)))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"workspace-branch\"")));
        for(String path:new String[]{"dashboard","categories","products","inventory","inventory-intelligence","sales","cash","returns","purchasing","reports","change","users","customers","suppliers"}) {
            String body=mvc.perform(get("/shop/"+path).with(user(principal))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(body).as(path).contains("All branches").doesNotContain("Whitelabel Error");
            if(java.util.Set.of("dashboard","categories","products").contains(path)) capture(path,body);
        }
        Long branch=branches.findByTenantIdAndIsActiveTrue(account.getTenantId()).get(0).getId();
        var available=branches.findByTenantIdAndIsActiveTrue(account.getTenantId());
        Long other=available.get(available.size()-1).getId();
        var session=new org.springframework.mock.web.MockHttpSession();
        mvc.perform(get("/shop/cash").param("branchId",other.toString()).session(session).with(user(new CustomUserDetails(original))))
                .andExpect(status().isOk()).andExpect(model().attribute("selectedBranchId",other));
        mvc.perform(get("/shop/sales").session(session).with(user(new CustomUserDetails(original))))
                .andExpect(status().isOk()).andExpect(model().attribute("selectedBranchId",other));
        mvc.perform(get("/shop/reports").param("branchId","0").session(session).with(user(new CustomUserDetails(original))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("All branches")));
        for(String path:new String[]{"categories","products","inventory","sales","cash","returns","purchasing","reports","change","users"})
            mvc.perform(get("/shop/"+path).param("branchId",branch.toString()).with(user(principal))).andExpect(status().isOk());
        for(String module:new String[]{"gas","gas-sales","gas-change","gas-restocking","gas-expenses","gas-tanks","gas-accounting"})
            activity.page(account.getTenantId(),module,null,0,25,new org.springframework.ui.ExtendedModelMap());
    }
    @Test void adminSettingsRender() throws Exception {
        String name=admins.findAll().stream().filter(a -> Boolean.TRUE.equals(a.getIsActive())).findFirst().orElseThrow().getUsername();
        String body=mvc.perform(get("/admin/settings").with(user(name).roles("SAAS_ADMIN"))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Platform administrators"))).andReturn().getResponse().getContentAsString();
        capture("admin-settings",body);
    }
    private void capture(String name,String body) throws Exception {
        var dir=java.nio.file.Path.of("target/workspace-smoke");java.nio.file.Files.createDirectories(dir);
        java.nio.file.Files.writeString(dir.resolve(name+".html"),body);
    }
}
