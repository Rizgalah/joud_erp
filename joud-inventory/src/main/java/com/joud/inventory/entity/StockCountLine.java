package com.joud.inventory.entity;

import jakarta.persistence.Entity; // 👈 الصواب المتوافق مع تومكات 10
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.io.Serializable;

@Entity // 👈 تأكد من وجود هذا السطر تحديداً وبحروف كبيرة وصغيرة صحيحة
@Table(name = "stock_count_line")
public class StockCountLine implements Serializable {

    @Id
    @Column(name = "LINE_I_ID")
    private Long id;

    @Column(name = "COUNT_I_ID", nullable = false)
    private Long countId;

    @Column(name = "PROD_I_ID", nullable = false)
    private Long productId;

    @Column(name = "WARE_I_ID")
    private Long warehouseId;

    @Column(name = "LINE_F_SYS_QTY", precision = 23, scale = 5)
    private BigDecimal sysQty = BigDecimal.ZERO;

    @Column(name = "LINE_F_ACTUAL_QTY", precision = 23, scale = 5)
    private BigDecimal actualQty = BigDecimal.ZERO;

    @Column(name = "LINE_F_DIFF_QTY", precision = 23, scale = 5)
    private BigDecimal diffQty = BigDecimal.ZERO;

    @Column(name = "LINE_F_UNIT_COST", precision = 23, scale = 5)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(name = "LINE_F_DIFF_VAL", precision = 23, scale = 5)
    private BigDecimal diffValue = BigDecimal.ZERO;

    @Column(name = "LINE_C_NOTES", length = 250)
    private String notes;

    @Column(name = "COUNTED_BY")
    private Long countedBy;

    @Column(name = "COUNTED_AT")
    private LocalDateTime countedAt;

    // اسم الصنف للعرض (غير محفوظ في قاعدة البيانات)
    @Transient
    private String productName;
    @Transient
    private String productCode;

    public void recalculate() {
        this.diffQty = (actualQty != null ? actualQty : BigDecimal.ZERO)
                .subtract(sysQty != null ? sysQty : BigDecimal.ZERO);
        this.diffValue = diffQty.multiply(unitCost != null ? unitCost : BigDecimal.ZERO)
                .setScale(5, java.math.RoundingMode.HALF_UP);
    }

    public boolean hasDifference() {
        return diffQty != null && diffQty.compareTo(BigDecimal.ZERO) != 0;
    }

    // Getters & Setters
    public Long getId() {
        return id;
    }

    public void setId(Long v) {
        this.id = v;
    }

    public Long getCountId() {
        return countId;
    }

    public void setCountId(Long v) {
        this.countId = v;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long v) {
        this.productId = v;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long v) {
        this.warehouseId = v;
    }

    public BigDecimal getSysQty() {
        return sysQty;
    }

    public void setSysQty(BigDecimal v) {
        this.sysQty = v;
    }

    public BigDecimal getActualQty() {
        return actualQty;
    }

    public void setActualQty(BigDecimal v) {
        this.actualQty = v;
        recalculate();
    }

    public BigDecimal getDiffQty() {
        return diffQty;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(BigDecimal v) {
        this.unitCost = v;
    }

    public BigDecimal getDiffValue() {
        return diffValue;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String v) {
        this.notes = v;
    }

    public Long getCountedBy() {
        return countedBy;
    }

    public void setCountedBy(Long v) {
        this.countedBy = v;
    }

    public LocalDateTime getCountedAt() {
        return countedAt;
    }

    public void setCountedAt(LocalDateTime v) {
        this.countedAt = v;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String v) {
        this.productName = v;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String v) {
        this.productCode = v;
    }
}
