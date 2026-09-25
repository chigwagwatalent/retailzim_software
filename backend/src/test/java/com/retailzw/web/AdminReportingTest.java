package com.retailzw.web;

import com.retailzw.controller.web.PlatformAdminController;
import com.retailzw.repository.*;
import com.retailzw.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminReportingTest {
    @Mock TenantRepository tenants;
    @Mock SaasPlanRepository plans;
    @Mock TenantChatMessageRepository chatMessages;
    @InjectMocks PlatformAdminController controller;

    @Test void registrationsUseOneBoundedQueryAndFillMissingMonths() {
        LocalDate today = LocalDate.of(2026,9,11);
        when(tenants.registrationCounts(today.withDayOfMonth(1).minusMonths(5).atStartOfDay(), today.plusDays(1).atStartOfDay()))
                .thenReturn(List.<Object[]>of(new Object[]{2026,9,4L}));
        Map<String,Object> chart = ReflectionTestUtils.invokeMethod(controller,"registrationChart",today);
        assertThat((List<?>) chart.get("points")).hasSize(6);
        assertThat(chart.get("max")).isEqualTo(4L);
        assertThat(chart.get("line")).isEqualTo("54,170 162,170 270,170 378,170 486,170 594,30");
        verify(tenants).registrationCounts(today.withDayOfMonth(1).minusMonths(5).atStartOfDay(),today.plusDays(1).atStartOfDay());
        verifyNoMoreInteractions(tenants);
    }

    @Test void subscriptionTotalsUseGroupedCountsRatherThanLoadingEveryTenant() {
        SaasPlan plan = new SaasPlan(); plan.setId(2L); plan.setName("Growth"); plan.setPriceUsd(new BigDecimal("30")); plan.setPriceZwg(new BigDecimal("900"));
        when(plans.findAll()).thenReturn(List.of(plan));
        when(tenants.activeCountsByPlan()).thenReturn(List.<Object[]>of(new Object[]{2L,100000L},new Object[]{null,7L}));
        ExtendedModelMap model = new ExtendedModelMap();
        controller.subscriptions(null,model);
        assertThat(model.get("monthlyRevenueUsd")).isEqualTo(new BigDecimal("3000000"));
        assertThat(model.get("monthlyRevenueZwg")).isEqualTo(new BigDecimal("90000000"));
        verify(tenants,never()).findAll();
        verify(tenants).activeCountsByPlan();
    }
}
