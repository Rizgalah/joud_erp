package com.joud.inventory.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public class Product {

    @Id
    @Column(name = "prod_i_id")
    private Long id;

    @Column(name = "prod_c_code", unique = true, nullable = false, length = 100)
    private String code;

    @Column(name = "prod_c_name", nullable = false, length = 255)
    private String name;

    @Column(name = "prod_c_barcode", length = 100)
    private String barcode;

    @Column(name = "prod_c_description")
    private String description;

    @Column(name = "categ_i_id")
    private Long categoryId;

    @Column(name = "unit_i_id")
    private Long unitId;

    @Column(name = "unit2_i_id")
    private Long unit2Id;

    @Column(name = "prod_f_conv_factor", precision = 23, scale = 5)
    private BigDecimal conversionFactor = BigDecimal.ONE;

    @Column(name = "WARE_I_ID")
    private Long warehouseId;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "AGENT_I_ID")
    private Long agentId;

    // ─── الأسعار ───────────────────────────────────
    @Column(name = "purchase_price", precision = 23, scale = 5)
    private BigDecimal purchasePrice = BigDecimal.ZERO;

    @Column(name = "sale_price", precision = 23, scale = 5)
    private BigDecimal salePrice = BigDecimal.ZERO;

    @Column(name = "prod_f_avg_cost", precision = 23, scale = 5)
    private BigDecimal avgCost = BigDecimal.ZERO;

    @Column(name = "CURRENCY_I_ID")
    private Long currencyId;

    // ─── المخزون ───────────────────────────────────
    @Column(name = "opening_quantity", precision = 23, scale = 5)
    private BigDecimal openingQty = BigDecimal.ZERO;

    @Column(name = "current_quantity", precision = 23, scale = 5)
    private BigDecimal currentQty = BigDecimal.ZERO;

    @Column(name = "min_quantity", precision = 23, scale = 5)
    private BigDecimal minQty = BigDecimal.ZERO;

    @Column(name = "prod_f_moq", precision = 23, scale = 5)
    private BigDecimal moq = BigDecimal.ONE;

    @Column(name = "prod_f_weight", precision = 23, scale = 5)
    private BigDecimal weight = BigDecimal.ZERO;

    @Column(name = "prod_d_expiry")
    private LocalDate expiryDate;

    // ─── الإعدادات ─────────────────────────────────
    @Column(name = "prod_c_track_stock", length = 1)
    private String trackStock = "Y";

    @Column(name = "prod_c_is_service", length = 1)
    private String isService = "N";

    @Column(name = "prod_c_status", length = 1)
    private String status = "A";

    @Column(name = "prod_c_image_path", length = 500)
    private String imagePath;

    // ─── Helpers ───────────────────────────────────
    public boolean isActive() {
        return "A".equals(status);
    }

    public boolean isService() {
        return "Y".equals(isService);
    }

    public boolean isTracked() {
        return "Y".equals(trackStock);
    }

    public boolean isBelowMin() {
        return currentQty != null && minQty != null
                && currentQty.compareTo(minQty) <= 0;
    }

    public BigDecimal getStockValue() {
        if (currentQty == null || avgCost == null)
            return BigDecimal.ZERO;
        return currentQty.multiply(avgCost);
    }

    public BigDecimal getProfitMargin() {
        if (purchasePrice == null || purchasePrice.compareTo(BigDecimal.ZERO) == 0)
            return BigDecimal.ZERO;
        return salePrice.subtract(purchasePrice)
                .divide(purchasePrice, 4, java.math.RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }

    public String getStatusLabel() {
        return switch (status != null ? status : "A") {
            case "A" -> "نشط";
            case "D" -> "متوقف";
            case "O" -> "منتهي";
            default -> status;
        };
    }

    // Getters & Setters
    public Long getId() {
        return id;
    }

    public void setId(Long v) {
        this.id = v;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String v) {
        this.code = v;
    }

    public String getName() {
        return name;
    }

    public void setName(String v) {
        this.name = v;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String v) {
        this.barcode = v;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String v) {
        this.description = v;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long v) {
        this.categoryId = v;
    }

    public Long getUnitId() {
        return unitId;
    }

    public void setUnitId(Long v) {
        this.unitId = v;
    }

    public Long getUnit2Id() {
        return unit2Id;
    }

    public void setUnit2Id(Long v) {
        this.unit2Id = v;
    }

    public BigDecimal getConversionFactor() {
        return conversionFactor;
    }

    public void setConversionFactor(BigDecimal v) {
        this.conversionFactor = v;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long v) {
        this.warehouseId = v;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long v) {
        this.supplierId = v;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long v) {
        this.agentId = v;
    }

    public BigDecimal getPurchasePrice() {
        return purchasePrice;
    }

    public void setPurchasePrice(BigDecimal v) {
        this.purchasePrice = v;
    }

    public BigDecimal getSalePrice() {
        return salePrice;
    }

    public void setSalePrice(BigDecimal v) {
        this.salePrice = v;
    }

    public BigDecimal getAvgCost() {
        return avgCost;
    }

    public void setAvgCost(BigDecimal v) {
        this.avgCost = v;
    }

    public Long getCurrencyId() {
        return currencyId;
    }

    public void setCurrencyId(Long v) {
        this.currencyId = v;
    }

    public BigDecimal getOpeningQty() {
        return openingQty;
    }

    public void setOpeningQty(BigDecimal v) {
        this.openingQty = v;
    }

    public BigDecimal getCurrentQty() {
        return currentQty;
    }

    public void setCurrentQty(BigDecimal v) {
        this.currentQty = v;
    }

    public BigDecimal getMinQty() {
        return minQty;
    }

    public void setMinQty(BigDecimal v) {
        this.minQty = v;
    }

    public BigDecimal getMoq() {
        return moq;
    }

    public void setMoq(BigDecimal v) {
        this.moq = v;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal v) {
        this.weight = v;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate v) {
        this.expiryDate = v;
    }

    public String getTrackStock() {
        return trackStock;
    }

    public void setTrackStock(String v) {
        this.trackStock = v;
    }

    public String getIsService() {
        return isService;
    }

    public void setIsService(String v) {
        this.isService = v;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String v) {
        this.status = v;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String v) {
        this.imagePath = v;
    }
}
