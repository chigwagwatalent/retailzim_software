package com.retailzw.web;

import com.retailzw.dto.request.*;
import com.retailzw.dto.request.FuelStationRequests;
import com.retailzw.enums.*;
import com.retailzw.model.*;
import com.retailzw.repository.*;
import com.retailzw.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Explicit opt-in, disposable MySQL database only; each workflow rolls back. */
@EnabledIfSystemProperty(named="retailzw.localSmoke",matches="true")
@SpringBootTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=none"})
@Transactional
class DatabaseWorkflowIntegrationTest {
    @MockBean(name="seedData") CommandLineRunner seedData;
    @MockBean SmilePayReconciliationService reconciliation;
    @MockBean PaymentNotificationWorker notifications;
    @MockBean BillingAutomationService billing;
    @MockBean EmailService email;
    // Test transaction persistence independently of package setup; entitlement has separate tests.
    @MockBean PackageModuleAccessService moduleAccess;
    @Autowired org.springframework.context.ApplicationContext context;
    @Autowired JdbcTemplate db;
    @Autowired EntityManager em;
    @Autowired BranchRepository branches;
    @Autowired RetailOperationsService retail;
    @Autowired ReturnService returns;
    @Autowired PurchaseOrderService purchasing;
    @Autowired SupplierRepository suppliers;
    @Autowired GasOperationsService gas;
    @Autowired FuelStationService fuel;
    long tenant, user;
    static BigDecimal n(String s){return new BigDecimal(s);}
    @BeforeEach void fixture() throws Exception {
        String url=db.execute((java.sql.Connection c)->c.getMetaData().getURL());
        assertThat(url).contains("retailzim_audit_");
        if (db.queryForObject("select count(*) from users", Long.class) == 0) {
            var initializer=context.getBean(com.retailzw.config.DataInitializer.class);
            var method=Arrays.stream(initializer.getClass().getMethods()).filter(m -> m.getName().equals("seedData")).findFirst().orElseThrow();
            Object[] args=Arrays.stream(method.getParameterTypes()).map(context::getBean).toArray();
            ((CommandLineRunner)method.invoke(initializer,args)).run();
        }
        Map<String,Object> row=db.queryForMap("select id,tenant_id from users where is_active=1 order by id limit 1");
        user=((Number)row.get("id")).longValue();tenant=((Number)row.get("tenant_id")).longValue();
    }
    Branch branch(BusinessModule module){return branches.saveAndFlush(Branch.builder().tenantId(tenant).branchCode("AUD"+UUID.randomUUID().toString().substring(0,8)).name("Audit only").moduleType(module).build());}
    @Test void migrationsProvideEveryMappedColumn() throws Exception {
        List<String> missing=new ArrayList<>();
        var factory=em.getEntityManagerFactory().unwrap(org.hibernate.engine.spi.SessionFactoryImplementor.class);
        factory.getRuntimeMetamodels().getMappingMetamodel().forEachEntityDescriptor(descriptor -> {
            if (!(descriptor instanceof org.hibernate.persister.entity.AbstractEntityPersister entity)) return;
            String table=entity.getTableName().replace("`", "");
            Set<String> actual=new HashSet<>(db.queryForList("select column_name from information_schema.columns where table_schema=database() and table_name=?",String.class,table));
            Set<String> expected=new HashSet<>(Arrays.asList(entity.getIdentifierColumnNames()));
            for(int i=0;i<entity.getPropertyNames().length;i++) expected.addAll(Arrays.asList(entity.getPropertyColumnNames(i)));
            for(String column:expected) if(column!=null&&!actual.contains(column.replace("`", ""))) missing.add(table+"."+column);
        });
        Collections.sort(missing);
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/audit-missing-columns.txt"),String.join("\n",missing));
        assertThat(missing).as("Mapped columns missing from Flyway schema").isEmpty();
    }
    @Test void retailSaleReducesStockAndCashShiftReconciles(){
        long b=branch(BusinessModule.SHOP_MODULE).getId();
        CreateProductRequest product=new CreateProductRequest();product.setBranchId(b);product.setName("Audit rice");product.setSku("AUD"+UUID.randomUUID());product.setCostPriceUsd(n("1"));product.setSellingPriceUsd(n("2"));product.setOpeningStock(n("10"));product.setIsTaxable(false);
        Product p=retail.createProduct(tenant,product,user);
        CashDrawer drawer=retail.createDrawer(tenant,b,"Audit drawer","");
        OpenSessionRequest open=new OpenSessionRequest();open.setDrawerId(drawer.getId());
        CashSession shift=retail.openSession(tenant,b,user,open);
        SaleItemRequest item=new SaleItemRequest();item.setProductId(p.getId());item.setQuantity(n("2"));
        SalePaymentRequest pay=new SalePaymentRequest();pay.setMethod(SalePayment.PaymentMethod.CASH);pay.setCurrency(CurrencyCode.USD);pay.setAmount(n("4"));
        SaleRequest sale=new SaleRequest();sale.setCashSessionId(shift.getId());sale.setCurrency(CurrencyCode.USD);sale.setItems(List.of(item));sale.setPayments(List.of(pay));sale.setOfflineReceiptNumber("AUD"+UUID.randomUUID());
        Sale result=retail.completeSale(tenant,b,user,sale);em.flush();
        assertThat(retail.completeSale(tenant,b,user,sale).getId()).isEqualTo(result.getId());
        assertThat(db.queryForObject("select quantity_on_hand from inventory where branch_id=? and product_id=?",BigDecimal.class,b,p.getId())).isEqualByComparingTo("8");
        CloseSessionRequest close=new CloseSessionRequest();close.setSessionId(shift.getId());close.setActualUsd(n("4"));
        assertThat(retail.closeSession(tenant,b,user,close).getVarianceUsd()).isEqualByComparingTo("0");em.flush();
        CreateReturnRequest refund=new CreateReturnRequest();refund.setOriginalReceiptNumber(result.getReceiptNumber());refund.setReason(com.retailzw.model.Return.ReturnReason.OTHER);refund.setRefundMethod(com.retailzw.model.Return.RefundMethod.CASH);
        var returned=new CreateReturnRequest.ReturnItemRequest();returned.setProductId(p.getId());returned.setQuantity(n("1"));refund.setItems(List.of(returned));returns.processReturn(tenant,b,user,refund);em.flush();
        assertThat(db.queryForObject("select quantity_on_hand from inventory where branch_id=? and product_id=?",BigDecimal.class,b,p.getId())).isEqualByComparingTo("9");
        Supplier supplier=suppliers.saveAndFlush(Supplier.builder().tenantId(tenant).name("Audit supplier").build());
        CreatePurchaseOrderRequest order=new CreatePurchaseOrderRequest();order.setSupplierId(supplier.getId());order.setCurrency(CurrencyCode.USD);
        var line=new CreatePurchaseOrderRequest.PoItemRequest();line.setProductId(p.getId());line.setQuantity(n("3"));line.setUnitCostUsd(n("1"));order.setItems(List.of(line));
        PurchaseOrder po=purchasing.createPurchaseOrder(tenant,b,user,order);purchasing.submitPurchaseOrder(po.getId());purchasing.approvePurchaseOrder(po.getId(),user);
        purchasing.receiveGoods(po.getId(),Map.of(po.getItems().get(0).getId(),n("3")),user);em.flush();
        assertThat(db.queryForObject("select quantity_on_hand from inventory where branch_id=? and product_id=?",BigDecimal.class,b,p.getId())).isEqualByComparingTo("12");
        assertThat(db.queryForObject("select quantity_on_order from inventory where branch_id=? and product_id=?",BigDecimal.class,b,p.getId())).isEqualByComparingTo("0");
    }
    @Test void gasSaleReducesMeasuredStockAndClosesShift(){
        long b=branch(BusinessModule.GAS_MODULE).getId();
        GasTankRequest tank=new GasTankRequest();tank.setBranchId(b);tank.setName("Audit LPG");tank.setProductName("LPG");tank.setTareWeightKg(n("20"));tank.setFullGrossWeightKg(n("120"));tank.setCurrentGrossWeightKg(n("70"));tank.setReorderLevelKg(n("5"));
        GasTank t=gas.createTank(tenant,tank);
        GasPriceRequest price=new GasPriceRequest();price.setBranchId(b);price.setPricePerKg(n("2"));gas.setPrice(tenant,price);
        OpenGasShiftRequest open=new OpenGasShiftRequest();open.setBranchId(b);open.setTankIds(List.of(t.getId()));GasShift shift=gas.openShift(tenant,user,open);
        GasSaleRequest sale=new GasSaleRequest();sale.setBranchId(b);sale.setTankId(t.getId());sale.setQuantityKg(n("5"));sale.setAmountReceived(n("10"));sale.setOfflineReceiptNumber("AUD"+UUID.randomUUID());
        GasSale sold=gas.completeSale(tenant,user,sale);em.flush();
        assertThat(gas.completeSale(tenant,user,sale).getId()).isEqualTo(sold.getId());
        assertThat(db.queryForObject("select current_kg from gas_tanks where id=?",BigDecimal.class,t.getId())).isEqualByComparingTo("45");
        GasTankClosingWeightRequest weight=new GasTankClosingWeightRequest();weight.setTankId(t.getId());weight.setClosingGrossKg(n("65"));
        CloseGasShiftRequest close=new CloseGasShiftRequest();close.setBranchId(b);close.setShiftId(shift.getId());close.setClosingWeights(List.of(weight));gas.closeShift(tenant,user,close);em.flush();
        assertThat(db.queryForObject("select status from gas_shifts where id=?",String.class,shift.getId())).isEqualTo("CLOSED");
    }
    @Test void fuelDeliverySaleRetryAndShiftBalance(){
        long b=branch(BusinessModule.FUEL_MODULE).getId();
        long grade=fuel.addGrade(tenant,"A"+UUID.randomUUID().toString().substring(0,8),"Audit diesel","#123456");
        long tank=fuel.addTank(tenant,null,b,grade,"T1",n("1000"),n("100"),n("20"));
        long pump=fuel.addPump(tenant,null,b,"P1");long nozzle=fuel.addNozzle(tenant,null,b,pump,tank,grade,"N1",n("0"));
        fuel.setPrice(tenant,user,null,b,grade,"USD",n("2"));
        fuel.delivery(tenant,user,null,new FuelStationRequests.Delivery(b,tank,"Audit supplier","AUD"+UUID.randomUUID(),n("100"),n("100"),n("100"),n("200"),n("100"),"USD"));
        long shift=fuel.openShift(tenant,user,null,new FuelStationRequests.OpenShift(b,n("0"),n("0")));
        var sale=new FuelStationRequests.Sale(b,shift,nozzle,n("5"),"USD",List.of(new FuelStationRequests.Payment("CASH","USD",n("10"),null)),"AUD"+UUID.randomUUID(),null,null);
        long id=fuel.sale(tenant,user,null,sale);assertThat(fuel.sale(tenant,user,null,sale)).isEqualTo(id);
        assertThat(db.queryForObject("select book_stock_l from fuel_tanks where id=?",BigDecimal.class,tank)).isEqualByComparingTo("195");
        fuel.closeShift(tenant,user,null,new FuelStationRequests.CloseShift(b,shift,n("10"),n("0"),List.of(new FuelStationRequests.MeterReading(nozzle,n("5"))),List.of(new FuelStationRequests.DipReading(tank,n("195"),n("0"))),"audit"));
        Map<String,Object> row=db.queryForMap("select cash_variance_usd,meter_variance_l,wet_variance_l from fuel_shifts where id=?",shift);
        for(Object value:row.values())assertThat((BigDecimal)value).isEqualByComparingTo("0");
    }
}
