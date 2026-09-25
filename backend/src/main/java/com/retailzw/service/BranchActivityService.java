package com.retailzw.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Tenant-scoped, bounded scalar projections for the read-only all-branches workspace. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BranchActivityService {
    private final EntityManager em;
    private record View(String title, String source, String branch, String fields, String search, List<String> headings) {}
    private static View view(String title,String entity,String fields,String search,String... headings) {
        return new View(title,entity+" x","x.branchId",fields,search,List.of(headings));
    }
    private static final View STOCK = new View("Products & inventory", "Inventory x join Product p on p.id=x.productId and p.tenantId=x.tenantId",
            "x.branchId", "p.name,p.sku,x.quantityOnHand,p.sellingPriceUsd", "p.name", List.of("Product","SKU","Stock","Price USD"));
    private static final View SALES = view("Sales","Sale","x.receiptNumber,x.createdAt,x.currency,x.grandTotal,x.status","x.receiptNumber","Receipt","Date","Currency","Total","Status");
    private static final View GAS = view("Gas sales","GasSale","x.receiptNumber,x.createdAt,x.quantityKg,x.currency,x.total,x.status","x.receiptNumber","Receipt","Date","Quantity kg","Currency","Total","Status");
    private static final Map<String,View> VIEWS = Map.ofEntries(
        Map.entry("products",STOCK),Map.entry("inventory",STOCK),Map.entry("sales",SALES),Map.entry("reports",SALES),
        Map.entry("cash",view("Cash sessions","CashSession","x.openedAt,x.status,x.totalSalesUsd,x.totalSalesZwg","b.name","Opened","Status","Sales USD","Sales ZWG")),
        Map.entry("returns",view("Returns","Return","x.returnNumber,x.originalReceiptNumber,x.currency,x.totalRefund","x.returnNumber","Return","Original receipt","Currency","Refund")),
        Map.entry("change",view("Held change","HeldChange","x.referenceNumber,x.customerName,x.currency,x.amount,x.status","x.customerName","Reference","Customer","Currency","Amount","Status")),
        Map.entry("purchasing",view("Purchase orders","PurchaseOrder","x.poNumber,x.createdAt,x.status,x.totalUsd,x.totalZwg","x.poNumber","Order","Created","Status","Total USD","Total ZWG")),
        Map.entry("inventory-intelligence",new View("Stock transfers","StockTransfer x join Branch target on target.id=x.toBranchId and target.tenantId=x.tenantId","x.fromBranchId","x.transferNumber,target.name,x.initiatedAt,x.status","x.transferNumber",List.of("Transfer","Destination","Initiated","Status"))),
        Map.entry("gas",GAS),Map.entry("gas-sales",GAS),Map.entry("gas-accounting",GAS),
        Map.entry("gas-change",view("Gas held change","HeldChange","x.referenceNumber,x.customerName,x.currency,x.amount,x.status","x.customerName","Reference","Customer","Currency","Amount","Status")),
        Map.entry("gas-restocking",view("Gas restocking","GasRestock","x.createdAt,x.supplierName,x.quantityKg,x.currency,x.totalCost","x.supplierName","Date","Supplier","Quantity kg","Currency","Cost")),
        Map.entry("gas-expenses",view("Gas expenses","GasExpense","x.createdAt,x.category,x.description,x.currency,x.amount","x.description","Date","Category","Description","Currency","Amount")),
        Map.entry("gas-tanks",view("Gas tanks","GasTank","x.name,x.productName,x.currentKg,x.status","x.name","Tank","Product","Available kg","Status"))
    );
    public boolean supports(String module) { return VIEWS.containsKey(module); }
    public void page(Long tenant,String module,String search,int page,int size,Model model) {
        View v=VIEWS.get(module);
        if(v==null) throw new IllegalArgumentException("Unsupported branch activity page.");
        int limit=Math.max(1,Math.min(size,100)), index=Math.max(0,Math.min(page,100000));
        String term=search==null ? "" : search.strip().toLowerCase(java.util.Locale.ROOT);
        if(term.length()>100) term=term.substring(0,100);
        String from=" from "+v.source()+" join Branch b on b.id="+v.branch()+" and b.tenantId=x.tenantId where x.tenantId=:tenant";
        if("gas-change".equals(module)) from+=" and x.gasSaleId is not null";
        if(!term.isEmpty()) from+=" and lower("+v.search()+") like :search";
        var rows=em.createQuery("select b.name,"+v.fields()+from+" order by x.id desc",Object[].class).setParameter("tenant",tenant);
        var count=em.createQuery("select count(x)"+from,Long.class).setParameter("tenant",tenant);
        if(!term.isEmpty()) { rows.setParameter("search","%"+term+"%"); count.setParameter("search","%"+term+"%"); }
        long total=count.getSingleResult();
        model.addAttribute("activityRows",rows.setFirstResult(index*limit).setMaxResults(limit).getResultList());
        model.addAttribute("activityHeadings",v.headings()); model.addAttribute("pageTitle",v.title());
        model.addAttribute("activityTotal",total);model.addAttribute("activityPage",index);model.addAttribute("activitySize",limit);
        model.addAttribute("activityHasNext",(long)(index+1)*limit<total);model.addAttribute("activitySearch",term);
    }
    public void dashboard(Long tenant,Model model) {
        var start=LocalDate.now().atStartOfDay();
        model.addAttribute("allBranchCount",em.createQuery("select count(b) from Branch b where b.tenantId=:tenant and b.isActive=true",Long.class).setParameter("tenant",tenant).getSingleResult());
        model.addAttribute("allProductCount",em.createQuery("select count(p) from Product p where p.tenantId=:tenant and p.isActive=true",Long.class).setParameter("tenant",tenant).getSingleResult());
        model.addAttribute("allTodayPayments",em.createQuery("select p.currency,sum(p.amount) from SalePayment p join p.sale s where s.tenantId=:tenant and s.status='COMPLETED' and s.createdAt>=:start and s.createdAt<:end group by p.currency",Object[].class).setParameter("tenant",tenant).setParameter("start",start).setParameter("end",start.plusDays(1)).getResultList());
        model.addAttribute("allOpenSessions",em.createQuery("select count(s) from CashSession s where s.tenantId=:tenant and s.status='OPEN'",Long.class).setParameter("tenant",tenant).getSingleResult());
        page(tenant,"sales",null,0,10,model);
    }
}
