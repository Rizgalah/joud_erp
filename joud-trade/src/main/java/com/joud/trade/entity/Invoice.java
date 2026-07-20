package com.joud.trade.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Invoice {

    public enum InvoiceType   { sale, purchase, sale_return, purchase_return }
    public enum InvoiceStatus { draft, posted, cancelled }
    public enum PaymentMethod { cash, credit, partial }

    @Id @Column(name = "invoice_i_id")
    private Long id;

    @Column(name = "invoice_number", unique = true, nullable = false, length = 100)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_c_type", nullable = false, length = 50)
    private InvoiceType type;

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "AGENT_I_ID", nullable = false)
    private Long agentId;

    @Column(name = "CURRENCY_I_ID", nullable = false)
    private Long currencyId;

    @Column(name = "invoice_d_date", nullable = false)
    private LocalDate invoiceDate = LocalDate.now();

    @Column(name = "invoice_d_due")
    private LocalDate dueDate;

    @Column(name = "invoice_c_ref", length = 100)
    private String reference;

    // ─── المبالغ ──────────────────────────────────
    @Column(name = "subtotal", precision = 23, scale = 5)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "invoice_f_disc_amt", precision = 23, scale = 5)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "invoice_f_tax_rate", precision = 5, scale = 2)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "invoice_f_tax_amt", precision = 23, scale = 5)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total", precision = 23, scale = 5)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "invoice_f_paid", precision = 23, scale = 5)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "invoice_f_balance", precision = 23, scale = 5)
    private BigDecimal balance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 50)
    private PaymentMethod paymentMethod = PaymentMethod.cash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50)
    private InvoiceStatus status = InvoiceStatus.draft;

    @Column(name = "SALES_EMP_ID")
    private Long salesEmployeeId;

    @Column(name = "JV_I_ID")
    private Long journalVoucherId;

    @Column(name = "invoice_c_notes")
    private String notes;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "invoiceId", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<InvoiceItem> items = new ArrayList<>();

    // ─── Business Logic ────────────────────────────
    public void recalculate() {
        this.subtotal = items.stream()
            .map(InvoiceItem::getTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.taxAmount = subtotal.subtract(discountAmount)
            .multiply(taxRate)
            .divide(new java.math.BigDecimal("100"), 5, java.math.RoundingMode.HALF_UP);

        this.total   = subtotal.subtract(discountAmount).add(taxAmount);
        this.balance = total.subtract(paidAmount);
    }

    public boolean isDraft()     { return InvoiceStatus.draft     == status; }
    public boolean isPosted()    { return InvoiceStatus.posted    == status; }
    public boolean isCancelled() { return InvoiceStatus.cancelled == status; }
    public boolean isSale()      { return InvoiceType.sale        == type; }
    public boolean isPurchase()  { return InvoiceType.purchase    == type; }
    public boolean isFullyPaid() { return balance.compareTo(BigDecimal.ZERO) <= 0; }
    public boolean canPost()     { return isDraft() && !items.isEmpty(); }
    public boolean canCancel()   { return !isCancelled() && paidAmount.compareTo(BigDecimal.ZERO) == 0; }

    public String getTypeLabel() {
        if (type == null) return "—";
        return switch (type) {
            case sale             -> "فاتورة مبيعات";
            case purchase         -> "فاتورة مشتريات";
            case sale_return      -> "مردود مبيعات";
            case purchase_return  -> "مردود مشتريات";
        };
    }

    public String getStatusLabel() {
        if (status == null) return "—";
        return switch (status) {
            case draft     -> "مسودة";
            case posted    -> "مرحّلة";
            case cancelled -> "ملغاة";
        };
    }

    // Getters & Setters
    public Long getId()                           { return id; }
    public void setId(Long v)                     { this.id = v; }
    public String getNumber()                     { return number; }
    public void setNumber(String v)               { this.number = v; }
    public InvoiceType getType()                  { return type; }
    public void setType(InvoiceType v)            { this.type = v; }
    public Long getPartyId()                      { return partyId; }
    public void setPartyId(Long v)                { this.partyId = v; }
    public Long getWarehouseId()                  { return warehouseId; }
    public void setWarehouseId(Long v)            { this.warehouseId = v; }
    public Long getAgentId()                      { return agentId; }
    public void setAgentId(Long v)                { this.agentId = v; }
    public Long getCurrencyId()                   { return currencyId; }
    public void setCurrencyId(Long v)             { this.currencyId = v; }
    public LocalDate getInvoiceDate()             { return invoiceDate; }
    public void setInvoiceDate(LocalDate v)       { this.invoiceDate = v; }
    public LocalDate getDueDate()                 { return dueDate; }
    public void setDueDate(LocalDate v)           { this.dueDate = v; }
    public String getReference()                  { return reference; }
    public void setReference(String v)            { this.reference = v; }
    public BigDecimal getSubtotal()               { return subtotal; }
    public void setSubtotal(BigDecimal v)         { this.subtotal = v; }
    public BigDecimal getDiscountAmount()         { return discountAmount; }
    public void setDiscountAmount(BigDecimal v)   { this.discountAmount = v; }
    public BigDecimal getTaxRate()                { return taxRate; }
    public void setTaxRate(BigDecimal v)          { this.taxRate = v; }
    public BigDecimal getTaxAmount()              { return taxAmount; }
    public void setTaxAmount(BigDecimal v)        { this.taxAmount = v; }
    public BigDecimal getTotal()                  { return total; }
    public void setTotal(BigDecimal v)            { this.total = v; }
    public BigDecimal getPaidAmount()             { return paidAmount; }
    public void setPaidAmount(BigDecimal v)       { this.paidAmount = v; }
    public BigDecimal getBalance()                { return balance; }
    public void setBalance(BigDecimal v)          { this.balance = v; }
    public PaymentMethod getPaymentMethod()       { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod v) { this.paymentMethod = v; }
    public InvoiceStatus getStatus()              { return status; }
    public void setStatus(InvoiceStatus v)        { this.status = v; }
    public Long getSalesEmployeeId()              { return salesEmployeeId; }
    public void setSalesEmployeeId(Long v)        { this.salesEmployeeId = v; }
    public Long getJournalVoucherId()             { return journalVoucherId; }
    public void setJournalVoucherId(Long v)       { this.journalVoucherId = v; }
    public String getNotes()                      { return notes; }
    public void setNotes(String v)                { this.notes = v; }
    public Long getCreatedBy()                    { return createdBy; }
    public void setCreatedBy(Long v)              { this.createdBy = v; }
    public LocalDateTime getCreatedAt()           { return createdAt; }
    public List<InvoiceItem> getItems()           { return items; }
    public void setItems(List<InvoiceItem> v)     { this.items = v; }
}
