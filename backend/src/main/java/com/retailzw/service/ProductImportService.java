package com.retailzw.service;

import com.retailzw.dto.response.ProductImportResult;
import com.retailzw.model.Branch;
import com.retailzw.model.Inventory;
import com.retailzw.model.Product;
import com.retailzw.model.ProductCategory;
import com.retailzw.model.UnitOfMeasure;
import com.retailzw.repository.BranchRepository;
import com.retailzw.repository.InventoryRepository;
import com.retailzw.repository.ProductCategoryRepository;
import com.retailzw.repository.ProductRepository;
import com.retailzw.repository.UnitOfMeasureRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductImportService {

    private static final int MAX_ERRORS = 50;

    private final ProductRepository products;
    private final ProductCategoryRepository categories;
    private final UnitOfMeasureRepository uoms;
    private final InventoryRepository inventory;
    private final BranchRepository branches;

    @Transactional
    public ProductImportResult importProducts(Long tenantId, Long branchId, Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a product Excel file to import.");
        }
        Branch branch = branches.findById(branchId)
                .filter(b -> b.getTenantId().equals(tenantId))
                .filter(b -> Boolean.TRUE.equals(b.getIsActive()))
                .orElseThrow(() -> new IllegalArgumentException("Selected branch is not active for this shop."));

        ProductImportResult result = ProductImportResult.builder().build();
        DataFormatter formatter = new DataFormatter();

        try (InputStream stream = file.getInputStream();
             org.apache.poi.ss.usermodel.Workbook workbook = WorkbookFactory.create(stream)) {
            Sheet sheet = workbook.getSheet("Products Import");
            if (sheet == null) {
                sheet = workbook.getSheetAt(0);
            }
            Map<String, Integer> headers = findHeaders(sheet, formatter);
            requireHeader(headers, "name");

            for (int rowNumber = headerRowIndex(sheet, formatter) + 1; rowNumber <= sheet.getLastRowNum(); rowNumber++) {
                Row row = sheet.getRow(rowNumber);
                if (row == null || isBlankRow(row, formatter)) {
                    continue;
                }
                result.setProcessedRows(result.getProcessedRows() + 1);
                try {
                    importRow(tenantId, branch.getId(), userId, row, headers, formatter, result);
                } catch (Exception ex) {
                    result.setSkippedRows(result.getSkippedRows() + 1);
                    addError(result, "Row " + (rowNumber + 1) + ": " + ex.getMessage());
                }
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not read product Excel file: " + ex.getMessage(), ex);
        }

        return result;
    }

    private void importRow(Long tenantId, Long branchId, Long userId, Row row, Map<String, Integer> headers,
                           DataFormatter formatter, ProductImportResult result) {
        String name = value(row, headers, formatter, "name");
        String sku = value(row, headers, formatter, "sku");
        String barcode = value(row, headers, formatter, "barcode");
        if (name == null) {
            throw new IllegalArgumentException("Name is required.");
        }
        if (sku == null && barcode == null) {
            sku = generatedSku(name);
        }

        Optional<Product> existing = sku == null ? Optional.empty() : products.findByTenantIdAndSku(tenantId, sku);
        if (existing.isEmpty() && barcode != null) {
            existing = products.findByTenantIdAndBarcode(tenantId, barcode);
        }
        Product product = existing.orElseGet(() -> Product.builder()
                .tenantId(tenantId)
                .createdBy(userId)
                .isActive(true)
                .build());

        assertNoConflictingIdentity(tenantId, product, sku, barcode);

        product.setName(name);
        product.setSku(sku);
        product.setBarcode(barcode);
        product.setDescription(value(row, headers, formatter, "description"));
        product.setCategory(resolveCategory(tenantId, value(row, headers, formatter, "category")));
        product.setUnitOfMeasure(resolveUom(tenantId, value(row, headers, formatter, "uom")));
        product.setCostPriceUsd(decimal(row, headers, formatter, "costusd", BigDecimal.ZERO));
        product.setSellingPriceUsd(decimal(row, headers, formatter, "sellingusd", BigDecimal.ZERO));
        product.setCostPriceZwg(decimal(row, headers, formatter, "costzwg", BigDecimal.ZERO));
        product.setSellingPriceZwg(decimal(row, headers, formatter, "sellingzwg", BigDecimal.ZERO));
        product.setTaxRate(decimal(row, headers, formatter, "taxrate", BigDecimal.ZERO));
        product.setIsTaxable(bool(row, headers, formatter, "taxable", true));
        product.setReorderLevel(decimal(row, headers, formatter, "reorderlevel", BigDecimal.ZERO));
        product.setMaxStockLevel(decimal(row, headers, formatter, "maxstock", null));
        product.setImageUrl(value(row, headers, formatter, "imageurl"));
        product.setIsService(bool(row, headers, formatter, "isservice", false));
        product.setHasVariants(bool(row, headers, formatter, "hasvariants", false));
        product.setTrackingMode(trackingMode(value(row, headers, formatter, "trackingmode")));
        product.setExpiryTracking(bool(row, headers, formatter, "expirytracking", false));
        product.setIsActive(true);

        boolean created = product.getId() == null;
        Product saved = products.save(product);
        if (created) {
            result.setCreatedProducts(result.getCreatedProducts() + 1);
        } else {
            result.setUpdatedProducts(result.getUpdatedProducts() + 1);
        }

        BigDecimal openingStock = decimal(row, headers, formatter, "openingstock", BigDecimal.ZERO);
        Inventory stock = inventory.findByTenantIdAndBranchIdAndProductId(tenantId, branchId, saved.getId())
                .orElseGet(() -> Inventory.builder()
                        .tenantId(tenantId)
                        .branchId(branchId)
                        .productId(saved.getId())
                        .quantityReserved(BigDecimal.ZERO)
                        .quantityOnOrder(BigDecimal.ZERO)
                        .build());
        stock.setQuantityOnHand(openingStock);
        stock.setAverageCostUsd(saved.getCostPriceUsd());
        stock.setAverageCostZwg(saved.getCostPriceZwg());
        inventory.save(stock);
        result.setStockRowsUpdated(result.getStockRowsUpdated() + 1);
    }

    private void assertNoConflictingIdentity(Long tenantId, Product product, String sku, String barcode) {
        if (sku != null) {
            products.findByTenantIdAndSku(tenantId, sku)
                    .filter(found -> !found.getId().equals(product.getId()))
                    .ifPresent(found -> {
                        throw new IllegalArgumentException("SKU already belongs to another product: " + sku);
                    });
        }
        if (barcode != null) {
            products.findByTenantIdAndBarcode(tenantId, barcode)
                    .filter(found -> !found.getId().equals(product.getId()))
                    .ifPresent(found -> {
                        throw new IllegalArgumentException("Barcode already belongs to another product: " + barcode);
                    });
        }
    }

    private ProductCategory resolveCategory(Long tenantId, String categoryName) {
        if (categoryName == null) return null;
        List<ProductCategory> existing = categories.findByTenantIdOrderBySortOrderAsc(tenantId);
        for (ProductCategory category : existing) {
            if (equalsClean(category.getName(), categoryName) || equalsClean(category.getCode(), categoryName)) {
                return category;
            }
        }
        String code = generatedCode(categoryName);
        while (categories.existsByTenantIdAndCode(tenantId, code)) {
            code = code + "1";
        }
        return categories.save(ProductCategory.builder()
                .tenantId(tenantId)
                .name(categoryName)
                .code(code)
                .isActive(true)
                .sortOrder(existing.size() + 1)
                .build());
    }

    private UnitOfMeasure resolveUom(Long tenantId, String abbreviation) {
        if (abbreviation == null) return null;
        for (UnitOfMeasure uom : uoms.findByTenantId(tenantId)) {
            if (equalsClean(uom.getAbbreviation(), abbreviation) || equalsClean(uom.getName(), abbreviation)) {
                return uom;
            }
        }
        return uoms.save(UnitOfMeasure.builder()
                .tenantId(tenantId)
                .name(abbreviation)
                .abbreviation(abbreviation)
                .isDecimal(false)
                .build());
    }

    private Map<String, Integer> findHeaders(Sheet sheet, DataFormatter formatter) {
        int rowIndex = headerRowIndex(sheet, formatter);
        if (rowIndex < 0) {
            throw new IllegalArgumentException("Could not find a header row with a Name column.");
        }
        Row row = sheet.getRow(rowIndex);
        Map<String, Integer> headers = new HashMap<>();
        for (Cell cell : row) {
            String normalized = normalizeHeader(formatter.formatCellValue(cell));
            if (!normalized.isBlank()) {
                headers.put(normalized, cell.getColumnIndex());
            }
        }
        return headers;
    }

    private int headerRowIndex(Sheet sheet, DataFormatter formatter) {
        int max = Math.min(sheet.getLastRowNum(), 20);
        for (int i = 0; i <= max; i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            for (Cell cell : row) {
                if ("name".equals(normalizeHeader(formatter.formatCellValue(cell)))) {
                    return i;
                }
            }
        }
        return -1;
    }

    private void requireHeader(Map<String, Integer> headers, String header) {
        if (!headers.containsKey(header)) {
            throw new IllegalArgumentException("Missing required column: " + header);
        }
    }

    private String value(Row row, Map<String, Integer> headers, DataFormatter formatter, String header) {
        Integer index = headers.get(header);
        if (index == null) return null;
        String value = formatter.formatCellValue(row.getCell(index)).trim();
        return value.isBlank() ? null : value;
    }

    private BigDecimal decimal(Row row, Map<String, Integer> headers, DataFormatter formatter, String header, BigDecimal fallback) {
        String value = value(row, headers, formatter, header);
        if (value == null) return fallback;
        try {
            return new BigDecimal(value.replace(",", ""));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(header + " must be a number.");
        }
    }

    private Boolean bool(Row row, Map<String, Integer> headers, DataFormatter formatter, String header, boolean fallback) {
        String value = value(row, headers, formatter, header);
        if (value == null) return fallback;
        String clean = value.trim().toLowerCase(Locale.ROOT);
        return clean.equals("true") || clean.equals("yes") || clean.equals("y") || clean.equals("1");
    }

    private Product.TrackingMode trackingMode(String value) {
        if (value == null) return Product.TrackingMode.NONE;
        try {
            return Product.TrackingMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return Product.TrackingMode.NONE;
        }
    }

    private String normalizeHeader(String value) {
        String clean = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKD)
                .replaceAll("[^A-Za-z0-9]", "")
                .toLowerCase(Locale.ROOT);
        return switch (clean) {
            case "productname" -> "name";
            case "categoryname" -> "category";
            case "unit", "unitofmeasure" -> "uom";
            case "costpriceusd", "costus" -> "costusd";
            case "sellingpriceusd", "priceusd", "sellusd" -> "sellingusd";
            case "costpricezwg", "costzig", "costpricezig" -> "costzwg";
            case "sellingpricezwg", "pricezwg", "sellzwg", "sellingpricezig", "pricezig" -> "sellingzwg";
            case "taxrate", "taxratepercent" -> "taxrate";
            case "reorder", "reorderstock" -> "reorderlevel";
            case "maxstocklevel" -> "maxstock";
            case "quantity", "qty", "quantityonhand", "stock", "openingquantity" -> "openingstock";
            case "service" -> "isservice";
            case "variants" -> "hasvariants";
            case "tracking" -> "trackingmode";
            case "expiry" -> "expirytracking";
            default -> clean;
        };
    }

    private boolean isBlankRow(Row row, DataFormatter formatter) {
        for (Cell cell : row) {
            if (!formatter.formatCellValue(cell).trim().isBlank()) {
                return false;
            }
        }
        return true;
    }

    private void addError(ProductImportResult result, String error) {
        if (result.getErrors().size() < MAX_ERRORS) {
            result.getErrors().add(error);
        }
    }

    private boolean equalsClean(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
    }

    private String generatedSku(String name) {
        return generatedCode(name) + "-" + Math.abs(name.hashCode());
    }

    private String generatedCode(String name) {
        String code = Normalizer.normalize(name, Normalizer.Form.NFKD)
                .replaceAll("[^A-Za-z0-9]", "")
                .toUpperCase(Locale.ROOT);
        if (code.length() > 10) {
            code = code.substring(0, 10);
        }
        return code.isBlank() ? "PRODUCT" : code;
    }
}
