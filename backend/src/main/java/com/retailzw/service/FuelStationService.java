package com.retailzw.service;

import com.retailzw.dto.request.FuelStationRequests.*;
import com.retailzw.enums.BusinessModule;
import com.retailzw.model.Branch;
import com.retailzw.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FuelStationService {
    private static final Set<String> CURRENCIES = Set.of("USD", "ZWG");
    private static final Set<String> PAYMENT_METHODS = Set.of("CASH", "ECOCASH", "ONEMONEY", "INNBUCKS", "CARD", "BANK_TRANSFER");
    private final NamedParameterJdbcTemplate db;
    private final BranchRepository branches;
    private final PackageModuleAccessService moduleAccess;

    @Transactional(readOnly = true)
    public Map<String,Object> dashboard(Long tenantId, Long assignedBranchId, Long selectedBranchId) {
        moduleAccess.requireModule(tenantId, BusinessModule.FUEL_MODULE);
        Long branchId = resolveBranch(tenantId, assignedBranchId, selectedBranchId, true);
        String branchFilter = branchId == null ? "" : " and branch_id=:branch";
        MapSqlParameterSource params = new MapSqlParameterSource("tenant", tenantId);
        if (branchId != null) params.addValue("branch", branchId);
        List<Map<String,Object>> stations = fuelBranches(tenantId, assignedBranchId).stream().map(this::branchRow).toList();
        List<Map<String,Object>> totals = db.query("select currency,coalesce(sum(amount),0) amount,coalesce(sum(litres),0) litres,count(*) sales from fuel_sales where tenant_id=:tenant and voided=b'0' and occurred_at>=CURRENT_DATE" + branchFilter + " group by currency order by currency", params,
                (rs,n)->Map.of("currency",rs.getString("currency"),"amount",rs.getBigDecimal("amount"),"litres",rs.getBigDecimal("litres"),"sales",rs.getLong("sales")));
        List<Map<String,Object>> tanks = db.query("select t.id,t.branch_id,t.code,g.name grade,g.colour,t.safe_capacity_l,t.book_stock_l,t.reorder_level_l,t.latest_dip_l,t.water_level_mm from fuel_tanks t join fuel_grades g on g.id=t.grade_id where t.tenant_id=:tenant and t.active=b'1'" + branchFilter.replace("branch_id","t.branch_id") + " order by t.branch_id,t.code", params,
                (rs,n)->{
                    BigDecimal capacity = rs.getBigDecimal("safe_capacity_l");
                    BigDecimal stock = rs.getBigDecimal("book_stock_l");
                    int fillPercent = capacity == null || capacity.signum() == 0 ? 0 : stock
                            .multiply(BigDecimal.valueOf(100))
                            .divide(capacity, 0, RoundingMode.HALF_UP)
                            .intValue();
                    return Map.ofEntries(Map.entry("id",rs.getLong("id")),Map.entry("branchId",rs.getLong("branch_id")),Map.entry("code",rs.getString("code")),Map.entry("grade",rs.getString("grade")),Map.entry("colour",rs.getString("colour")),Map.entry("capacity",capacity),Map.entry("stock",stock),Map.entry("fillPercent",Math.max(0,Math.min(fillPercent,100))),Map.entry("reorder",rs.getBigDecimal("reorder_level_l")),Map.entry("dip",nvl(rs.getBigDecimal("latest_dip_l"))),Map.entry("water",nvl(rs.getBigDecimal("water_level_mm"))));
                });
        List<Map<String,Object>> nozzles = db.query("select n.id,n.branch_id,n.tank_id,n.grade_id,p.code pump,n.code nozzle,g.name grade,g.colour,n.meter_l,n.status from fuel_nozzles n join fuel_pumps p on p.id=n.pump_id join fuel_grades g on g.id=n.grade_id where n.tenant_id=:tenant and n.active=b'1'" + branchFilter.replace("branch_id","n.branch_id") + " order by n.branch_id,p.code,n.code", params,
                (rs,n)->Map.ofEntries(Map.entry("id",rs.getLong("id")),Map.entry("branchId",rs.getLong("branch_id")),Map.entry("tankId",rs.getLong("tank_id")),Map.entry("gradeId",rs.getLong("grade_id")),Map.entry("pump",rs.getString("pump")),Map.entry("nozzle",rs.getString("nozzle")),Map.entry("grade",rs.getString("grade")),Map.entry("colour",rs.getString("colour")),Map.entry("meter",rs.getBigDecimal("meter_l")),Map.entry("status",rs.getString("status"))));
        Long openShifts = db.queryForObject("select count(*) from fuel_shifts where tenant_id=:tenant and status='OPEN'" + branchFilter, params, Long.class);
        return Map.of("selectedBranchId",branchId == null ? 0L : branchId,"stations",stations,"totals",totals,"tanks",tanks,"nozzles",nozzles,"openShifts",nvl(openShifts));
    }

    @Transactional(readOnly = true)
    public Map<String,Object> posBootstrap(Long tenantId, Long userId, Long assignedBranchId, Long requestedBranchId) {
        Long branchId = resolveBranch(tenantId, assignedBranchId, requestedBranchId, false);
        MapSqlParameterSource p = params("tenant",tenantId,"branch",branchId);
        Map<String,Object> payload = new LinkedHashMap<>(dashboard(tenantId, assignedBranchId, branchId));
        payload.put("prices", db.query("select p.grade_id,g.name grade,p.currency,p.amount_per_l from fuel_prices p join fuel_grades g on g.id=p.grade_id where p.tenant_id=:tenant and p.branch_id=:branch and p.active=b'1' and p.effective_at<=CURRENT_TIMESTAMP order by g.name,p.currency",p,(rs,n)->Map.of("gradeId",rs.getLong("grade_id"),"grade",rs.getString("grade"),"currency",rs.getString("currency"),"amountPerLitre",rs.getBigDecimal("amount_per_l"))));
        payload.put("currentShift", db.query("select id,shift_no,opened_at,opening_usd,opening_zwg from fuel_shifts where tenant_id=:tenant and branch_id=:branch and cashier_id=:user and status='OPEN' order by opened_at desc limit 1",p.addValue("user",userId),(rs,n)->Map.of("id",rs.getLong("id"),"shiftNumber",rs.getString("shift_no"),"openedAt",rs.getTimestamp("opened_at").toLocalDateTime(),"openingUsd",rs.getBigDecimal("opening_usd"),"openingZwg",rs.getBigDecimal("opening_zwg"))).stream().findFirst().orElse(null));
        payload.put("shiftSales", db.query("select id,receipt_no,litres,amount,currency,occurred_at from fuel_sales where tenant_id=:tenant and branch_id=:branch and cashier_id=:user and voided=b'0' and shift_id=(select coalesce(max(id),0) from fuel_shifts where tenant_id=:tenant and branch_id=:branch and cashier_id=:user) order by occurred_at desc limit 100",p,(rs,n)->Map.of("id",rs.getLong("id"),"receiptNumber",rs.getString("receipt_no"),"litres",rs.getBigDecimal("litres"),"amount",rs.getBigDecimal("amount"),"currency",rs.getString("currency"),"occurredAt",rs.getTimestamp("occurred_at").toLocalDateTime())));
        payload.put("paymentMethods", PAYMENT_METHODS);
        return payload;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public long openShift(Long tenantId, Long userId, Long assignedBranchId, OpenShift request) {
        Long branchId=resolveBranch(tenantId,assignedBranchId,request.branchId(),false);
        MapSqlParameterSource p=params("tenant",tenantId,"branch",branchId,"user",userId);
        Long existing=db.queryForObject("select count(*) from fuel_shifts where tenant_id=:tenant and branch_id=:branch and cashier_id=:user and status='OPEN'",p,Long.class);
        if(nvl(existing)>0) throw new IllegalStateException("This cashier already has an open fuel shift.");
        String number="FUEL-"+branchId+"-"+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))+"-"+UUID.randomUUID().toString().substring(0,8);
        long shift=insert("insert into fuel_shifts(tenant_id,branch_id,shift_no,cashier_id,status,opened_at,opening_usd,opening_zwg) values(:tenant,:branch,:number,:user,'OPEN',CURRENT_TIMESTAMP,:usd,:zwg)",p.addValue("number",number).addValue("usd",money(request.openingUsd())).addValue("zwg",money(request.openingZwg())));
        db.update("insert into fuel_shift_nozzle_meters(shift_id,nozzle_id,opening_meter_l) select :shift,id,meter_l from fuel_nozzles where tenant_id=:tenant and branch_id=:branch and active=b'1'",p.addValue("shift",shift));
        db.update("insert into fuel_shift_tank_dips(shift_id,tank_id,opening_dip_l) select :shift,id,coalesce(latest_dip_l,book_stock_l) from fuel_tanks where tenant_id=:tenant and branch_id=:branch and active=b'1'",p);
        audit(tenantId,branchId,userId,"SHIFT_OPENED","SHIFT",shift,number);return shift;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public long sale(Long tenantId, Long userId, Long assignedBranchId, Sale request) {
        Long branchId=resolveBranch(tenantId,assignedBranchId,request.branchId(),false); positive(request.litres(),"Litres");
        MapSqlParameterSource base=params("tenant",tenantId,"branch",branchId,"user",userId);
        List<Long> duplicate=db.query("select id from fuel_sales where tenant_id=:tenant and idempotency_key=:key",base.addValue("key",shortText(request.idempotencyKey(),100)),(rs,n)->rs.getLong(1));
        if(!duplicate.isEmpty()) return duplicate.get(0);
        Map<String,Object> nozzle=db.query("select n.tank_id,n.grade_id,n.meter_l,t.book_stock_l,n.status from fuel_nozzles n join fuel_tanks t on t.id=n.tank_id where n.id=:nozzle and n.tenant_id=:tenant and n.branch_id=:branch and n.active=b'1' for update",base.addValue("nozzle",request.nozzleId()),rs->{if(!rs.next())throw new IllegalArgumentException("Nozzle not found in this fuel branch.");return Map.of("tank",rs.getLong(1),"grade",rs.getLong(2),"meter",rs.getBigDecimal(3),"stock",rs.getBigDecimal(4),"status",rs.getString(5));});
        if("OFFLINE".equals(nozzle.get("status"))) throw new IllegalStateException("This nozzle is offline.");
        if(((BigDecimal)nozzle.get("stock")).compareTo(request.litres())<0) throw new IllegalStateException("Insufficient fuel stock in the connected tank.");
        Long shiftCount=db.queryForObject("select count(*) from fuel_shifts where id=:shift and tenant_id=:tenant and branch_id=:branch and cashier_id=:user and status='OPEN'",base.addValue("shift",request.shiftId()),Long.class);
        if(nvl(shiftCount)!=1) throw new IllegalStateException("Sale requires this cashier's open fuel shift.");
        String currency=currency(request.currency());
        BigDecimal price=db.query("select amount_per_l from fuel_prices where tenant_id=:tenant and branch_id=:branch and grade_id=:grade and currency=:currency and active=b'1' and effective_at<=CURRENT_TIMESTAMP order by effective_at desc,id desc limit 1",base.addValue("grade",nozzle.get("grade")).addValue("currency",currency),(rs,n)->rs.getBigDecimal(1)).stream().findFirst().orElseThrow(()->new IllegalStateException("No approved fuel price exists for this currency."));
        BigDecimal total=price.multiply(request.litres()).setScale(2,RoundingMode.HALF_UP);
        BigDecimal paid=request.payments().stream().peek(x->{if(!PAYMENT_METHODS.contains(x.method().toUpperCase(Locale.ROOT)))throw new IllegalArgumentException("Unsupported payment method.");if(!currency.equals(currency(x.currency())))throw new IllegalArgumentException("Cross-currency tender requires an explicit exchange transaction.");}).map(Payment::amount).reduce(BigDecimal.ZERO,BigDecimal::add).setScale(2,RoundingMode.HALF_UP);
        if(paid.compareTo(total)!=0) throw new IllegalArgumentException("Payments must equal the sale total of "+currency+" "+total+".");
        String receipt="FS-"+branchId+"-"+UUID.randomUUID();
        long sale=insert("insert into fuel_sales(tenant_id,branch_id,shift_id,nozzle_id,receipt_no,cashier_id,litres,unit_price,amount,currency,payment_status,fiscal_status,vehicle_reg,customer_ref,idempotency_key) values(:tenant,:branch,:shift,:nozzle,:receipt,:user,:litres,:price,:amount,:currency,'PAID','QUEUED',:vehicle,:customer,:key)",base.addValue("receipt",receipt).addValue("litres",request.litres()).addValue("price",price).addValue("amount",total).addValue("vehicle",blank(request.vehicleRegistration())).addValue("customer",blank(request.customerReference())));
        for(Payment payment:request.payments()) db.update("insert into fuel_sale_payments(sale_id,method,currency,amount,reference) values(:sale,:method,:currency,:amount,:reference)",params("sale",sale,"method",payment.method().toUpperCase(Locale.ROOT),"currency",currency,"amount",money(payment.amount()),"reference",blank(payment.reference())));
        db.update("update fuel_nozzles set meter_l=meter_l+:litres,version=version+1 where id=:nozzle",base);
        db.update("update fuel_tanks set book_stock_l=book_stock_l-:litres,version=version+1 where id=:tank",base.addValue("tank",nozzle.get("tank")));
        movement(tenantId,branchId,(Long)nozzle.get("tank"),"SALE",request.litres().negate(),"SALE",sale);audit(tenantId,branchId,userId,"FUEL_SALE_COMPLETED","SALE",sale,receipt);return sale;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public long delivery(Long tenantId, Long userId, Long assignedBranchId, Delivery request) {
        Long branchId=resolveBranch(tenantId,assignedBranchId,request.branchId(),false);positive(request.receivedLitres(),"Received litres");
        MapSqlParameterSource p=params("tenant",tenantId,"branch",branchId,"tank",request.tankId());
        Map<String,BigDecimal> tank=db.query("select safe_capacity_l,book_stock_l,average_cost_per_l from fuel_tanks where id=:tank and tenant_id=:tenant and branch_id=:branch and active=b'1' for update",p,rs->{if(!rs.next())throw new IllegalArgumentException("Fuel tank not found.");return Map.of("capacity",rs.getBigDecimal(1),"stock",rs.getBigDecimal(2),"cost",rs.getBigDecimal(3));});
        BigDecimal next=tank.get("stock").add(request.receivedLitres());if(next.compareTo(tank.get("capacity"))>0)throw new IllegalStateException("Delivery exceeds the tank safe capacity.");
        positive(request.invoiceLitres(),"Invoice litres");nonNegative(request.beforeDipLitres(),"Before dip");nonNegative(request.afterDipLitres(),"After dip");
        if(request.afterDipLitres().compareTo(tank.get("capacity"))>0 || request.beforeDipLitres().compareTo(tank.get("capacity"))>0) throw new IllegalArgumentException("Dip exceeds tank capacity.");
        BigDecimal variance=request.afterDipLitres().subtract(request.beforeDipLitres()).subtract(request.receivedLitres());
        try { long id=insert("insert into fuel_deliveries(tenant_id,branch_id,tank_id,supplier,delivery_note,invoice_l,received_l,before_dip_l,after_dip_l,variance_l,cost_amount,currency,received_by) values(:tenant,:branch,:tank,:supplier,:note,:invoice,:received,:before,:after,:variance,:cost,:currency,:user)",p.addValue("supplier",shortText(request.supplier(),160)).addValue("note",shortText(request.deliveryNote(),100)).addValue("invoice",request.invoiceLitres()).addValue("received",request.receivedLitres()).addValue("before",request.beforeDipLitres()).addValue("after",request.afterDipLitres()).addValue("variance",variance).addValue("cost",money(request.costAmount())).addValue("currency",currency(request.currency())).addValue("user",userId));
            BigDecimal unitCost=request.costAmount().divide(request.receivedLitres(),4,RoundingMode.HALF_UP);BigDecimal weighted=tank.get("stock").multiply(tank.get("cost")).add(request.receivedLitres().multiply(unitCost)).divide(next,4,RoundingMode.HALF_UP);
            db.update("update fuel_tanks set book_stock_l=:next,latest_dip_l=:after,average_cost_per_l=:average,version=version+1 where id=:tank",p.addValue("next",next).addValue("average",weighted));movement(tenantId,branchId,request.tankId(),"DELIVERY",request.receivedLitres(),"DELIVERY",id);audit(tenantId,branchId,userId,"FUEL_RECEIVED","DELIVERY",id,request.deliveryNote());return id;
        } catch(DuplicateKeyException ex){throw new IllegalArgumentException("This delivery note has already been received.");}
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void closeShift(Long tenantId, Long userId, Long assignedBranchId, CloseShift request) {
        Long branchId=resolveBranch(tenantId,assignedBranchId,request.branchId(),false);MapSqlParameterSource p=params("tenant",tenantId,"branch",branchId,"user",userId,"shift",request.shiftId());
        Map<String,BigDecimal> shift=db.query("select opening_usd,opening_zwg from fuel_shifts where id=:shift and tenant_id=:tenant and branch_id=:branch and cashier_id=:user and status='OPEN' for update",p,rs->{if(!rs.next())throw new IllegalArgumentException("Open fuel shift not found.");return Map.of("usd",rs.getBigDecimal(1),"zwg",rs.getBigDecimal(2));});
        Long nozzleCount=db.queryForObject("select count(*) from fuel_shift_nozzle_meters where shift_id=:shift",p,Long.class);if(request.meters().size()!=nvl(nozzleCount)||request.meters().stream().map(MeterReading::nozzleId).distinct().count()!=request.meters().size())throw new IllegalArgumentException("One closing reading is required for every nozzle.");
        Long tankCount=db.queryForObject("select count(*) from fuel_shift_tank_dips where shift_id=:shift",p,Long.class);if(request.dips().size()!=nvl(tankCount)||request.dips().stream().map(DipReading::tankId).distinct().count()!=request.dips().size())throw new IllegalArgumentException("One closing dip is required for every tank.");
        BigDecimal meterTotal=BigDecimal.ZERO;for(MeterReading r:request.meters()){MapSqlParameterSource q=params("shift",request.shiftId(),"id",r.nozzleId(),"meter",r.meterLitres());BigDecimal opening=db.queryForObject("select opening_meter_l from fuel_shift_nozzle_meters where shift_id=:shift and nozzle_id=:id",q,BigDecimal.class);if(r.meterLitres().compareTo(opening)<0)throw new IllegalArgumentException("Closing meter cannot be below its opening reading.");meterTotal=meterTotal.add(r.meterLitres().subtract(opening));db.update("update fuel_shift_nozzle_meters set closing_meter_l=:meter where shift_id=:shift and nozzle_id=:id",q);}
        BigDecimal closingDip=BigDecimal.ZERO,openingDip=BigDecimal.ZERO;for(DipReading r:request.dips()){MapSqlParameterSource q=params("shift",request.shiftId(),"id",r.tankId(),"dip",r.dipLitres(),"water",r.waterLevelMm());BigDecimal opening=db.queryForObject("select opening_dip_l from fuel_shift_tank_dips where shift_id=:shift and tank_id=:id",q,BigDecimal.class);openingDip=openingDip.add(opening);closingDip=closingDip.add(r.dipLitres());db.update("update fuel_shift_tank_dips set closing_dip_l=:dip where shift_id=:shift and tank_id=:id",q);db.update("update fuel_tanks set latest_dip_l=:dip,water_level_mm=:water,version=version+1 where id=:id",q);}
        Map<String,BigDecimal> sales=db.query("select coalesce(sum(case when currency='USD' then amount else 0 end),0),coalesce(sum(case when currency='ZWG' then amount else 0 end),0),coalesce(sum(litres),0) from fuel_sales where shift_id=:shift and voided=b'0'",p,rs->{rs.next();return Map.of("usd",rs.getBigDecimal(1),"zwg",rs.getBigDecimal(2),"litres",rs.getBigDecimal(3));});
        var cash=db.queryForMap("select coalesce(sum(case when p.currency='USD' then p.amount else 0 end),0) usd,coalesce(sum(case when p.currency='ZWG' then p.amount else 0 end),0) zwg from fuel_sale_payments p join fuel_sales s on s.id=p.sale_id where s.shift_id=:shift and s.tenant_id=:tenant and s.voided=0 and p.method='CASH'",p);
        BigDecimal delivered=db.queryForObject("select coalesce(sum(d.received_l),0) from fuel_deliveries d join fuel_shifts s on s.id=:shift where d.tenant_id=:tenant and d.branch_id=:branch and d.received_at>=s.opened_at",p,BigDecimal.class);
        // Physical meters and tank dips cover the whole station, including concurrent cashiers.
        BigDecimal stationLitres=db.queryForObject("select coalesce(sum(f.litres),0) from fuel_sales f join fuel_shifts s on s.id=:shift where f.tenant_id=:tenant and f.branch_id=:branch and f.voided=0 and f.occurred_at>=s.opened_at",p,BigDecimal.class);
        BigDecimal expectedUsd=shift.get("usd").add((BigDecimal)cash.get("usd")),expectedZwg=shift.get("zwg").add((BigDecimal)cash.get("zwg"));BigDecimal wetVariance=openingDip.add(delivered).subtract(closingDip).subtract(stationLitres);BigDecimal meterVariance=meterTotal.subtract(stationLitres);
        db.update("update fuel_shifts set status='CLOSED',closed_at=CURRENT_TIMESTAMP,counted_usd=:countedUsd,counted_zwg=:countedZwg,expected_usd=:expectedUsd,expected_zwg=:expectedZwg,cash_variance_usd=:usdVariance,cash_variance_zwg=:zwgVariance,meter_variance_l=:meterVariance,wet_variance_l=:wetVariance,close_note=:note,version=version+1 where id=:shift",p.addValue("countedUsd",money(request.countedUsd())).addValue("countedZwg",money(request.countedZwg())).addValue("expectedUsd",expectedUsd).addValue("expectedZwg",expectedZwg).addValue("usdVariance",request.countedUsd().subtract(expectedUsd)).addValue("zwgVariance",request.countedZwg().subtract(expectedZwg)).addValue("meterVariance",meterVariance).addValue("wetVariance",wetVariance).addValue("note",blank(request.note())));audit(tenantId,branchId,userId,"SHIFT_CLOSED","SHIFT",request.shiftId(),"meterVariance="+meterVariance+", wetVariance="+wetVariance);
    }

    @Transactional public long addGrade(Long tenantId,String code,String name,String colour){moduleAccess.requireModule(tenantId,BusinessModule.FUEL_MODULE);if(colour==null||!colour.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Select a valid fuel colour.");return insert("insert into fuel_grades(tenant_id,code,name,colour) values(:tenant,:code,:name,:colour)",params("tenant",tenantId,"code",shortText(code,30).toUpperCase(Locale.ROOT),"name",shortText(name,100),"colour",shortText(colour,20)));}
    @Transactional public long addTank(Long tenantId,Long assignedBranchId,Long branchId,Long gradeId,String code,BigDecimal capacity,BigDecimal stock,BigDecimal reorder){Long b=resolveBranch(tenantId,assignedBranchId,branchId,false);requireGrade(tenantId,gradeId);positive(capacity,"Capacity");nonNegative(stock,"Opening stock");nonNegative(reorder,"Reorder level");if(reorder.compareTo(capacity)>0)throw new IllegalArgumentException("Reorder level exceeds capacity.");if(stock.compareTo(capacity)>0)throw new IllegalArgumentException("Opening stock cannot exceed safe capacity.");return insert("insert into fuel_tanks(tenant_id,branch_id,grade_id,code,safe_capacity_l,book_stock_l,reorder_level_l,latest_dip_l) values(:tenant,:branch,:grade,:code,:capacity,:stock,:reorder,:stock)",params("tenant",tenantId,"branch",b,"grade",gradeId,"code",shortText(code,30),"capacity",positive(capacity,"Capacity"),"stock",nonNegative(stock,"Opening stock"),"reorder",nonNegative(reorder,"Reorder level")));}
    @Transactional public long addPump(Long tenantId,Long assignedBranchId,Long branchId,String code){Long b=resolveBranch(tenantId,assignedBranchId,branchId,false);return insert("insert into fuel_pumps(tenant_id,branch_id,code) values(:tenant,:branch,:code)",params("tenant",tenantId,"branch",b,"code",shortText(code,30)));}
    @Transactional public long addNozzle(Long tenantId,Long assignedBranchId,Long branchId,Long pumpId,Long tankId,Long gradeId,String code,BigDecimal meter){Long b=resolveBranch(tenantId,assignedBranchId,branchId,false);return insert("insert into fuel_nozzles(tenant_id,branch_id,pump_id,tank_id,grade_id,code,meter_l) select :tenant,:branch,:pump,:tank,:grade,:code,:meter where exists(select 1 from fuel_pumps where id=:pump and tenant_id=:tenant and branch_id=:branch) and exists(select 1 from fuel_tanks where id=:tank and tenant_id=:tenant and branch_id=:branch and grade_id=:grade)",params("tenant",tenantId,"branch",b,"pump",pumpId,"tank",tankId,"grade",gradeId,"code",shortText(code,30),"meter",nonNegative(meter,"Opening meter")));}
    @Transactional public long setPrice(Long tenantId,Long userId,Long assignedBranchId,Long branchId,Long gradeId,String currency,BigDecimal amount){Long b=resolveBranch(tenantId,assignedBranchId,branchId,false);requireGrade(tenantId,gradeId);MapSqlParameterSource p=params("tenant",tenantId,"branch",b,"grade",gradeId,"currency",currency(currency),"amount",positive(amount,"Price"),"user",userId);db.update("update fuel_prices set active=b'0' where tenant_id=:tenant and branch_id=:branch and grade_id=:grade and currency=:currency and active=b'1'",p);return insert("insert into fuel_prices(tenant_id,branch_id,grade_id,currency,amount_per_l,effective_at,approved_by) values(:tenant,:branch,:grade,:currency,:amount,CURRENT_TIMESTAMP,:user)",p);}
    public List<Map<String,Object>> grades(Long tenantId){moduleAccess.requireModule(tenantId,BusinessModule.FUEL_MODULE);return db.query("select id,code,name,colour from fuel_grades where tenant_id=:tenant and active=b'1' order by name",params("tenant",tenantId),(rs,n)->Map.of("id",rs.getLong("id"),"code",rs.getString("code"),"name",rs.getString("name"),"colour",rs.getString("colour")));}
    public List<Map<String,Object>> pumps(Long tenantId,Long assignedBranchId,Long branchId){Long b=resolveBranch(tenantId,assignedBranchId,branchId,false);return db.query("select id,code,status,controller_ref from fuel_pumps where tenant_id=:tenant and branch_id=:branch and active=b'1' order by code",params("tenant",tenantId,"branch",b),(rs,n)->{Map<String,Object> row=new LinkedHashMap<>();row.put("id",rs.getLong("id"));row.put("code",rs.getString("code"));row.put("status",rs.getString("status"));row.put("controllerRef",rs.getString("controller_ref"));return row;});}
    public List<Map<String,Object>> recentDeliveries(Long tenantId,Long assignedBranchId,Long branchId){Long b=resolveBranch(tenantId,assignedBranchId,branchId,false);return db.query("select d.id,d.delivery_note,d.supplier,t.code tank,d.received_l,d.variance_l,d.cost_amount,d.currency,d.received_at from fuel_deliveries d join fuel_tanks t on t.id=d.tank_id where d.tenant_id=:tenant and d.branch_id=:branch order by d.received_at desc limit 100",params("tenant",tenantId,"branch",b),(rs,n)->Map.of("id",rs.getLong("id"),"note",rs.getString("delivery_note"),"supplier",rs.getString("supplier"),"tank",rs.getString("tank"),"litres",rs.getBigDecimal("received_l"),"variance",rs.getBigDecimal("variance_l"),"cost",rs.getBigDecimal("cost_amount"),"currency",rs.getString("currency"),"at",rs.getTimestamp("received_at").toLocalDateTime()));}
    public List<Map<String,Object>> recentShifts(Long tenantId,Long assignedBranchId,Long branchId){Long b=resolveBranch(tenantId,assignedBranchId,branchId,false);return db.query("select id,shift_no,status,opened_at,closed_at,expected_usd,expected_zwg,cash_variance_usd,cash_variance_zwg,meter_variance_l,wet_variance_l from fuel_shifts where tenant_id=:tenant and branch_id=:branch order by opened_at desc limit 100",params("tenant",tenantId,"branch",b),(rs,n)->{Map<String,Object> row=new LinkedHashMap<>();row.put("id",rs.getLong("id"));row.put("number",rs.getString("shift_no"));row.put("status",rs.getString("status"));row.put("openedAt",rs.getTimestamp("opened_at").toLocalDateTime());row.put("closedAt",rs.getTimestamp("closed_at") == null ? null : rs.getTimestamp("closed_at").toLocalDateTime());row.put("expectedUsd",nvl(rs.getBigDecimal("expected_usd")));row.put("expectedZwg",nvl(rs.getBigDecimal("expected_zwg")));row.put("cashVarianceUsd",nvl(rs.getBigDecimal("cash_variance_usd")));row.put("cashVarianceZwg",nvl(rs.getBigDecimal("cash_variance_zwg")));row.put("meterVariance",nvl(rs.getBigDecimal("meter_variance_l")));row.put("wetVariance",nvl(rs.getBigDecimal("wet_variance_l")));return row;});}
    public List<Map<String,Object>> recentSales(Long tenantId,Long assignedBranchId,Long branchId){Long b=resolveBranch(tenantId,assignedBranchId,branchId,false);return db.query("select s.id,s.receipt_no,p.code pump,n.code nozzle,s.litres,s.amount,s.currency,s.fiscal_status,s.occurred_at from fuel_sales s join fuel_nozzles n on n.id=s.nozzle_id join fuel_pumps p on p.id=n.pump_id where s.tenant_id=:tenant and s.branch_id=:branch and s.voided=b'0' order by s.occurred_at desc limit 100",params("tenant",tenantId,"branch",b),(rs,n)->Map.of("id",rs.getLong("id"),"receipt",rs.getString("receipt_no"),"pump",rs.getString("pump"),"nozzle",rs.getString("nozzle"),"litres",rs.getBigDecimal("litres"),"amount",rs.getBigDecimal("amount"),"currency",rs.getString("currency"),"fiscalStatus",rs.getString("fiscal_status"),"at",rs.getTimestamp("occurred_at").toLocalDateTime()));}
    public List<Branch> fuelBranches(Long tenantId,Long assignedBranchId){moduleAccess.requireModule(tenantId,BusinessModule.FUEL_MODULE);return branches.findByTenantIdAndIsActiveTrue(tenantId).stream().filter(b->BusinessModule.FUEL_MODULE.equals(b.getModuleType())).filter(b->assignedBranchId==null||assignedBranchId.equals(b.getId())).toList();}

    @Transactional(readOnly = true)
    public List<Map<String,Object>> records(Long tenant, Long assigned, Long branch, String section, int page) {
        Long selected = resolveBranch(tenant, assigned, branch, true);
        String select = switch(section) {
            case "expenses" -> "select b.name Branch,r.category Category,r.description Description,r.currency Currency,r.amount Amount,r.reference Reference,r.incurred_on Date from fuel_expenses r";
            case "pricing" -> "select b.name Branch,g.name Fuel,r.currency Currency,r.amount_per_l Price,r.effective_at Effective,case when r.active=1 then 'Active' else 'Superseded' end Status from fuel_prices r join fuel_grades g on g.id=r.grade_id";
            case "deliveries" -> "select b.name Branch,r.delivery_note Reference,r.supplier Supplier,t.code Tank,r.received_l Litres,r.variance_l Variance,r.currency Currency,r.cost_amount Cost,r.received_at Received from fuel_deliveries r join fuel_tanks t on t.id=r.tank_id";
            case "shifts" -> "select b.name Branch,r.shift_no Shift,concat(u.first_name,' ',u.last_name) Cashier,r.status Status,r.opened_at Opened,r.closed_at Closed,r.cash_variance_usd USD_variance,r.cash_variance_zwg ZWG_variance,r.meter_variance_l Meter_variance,r.wet_variance_l Stock_variance from fuel_shifts r join users u on u.id=r.cashier_id and u.tenant_id=r.tenant_id";
            case "stock" -> "select b.name Branch,t.code Tank,r.movement_type Movement,r.litres Litres,r.reference_type Source,r.occurred_at Recorded from fuel_stock_movements r join fuel_tanks t on t.id=r.tank_id";
            default -> "select b.name Branch,r.receipt_no Receipt,concat(u.first_name,' ',u.last_name) Cashier,p.code Pump,n.code Nozzle,r.litres Litres,r.currency Currency,r.amount Total,r.fiscal_status Fiscal,r.occurred_at Recorded from fuel_sales r join users u on u.id=r.cashier_id and u.tenant_id=r.tenant_id join fuel_nozzles n on n.id=r.nozzle_id join fuel_pumps p on p.id=n.pump_id";
        };
        return db.queryForList(select + " join branches b on b.id=r.branch_id and b.tenant_id=r.tenant_id where r.tenant_id=:tenant" + (selected == null ? "" : " and r.branch_id=:branch") + " order by r.id desc limit 51 offset :offset",
                params("tenant",tenant,"branch",selected,"offset",Math.min(Math.max(page,0),100000)*50));
    }

    @Transactional
    public void recordDip(Long tenant, Long user, Long assigned, Long branch, Long tank, BigDecimal litres, BigDecimal water) {
        Long selected=resolveBranch(tenant,assigned,branch,false);
        nonNegative(litres,"Dip litres"); nonNegative(water,"Water level");
        var p=params("tenant",tenant,"branch",selected,"tank",tank,"litres",litres,"water",water);
        var capacities=db.query("select safe_capacity_l from fuel_tanks where id=:tank and tenant_id=:tenant and branch_id=:branch and active=1 for update",p,(rs,n)->rs.getBigDecimal(1));
        if(capacities.isEmpty() || litres.compareTo(capacities.get(0))>0) throw new IllegalArgumentException("Select a tank and enter a dip within its safe capacity.");
        db.update("update fuel_tanks set latest_dip_l=:litres,water_level_mm=:water,version=version+1 where id=:tank and tenant_id=:tenant and branch_id=:branch",p);
        audit(tenant,selected,user,"TANK_DIP_RECORDED","TANK",tank,"Dip="+litres+" L, water="+water+" mm");
    }

    @Transactional
    public void expense(Long tenant,Long user,Long assigned,Long branch,String category,String description,BigDecimal amount,String currency,String reference,java.time.LocalDate date) {
        Long selected=resolveBranch(tenant,assigned,branch,false);
        if(date==null||date.isAfter(java.time.LocalDate.now()))throw new IllegalArgumentException("Select an expense date no later than today.");
        long id=insert("insert into fuel_expenses(tenant_id,branch_id,category,description,amount,currency,reference,incurred_on,created_by) values(:tenant,:branch,:category,:description,:amount,:currency,:reference,:date,:user)",params("tenant",tenant,"branch",selected,"category",shortText(category,80),"description",shortText(description,500),"amount",positive(amount,"Expense amount").setScale(2,RoundingMode.HALF_UP),"currency",currency(currency),"reference",shortText(reference,100),"date",date,"user",user));
        audit(tenant,selected,user,"EXPENSE_RECORDED","EXPENSE",id,reference);
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> financialSummary(Long tenant,Long assigned,Long branch) {
        Long selected=resolveBranch(tenant,assigned,branch,true);
        return db.queryForList("select currency,sum(revenue) Revenue,sum(deliveries) Fuel_purchases,sum(expenses) Expenses from (select currency,sum(amount) revenue,0 deliveries,0 expenses from fuel_sales where tenant_id=:tenant and (:branch is null or branch_id=:branch) and voided=0 and occurred_at>=DATE_FORMAT(CURRENT_DATE,'%Y-%m-01') group by currency union all select currency,0,sum(cost_amount),0 from fuel_deliveries where tenant_id=:tenant and (:branch is null or branch_id=:branch) and received_at>=DATE_FORMAT(CURRENT_DATE,'%Y-%m-01') group by currency union all select currency,0,0,sum(amount) from fuel_expenses where tenant_id=:tenant and (:branch is null or branch_id=:branch) and incurred_on>=DATE_FORMAT(CURRENT_DATE,'%Y-%m-01') group by currency) x group by currency",params("tenant",tenant,"branch",selected));
    }

    @Transactional
    public void nozzleStatus(Long tenant, Long user, Long assigned, Long branch, Long nozzle, String status) {
        Long selected=resolveBranch(tenant,assigned,branch,false);
        if(!Set.of("IDLE","OFFLINE").contains(status)) throw new IllegalArgumentException("Select an available nozzle status.");
        int updated=db.update("update fuel_nozzles set status=:status,version=version+1 where id=:id and tenant_id=:tenant and branch_id=:branch and active=1",params("status",status,"id",nozzle,"tenant",tenant,"branch",selected));
        if(updated!=1) throw new IllegalArgumentException("Nozzle is not available in this branch.");
        audit(tenant,selected,user,"NOZZLE_STATUS_CHANGED","NOZZLE",nozzle,status);
    }

    private Long resolveBranch(Long tenantId,Long assigned,Long requested,boolean allAllowed){List<Branch> allowed=fuelBranches(tenantId,assigned);if(requested==null||requested==0){if(assigned!=null)return allowed.stream().filter(b->assigned.equals(b.getId())).findFirst().orElseThrow(()->new IllegalArgumentException("Assigned fuel branch is unavailable.")).getId();if(allAllowed)return null;if(allowed.size()==1)return allowed.get(0).getId();throw new IllegalArgumentException("Select a fuel-station branch.");}return allowed.stream().filter(b->b.getId().equals(requested)).findFirst().orElseThrow(()->new IllegalArgumentException("Fuel branch is not available to this user.")).getId();}
    private void requireGrade(Long tenant,Long grade){Long count=db.queryForObject("select count(*) from fuel_grades where id=:grade and tenant_id=:tenant and active=1 for update",params("tenant",tenant,"grade",grade),Long.class);if(nvl(count)!=1)throw new IllegalArgumentException("Fuel grade is not available to this shop.");}
    private Map<String,Object> branchRow(Branch b){return Map.of("id",b.getId(),"code",b.getBranchCode(),"name",b.getName(),"city",Objects.toString(b.getCity(),""));}
    private long insert(String sql,MapSqlParameterSource params){KeyHolder keys=new GeneratedKeyHolder();int changed=db.update(sql,params,keys,new String[]{"id"});if(changed!=1||keys.getKey()==null)throw new IllegalStateException("Fuel operation did not create a record.");return keys.getKey().longValue();}
    private void movement(Long tenant,Long branch,Long tank,String type,BigDecimal litres,String reference,long id){db.update("insert into fuel_stock_movements(tenant_id,branch_id,tank_id,movement_type,litres,reference_type,reference_id) values(:tenant,:branch,:tank,:type,:litres,:reference,:id)",params("tenant",tenant,"branch",branch,"tank",tank,"type",type,"litres",litres,"reference",reference,"id",id));}
    private void audit(Long tenant,Long branch,Long user,String action,String type,long id,String details){db.update("insert into fuel_audit_events(tenant_id,branch_id,user_id,action,entity_type,entity_id,details) values(:tenant,:branch,:user,:action,:type,:id,:details)",params("tenant",tenant,"branch",branch,"user",user,"action",action,"type",type,"id",id,"details",details));}
    private MapSqlParameterSource params(Object... values){MapSqlParameterSource p=new MapSqlParameterSource();for(int i=0;i<values.length;i+=2)p.addValue(String.valueOf(values[i]),values[i+1]);return p;}
    private String currency(String value){String result=shortText(value,5).toUpperCase(Locale.ROOT);if(!CURRENCIES.contains(result))throw new IllegalArgumentException("Currency must be USD or ZWG.");return result;}
    private String shortText(String value,int max){if(value==null||value.trim().isEmpty())throw new IllegalArgumentException("A required value is missing.");String clean=value.trim();if(clean.length()>max)throw new IllegalArgumentException("Value is too long.");return clean;}
    private String blank(String value){return value==null||value.trim().isEmpty()?null:value.trim();}
    private BigDecimal positive(BigDecimal value,String label){if(value==null||value.signum()<=0)throw new IllegalArgumentException(label+" must be greater than zero.");return value;}
    private BigDecimal nonNegative(BigDecimal value,String label){if(value==null||value.signum()<0)throw new IllegalArgumentException(label+" cannot be negative.");return value;}
    private BigDecimal money(BigDecimal value){return nonNegative(value,"Amount").setScale(2,RoundingMode.HALF_UP);}
    private BigDecimal nvl(BigDecimal value){return value==null?BigDecimal.ZERO:value;}
    private long nvl(Long value){return value==null?0:value;}
}
