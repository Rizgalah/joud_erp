package com.joud.trade.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
@Entity
@Table(name = "payments")
public class Payment {

    public enum PaymentType {
        receipt, payment
    }

    public enum PaymentMethod2 {
        cash, bank, cheque, transfer
    }

    @Id
    @Column(name = "pay_i_id")
    private Long id;

    @Column(name = "invoice_id")
    private Long invoiceId;

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Column(name = "AGENT_I_ID", nullable = false)
    private Long agentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_c_type", nullable = false, length = 50)
    private PaymentType type;

    @Column(name = "pay_i_amount", nullable = false, precision = 23, scale = 5)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "CURRENCY_I_ID", nullable = false)
    private Long currencyId;

    @Column(name = "pay_d_DATE", nullable = false)
    private LocalDate paymentDate = LocalDate.now();

    @Column(name = "pay_c_method", length = 50)
    private String method = "cash";

    @Column(name = "pay_reference", length = 255)
    private String reference;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "created_by")
    private Long createdBy;

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

    public Long getPartyId() {
        return partyId;
    }

    public void setPartyId(Long v) {
        this.partyId = v;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long v) {
        this.agentId = v;
    }

    public PaymentType getType() {
        return type;
    }

    public void setType(PaymentType v) {
        this.type = v;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal v) {
        this.amount = v;
    }

    public Long getCurrencyId() {
        return currencyId;
    }

    public void setCurrencyId(Long v) {
        this.currencyId = v;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDate v) {
        this.paymentDate = v;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String v) {
        this.method = v;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String v) {
        this.reference = v;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String v) {
        this.notes = v;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long v) {
        this.createdBy = v;
    }
}
