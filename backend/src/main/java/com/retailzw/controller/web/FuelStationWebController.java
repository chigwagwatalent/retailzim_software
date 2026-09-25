package com.retailzw.controller.web;

import com.retailzw.dto.request.FuelStationRequests.Delivery;
import com.retailzw.enums.BusinessModule;
import com.retailzw.model.Branch;
import com.retailzw.model.SaasPlan;
import com.retailzw.repository.BranchRepository;
import com.retailzw.repository.SaasPlanRepository;
import com.retailzw.repository.TenantRepository;
import com.retailzw.service.CurrentUserService;
import com.retailzw.service.FuelStationService;
import com.retailzw.service.PackageModuleAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class FuelStationWebController {
    private final CurrentUserService current;
    private final FuelStationService fuel;
    private final PackageModuleAccessService moduleAccess;
    private final BranchRepository branches;
    private final TenantRepository tenants;
    private final SaasPlanRepository plans;

    @GetMapping({"/shop/fuel", "/shop/fuel/{section}"})
    public String page(@PathVariable(required = false) String section,
                       @RequestParam(required = false) Long branchId, @RequestParam(defaultValue="0") int recordPage, jakarta.servlet.http.HttpSession session, Model model) {
        moduleAccess.requireModule(current.tenantId(), BusinessModule.FUEL_MODULE);
        String page = switch (section == null ? "" : section) {
            case "setup", "deliveries", "pricing", "shifts", "reports", "sales", "stock", "expenses" -> section;
            default -> "dashboard";
        };
        List<Branch> fuelBranches = fuel.fuelBranches(current.tenantId(), current.branchId());
        String branchKey="fuelBranch:"+current.tenantId()+":"+current.userId();
        if(branchId==null && session.getAttribute(branchKey) instanceof Long saved) branchId=saved;
        Long selected = selectedBranch(fuelBranches, branchId, false);
        session.setAttribute(branchKey,selected==null?0L:selected);
        var dashboard = fuel.dashboard(current.tenantId(), current.branchId(), selected);
        model.addAttribute("title", switch (page) {
            case "setup" -> "Fuel Tanks & Pumps"; case "deliveries" -> "Fuel Deliveries";
            case "pricing" -> "Fuel Pricing"; case "shifts" -> "Fuel Shifts";
            case "reports" -> "Fuel Reports"; case "expenses" -> "Station Expenses"; case "sales" -> "Fuel Sales"; case "stock" -> "Fuel Stock Ledger"; default -> "Fuel Station Command Centre";
        });
        model.addAttribute("module", "dashboard".equals(page) ? "fuel" : "fuel-" + page);
        model.addAttribute("fuelPage", page);
        model.addAttribute("financialSummary", "reports".equals(page)?fuel.financialSummary(current.tenantId(),current.branchId(),selected):List.of());
        model.addAttribute("canManageFuel", !"SUPERVISOR".equals(current.roleName()));
        var records = fuel.records(current.tenantId(),current.branchId(),selected,page,recordPage);
        model.addAttribute("fuelRecords",records.stream().limit(50).toList());
        model.addAttribute("recordColumns",records.isEmpty()?List.of():List.copyOf(records.get(0).keySet()));
        model.addAttribute("recordPage",Math.max(0,recordPage));
        model.addAttribute("hasMoreRecords",records.size()>50);
        model.addAttribute("branchNames",fuelBranches.stream().collect(java.util.stream.Collectors.toMap(Branch::getId,Branch::getName)));
        model.addAttribute("fuelDashboard", dashboard);
        model.addAttribute("fuelBranches", fuelBranches);
        model.addAttribute("workspaceBranchOptions", fuelBranches);
        model.addAttribute("workspaceBranchId", selected);
        model.addAttribute("workspaceReturnTo", "/shop/fuel" + ("dashboard".equals(page) ? "" : "/" + page));
        model.addAttribute("tenantActiveBranchName", selected == null ? "All fuel stations" : fuelBranches.stream().filter(b -> b.getId().equals(selected)).map(Branch::getName).findFirst().orElse("Fuel station"));
        model.addAttribute("topbarBranchName", model.getAttribute("tenantActiveBranchName"));
        model.addAttribute("enabledBusinessModules", moduleAccess.syncAndGetEnabledModules(current.tenantId()));
        model.addAttribute("fuelModuleEnabled", true);
        model.addAttribute("shopModuleEnabled", moduleAccess.hasRetailShop(current.tenantId()));
        model.addAttribute("gasModuleEnabled", moduleAccess.hasGas(current.tenantId()));
        model.addAttribute("tenantBillingOnly", false);
        model.addAttribute("grades", fuel.grades(current.tenantId()));
        Long detailBranch = selected != null ? selected : (fuelBranches.size() == 1 ? fuelBranches.get(0).getId() : null);
        if (detailBranch != null) {
            model.addAttribute("detailBranchId", detailBranch);
            model.addAttribute("pumps", fuel.pumps(current.tenantId(), current.branchId(), detailBranch));
            model.addAttribute("deliveries", fuel.recentDeliveries(current.tenantId(), current.branchId(), detailBranch));
            model.addAttribute("shifts", fuel.recentShifts(current.tenantId(), current.branchId(), detailBranch));
            model.addAttribute("fuelSales", fuel.recentSales(current.tenantId(), current.branchId(), detailBranch));
        } else {
            model.addAttribute("pumps", List.of()); model.addAttribute("deliveries", List.of());
            model.addAttribute("shifts", List.of()); model.addAttribute("fuelSales", List.of());
        }
        tenants.findById(current.tenantId()).ifPresent(tenant -> {
            String packageName = tenant.getPlanId() == null ? "Fuel Station" : plans.findById(tenant.getPlanId()).map(SaasPlan::getName).orElse("Fuel Station");
            model.addAttribute("tenantPackageLabel", packageName.toLowerCase().endsWith("plan") ? packageName : packageName + " Plan");
            model.addAttribute("tenantStatus", tenant.getStatus());
        });
        return "shop/fuel-station";
    }

    @PostMapping("/shop/fuel/grades")
    public String grade(@RequestParam String code,@RequestParam String name,@RequestParam(defaultValue="#087cf0") String colour,RedirectAttributes redirect){return action(redirect,()->fuel.addGrade(current.tenantId(),code,name,colour),"Fuel grade created.","/shop/fuel/setup");}
    @PostMapping("/shop/fuel/tanks")
    public String tank(@RequestParam Long branchId,@RequestParam Long gradeId,@RequestParam String code,@RequestParam BigDecimal capacityLitres,@RequestParam BigDecimal openingStockLitres,@RequestParam BigDecimal reorderLevelLitres,RedirectAttributes redirect){return action(redirect,()->fuel.addTank(current.tenantId(),current.branchId(),branchId,gradeId,code,capacityLitres,openingStockLitres,reorderLevelLitres),"Fuel tank created.","/shop/fuel/setup?branchId="+branchId);}
    @PostMapping("/shop/fuel/pumps")
    public String pump(@RequestParam Long branchId,@RequestParam String code,RedirectAttributes redirect){return action(redirect,()->fuel.addPump(current.tenantId(),current.branchId(),branchId,code),"Pump created.","/shop/fuel/setup?branchId="+branchId);}
    @PostMapping("/shop/fuel/nozzles")
    public String nozzle(@RequestParam Long branchId,@RequestParam Long pumpId,@RequestParam Long tankId,@RequestParam Long gradeId,@RequestParam String code,@RequestParam BigDecimal openingMeterLitres,RedirectAttributes redirect){return action(redirect,()->fuel.addNozzle(current.tenantId(),current.branchId(),branchId,pumpId,tankId,gradeId,code,openingMeterLitres),"Nozzle connected.","/shop/fuel/setup?branchId="+branchId);}
    @PostMapping("/shop/fuel/prices")
    public String price(@RequestParam Long branchId,@RequestParam Long gradeId,@RequestParam String currency,@RequestParam BigDecimal amountPerLitre,RedirectAttributes redirect){return action(redirect,()->fuel.setPrice(current.tenantId(),current.userId(),current.branchId(),branchId,gradeId,currency,amountPerLitre),"Fuel price approved.","/shop/fuel/pricing?branchId="+branchId);}
    @PostMapping("/shop/fuel/deliveries")
    public String delivery(@RequestParam Long branchId,@RequestParam Long tankId,@RequestParam String supplier,@RequestParam String deliveryNote,@RequestParam BigDecimal invoiceLitres,@RequestParam BigDecimal receivedLitres,@RequestParam BigDecimal beforeDipLitres,@RequestParam BigDecimal afterDipLitres,@RequestParam BigDecimal costAmount,@RequestParam String currency,RedirectAttributes redirect){Delivery request=new Delivery(branchId,tankId,supplier,deliveryNote,invoiceLitres,receivedLitres,beforeDipLitres,afterDipLitres,costAmount,currency);return action(redirect,()->fuel.delivery(current.tenantId(),current.userId(),current.branchId(),request),"Fuel delivery received.","/shop/fuel/deliveries?branchId="+branchId);}

    @PostMapping("/shop/fuel/dips")
    public String dip(@RequestParam Long branchId,@RequestParam Long tankId,@RequestParam BigDecimal litres,@RequestParam BigDecimal water,RedirectAttributes redirect){return action(redirect,()->fuel.recordDip(current.tenantId(),current.userId(),current.branchId(),branchId,tankId,litres,water),"Tank dip recorded.","/shop/fuel?branchId="+branchId);}

    @PostMapping("/shop/fuel/nozzle-status")
    public String status(@RequestParam Long branchId,@RequestParam Long nozzleId,@RequestParam String status,RedirectAttributes redirect){return action(redirect,()->fuel.nozzleStatus(current.tenantId(),current.userId(),current.branchId(),branchId,nozzleId,status),"Nozzle availability updated.","/shop/fuel?branchId="+branchId);}

    @PostMapping("/shop/fuel/expenses")
    public String expense(@RequestParam Long branchId,@RequestParam String category,@RequestParam String description,@RequestParam BigDecimal amount,@RequestParam String currency,@RequestParam String reference,@RequestParam java.time.LocalDate date,RedirectAttributes redirect){return action(redirect,()->fuel.expense(current.tenantId(),current.userId(),current.branchId(),branchId,category,description,amount,currency,reference,date),"Expense recorded.","/shop/fuel/expenses?branchId="+branchId);}

    private Long selectedBranch(List<Branch> allowed,Long requested,boolean required){if(current.branchId()!=null)return current.branchId();if(requested!=null&&requested>0)return allowed.stream().filter(b->b.getId().equals(requested)).findFirst().orElseThrow(()->new IllegalArgumentException("Fuel branch is not available.")).getId();if(required&&allowed.size()==1)return allowed.get(0).getId();return null;}
    private String action(RedirectAttributes redirect,Operation operation,String success,String path){try{operation.run();redirect.addFlashAttribute("message",success);}catch(IllegalArgumentException|IllegalStateException ex){redirect.addFlashAttribute("error",ex.getMessage());}return "redirect:"+path;}
    @FunctionalInterface private interface Operation {void run();}
}
