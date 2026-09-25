package com.retailzw.web;

import com.retailzw.controller.api.RetailApiController;
import com.retailzw.model.*;
import com.retailzw.repository.*;
import com.retailzw.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BranchStockPagingTest {
    @Mock CurrentUserService current;
    @Mock InventoryRepository inventory;
    @Mock ProductRepository products;
    @Mock WholesalePricingService wholesalePricingService;
    @InjectMocks RetailApiController controller;

    @Test void branchStockUsesBoundedBulkReadsAndPreservesAvailableStock() {
        when(current.tenantId()).thenReturn(7L);
        when(current.branchId()).thenReturn(2L);
        Inventory stock = new Inventory(); stock.setProductId(9L); stock.setQuantityOnHand(new BigDecimal("12")); stock.setQuantityReserved(new BigDecimal("2"));
        Product product = new Product(); product.setId(9L); product.setTenantId(7L); product.setName("Milk");
        when(inventory.findBranchProductStockPage(7L,2L,null,null,PageRequest.of(2,50))).thenReturn(List.of(stock));
        when(products.findByTenantIdAndIdIn(7L,List.of(9L))).thenReturn(List.of(product));
        when(wholesalePricingService.configurations(7L,List.of(9L))).thenReturn(Map.of());
        var result = controller.productsForBranch(null,null,null,2,50);
        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0)).containsEntry("branchId",2L).containsEntry("name","Milk")
                .containsEntry("quantityAvailable",new BigDecimal("10")).containsEntry("wholesaleEnabled",false);
        verify(inventory).findBranchProductStockPage(7L,2L,null,null,PageRequest.of(2,50));
        verify(products).findByTenantIdAndIdIn(7L,List.of(9L));
        verify(wholesalePricingService).configurations(7L,List.of(9L));
        verifyNoMoreInteractions(inventory,products,wholesalePricingService);
    }

    @Test void oversizedAndNegativePagesAreRejectedBeforeInventoryQueries() {
        when(current.branchId()).thenReturn(2L);
        assertThatThrownBy(() -> controller.productsForBranch(null,null,null,0,501))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> controller.productsForBranch(null,null,null,-1,50))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(inventory,products,wholesalePricingService);
    }
}
