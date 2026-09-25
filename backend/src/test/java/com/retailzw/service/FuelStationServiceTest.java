package com.retailzw.service;

import com.retailzw.enums.BusinessModule;
import com.retailzw.model.Branch;
import com.retailzw.repository.BranchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FuelStationServiceTest {
    NamedParameterJdbcTemplate db=mock(NamedParameterJdbcTemplate.class);
    BranchRepository branches=mock(BranchRepository.class);
    PackageModuleAccessService access=mock(PackageModuleAccessService.class);
    FuelStationService service=new FuelStationService(db,branches,access);
    @BeforeEach void setup(){when(branches.findByTenantIdAndIsActiveTrue(7L)).thenReturn(List.of(
        Branch.builder().id(10L).tenantId(7L).name("Harare").branchCode("HRE").moduleType(BusinessModule.FUEL_MODULE).isActive(true).build(),
        Branch.builder().id(11L).tenantId(7L).name("Bulawayo").branchCode("BYO").moduleType(BusinessModule.FUEL_MODULE).isActive(true).build()));}
    @Test void assignedUserCannotReadAnotherBranch(){assertThatThrownBy(()->service.records(7L,10L,11L,"sales",0)).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(db);}
    @Test void tenantCannotReadForeignBranch(){assertThatThrownBy(()->service.records(7L,null,999L,"sales",0)).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(db);}
    @Test void noLongerAssignedFuelBranchIsRejected(){assertThatThrownBy(()->service.records(7L,999L,null,"sales",0)).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(db);}
    @Test void negativeDipRejectedBeforeDatabaseWrite(){assertThatThrownBy(()->service.recordDip(7L,1L,null,10L,1L,new BigDecimal("-1"),BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(db);}
    @Test void unknownPumpCommandIsRejected(){assertThatThrownBy(()->service.nozzleStatus(7L,1L,null,10L,1L,"DISPENSE")).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(db);}
    @Test void futureExpenseRejected(){assertThatThrownBy(()->service.expense(7L,1L,null,10L,"Maintenance","Pump repair",BigDecimal.TEN,"USD","X",LocalDate.now().plusDays(1))).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(db);}
    @Test void missingModuleEntitlementBlocksRecords(){doThrow(new IllegalStateException("No package access")).when(access).requireModule(7L,BusinessModule.FUEL_MODULE);assertThatThrownBy(()->service.records(7L,null,null,"sales",0)).isInstanceOf(IllegalStateException.class);verifyNoInteractions(db);}
}
