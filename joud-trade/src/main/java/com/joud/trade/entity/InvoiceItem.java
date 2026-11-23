package com.joud.trade.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

// ══════════════════════════════════════════════════════════════
// InvoiceItem — سطر الفاتورة
// ══════════════════════════════════════════════════════════════
@Entity
@Table(name = "invoice_items")
public class InvoiceItem {

    @Id
    @Column(name = "invitem_i_id")
    private Long id;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "product_code", length = 100)
    private String productCode;

    @Column(name = "unit_name", length = 100)
    private String unitName;

    @Column(name = "AGENT_I_ID")
    private Long agentId;

    @Column(name = "quantity", nullable = false, precision = 23, scale = 5)
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(name = "unit_price", nullable = false, precision = 23, scale = 5)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "CURRENCY_I_ID")
    private Long currencyId;

    @Column(name = "discount_amount", precision = 23, scale = 5)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "total", nullable = false, precision = 23, scale = 5)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_by")
    private Long createdBy;

    // ─── Business Logic ────────────────────────────
    public void recalculate() {
        this.total = quantity.multiply(unitPrice)
                .subtract(discountAmount)
                .setScale(5, java.math.RoundingMode.HALF_UP);
    }

    // Getters & Setters
    public Long getId() {
        return id;
    }

    public void setId(Long v) {
        this.id = v;
    }

    public Long getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(Long v) {
        this.invoiceId = v;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long v) {
        this.productId = v;
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

    public String getUnitName() {
        return unitName;
    }

    public void setUnitName(String v) {
        this.unitName = v;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long v) {
        this.agentId = v;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal v) {
        this.quantity = v;
        recalculate();
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal v) {
        this.unitPrice = v;
        recalculate();
    }

    public Long getCurrencyId() {
        return currencyId;
    }

    public void setCurrencyId(Long v) {
        this.currencyId = v;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal v) {
        this.discountAmount = v;
        recalculate();
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal v) {
        this.total = v;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String v) {
        this.notes = v;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long v) {
        this.createdBy = v;
    }
}
