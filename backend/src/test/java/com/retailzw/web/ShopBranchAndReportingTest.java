package com.retailzw.web;

import com.retailzw.controller.web.ShopWebController;
import com.retailzw.enums.CurrencyCode;
import com.retailzw.model.Branch;
import com.retailzw.repository.BranchRepository;
import com.retailzw.repository.SalePaymentRepository;
import com.retailzw.repository.SaleRepository;
import com.retailzw.service.CurrentUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShopBranchAndReportingTest {
    @Mock CurrentUserService current;
    @Mock BranchRepository branches;
    @Mock SalePaymentRepository salePayments;
    @Mock SaleRepository sales;
    @InjectMocks ShopWebController controller;

    @AfterEach void clearRequest() { RequestContextHolder.resetRequestAttributes(); }

    private Branch branch(long id) {
        Branch b = new Branch(); b.setId(id); b.setTenantId(7L); b.setName("Branch " + id); b.setIsActive(true); return b;
    }

    @Test void hqSelectionPersistsAndCannotSelectAnotherTenant() {
        when(current.roleName()).thenReturn("SUPER_ADMIN");
        when(current.tenantId()).thenReturn(7L);
        when(current.userId()).thenReturn(10L);
        when(branches.findByTenantIdAndIsActiveTrue(7L)).thenReturn(List.of(branch(1), branch(2)));
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertThat(controller.selectWorkspaceBranch(2L, request.getSession(), "/shop/dashboard")).isEqualTo("redirect:/shop/dashboard");
        assertThat((Long) ReflectionTestUtils.invokeMethod(controller, "activeBranch")).isEqualTo(2L);
        assertThatThrownBy(() -> controller.selectWorkspaceBranch(999L, request.getSession(), "/shop/dashboard")).isInstanceOf(AccessDeniedException.class);
        assertThat((Long) ReflectionTestUtils.invokeMethod(controller, "activeBranch")).isEqualTo(2L);
        when(branches.findByTenantIdAndIsActiveTrue(7L)).thenReturn(List.of(branch(1)));
        assertThat((Long) ReflectionTestUtils.invokeMethod(controller, "reportingBranch", (Object)null)).isNull();
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(controller, "activeBranch")).isInstanceOf(AccessDeniedException.class);
    }

    @Test void assignedAdministratorCanSwitchWithinTenant() {
        when(current.roleName()).thenReturn("SUPER_ADMIN");
        when(current.tenantId()).thenReturn(7L);
        when(branches.findByTenantIdAndIsActiveTrue(7L)).thenReturn(List.of(branch(1),branch(2)));
        assertThat(controller.selectWorkspaceBranch(2L, new MockHttpServletRequest().getSession(), "/shop/categories")).isEqualTo("redirect:/shop/categories");
    }

    @Test void allBranchesIsDefaultAndCannotBecomeAnImplicitWriteTarget() {
        when(current.roleName()).thenReturn("SUPER_ADMIN");when(current.tenantId()).thenReturn(7L);when(current.userId()).thenReturn(10L);
        MockHttpServletRequest request=new MockHttpServletRequest();RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertThat((Long)ReflectionTestUtils.invokeMethod(controller,"reportingBranch",(Object)null)).isNull();
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(controller,"activeBranch")).isInstanceOf(AccessDeniedException.class);
        assertThat(controller.selectWorkspaceBranch(0L,request.getSession(),"/shop/categories?branchId=2")).isEqualTo("redirect:/shop/categories");
        assertThat(controller.selectWorkspaceBranch(0L,request.getSession(),"https://outside.example/")).isEqualTo("redirect:/shop/dashboard");
        assertThat(request.getSession().getAttribute("retailzw.workspace.branch.7.10")).isEqualTo(0L);
    }

    @Test void cashierCannotSwitchWorkspace() {
        when(current.roleName()).thenReturn("CASHIER");
        assertThatThrownBy(() -> controller.selectWorkspaceBranch(2L, new MockHttpServletRequest().getSession(), "/shop/dashboard"))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(branches);
    }

    @Test void pageFilterSelectionPersistsAcrossRequestsAndAllBranchesClearsIt() {
        when(current.roleName()).thenReturn("ACCOUNTANT");when(current.tenantId()).thenReturn(7L);when(current.userId()).thenReturn(10L);
        when(branches.findByTenantIdAndIsActiveTrue(7L)).thenReturn(List.of(branch(1),branch(2)));
        MockHttpServletRequest first=new MockHttpServletRequest();RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(first));
        assertThat((Long)ReflectionTestUtils.invokeMethod(controller,"reportingBranch",2L)).isEqualTo(2L);
        MockHttpServletRequest next=new MockHttpServletRequest();next.setSession(first.getSession());RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(next));
        assertThat((Long)ReflectionTestUtils.invokeMethod(controller,"reportingBranch",(Object)null)).isEqualTo(2L);
        assertThat((Long)ReflectionTestUtils.invokeMethod(controller,"reportingBranch",0L)).isNull();
        assertThat(next.getSession().getAttribute("retailzw.workspace.branch.7.10")).isEqualTo(0L);
    }

    @Test void branchComparisonUsesTwoQueriesEvenForHundredBranches() {
        LocalDateTime from = LocalDate.of(2026,9,11).atStartOfDay(), to = from.plusDays(1);
        when(salePayments.sumCompletedByBranch(7L, from, to)).thenReturn(List.<Object[]>of(new Object[]{2L, CurrencyCode.USD, new BigDecimal("12.50")}));
        when(sales.countCompletedByBranch(7L, from, to)).thenReturn(List.<Object[]>of(new Object[]{2L, 3L}));
        List<?> result = ReflectionTestUtils.invokeMethod(controller, "dashboardBranchPerformance", 7L,
                LongStream.rangeClosed(1,100).mapToObj(this::branch).toList(), from, to);
        assertThat(result).hasSize(5);
        assertThat(result.get(0).toString()).contains("Branch 2", "USD 12.50");
        verify(salePayments).sumCompletedByBranch(7L,from,to);
        verify(sales).countCompletedByBranch(7L,from,to);
        verifyNoMoreInteractions(salePayments,sales);
    }

    @Test void chartUsesOneTenantAndBranchScopedQueryAndFillsMissingDays() {
        LocalDate today = LocalDate.of(2026,9,11);
        LocalDateTime from = today.minusDays(6).atStartOfDay(), to = today.plusDays(1).atStartOfDay();
        when(salePayments.sumCompletedByDay(7L, 2L, CurrencyCode.USD, from, to))
                .thenReturn(List.<Object[]>of(new Object[]{2026,9,11,new BigDecimal("12.50")}));
        Object result = ReflectionTestUtils.invokeMethod(controller,"dashboardSalesChart",7L,2L,from,today);
        assertThat(result.toString()).contains("USD 12.50", "Sep 5", "Sep 11");
        verify(salePayments).sumCompletedByDay(7L,2L,CurrencyCode.USD,from,to);
        verifyNoMoreInteractions(salePayments);
    }
}
