package com.joud.trade.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public class Party {

    public enum PartyType {
        customer, supplier, both
    }

    public enum CreditStatus {
        A, H, B
    } // Active, Hold, Blocked

    @Id
    @Column(name = "parties_i_id")
    private Long id;

    @Column(name = "parties_c_code", unique = true, nullable = false, length = 100)
    private String code;

    @Column(name = "parties_c_name", nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "parties_c_type", nullable = false, length = 20)
    private PartyType type;

    @Column(name = "AGENT_I_ID", nullable = false)
    private Long agentId;

    @Column(name = "parties_c_address", length = 500)
    private String address;

    @Column(name = "parties_c_phone", length = 50)
    private String phone;

    @Column(name = "parties_c_email", length = 100)
    private String email;

    @Column(name = "parties_c_tax_number", length = 100)
    private String taxNumber;

    @Column(name = "ACT_I_ID")
    private Long accountId;

    @Column(name = "parties_f_credit_limit", precision = 23, scale = 5)
    private BigDecimal creditLimit = BigDecimal.ZERO;

    @Column(name = "parties_i_pay_terms")
    private Integer paymentTermsDays = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "parties_c_credit_sts", length = 1)
    private CreditStatus creditStatus = CreditStatus.A;

    @Column(name = "parties_f_tax_rate", precision = 5, scale = 2)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "parties_c_region", length = 100)
    private String region;

    @Column(name = "parties_c_notes")
    private String notes;

    @Column(name = "is_active")
    private Integer active = 1;

    @Column(name = "parties_d_created")
    private LocalDate createdAt = LocalDate.now();

    @Column(name = "user_i_id")
    private Long userId;

    // ─── Helpers ───────────────────────────────────
    public boolean isCustomer() {
        return PartyType.customer == type || PartyType.both == type;
    }

    public boolean isSupplier() {
        return PartyType.supplier == type || PartyType.both == type;
    }

    public boolean isActive() {
        return active != null && active == 1;
    }

    public boolean isCreditOk() {
        return CreditStatus.A == creditStatus;
    }

    public String getTypeLabel() {
        if (type == null)
            return "—";
        return switch (type) {
            case customer -> "عميل";
            case supplier -> "مورد";
            case both -> "عميل / مورد";
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

    public PartyType getType() {
        return type;
    }

    public void setType(PartyType v) {
        this.type = v;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long v) {
        this.agentId = v;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String v) {
        this.address = v;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String v) {
        this.phone = v;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String v) {
        this.email = v;
    }

    public String getTaxNumber() {
        return taxNumber;
    }

    public void setTaxNumber(String v) {
        this.taxNumber = v;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long v) {
        this.accountId = v;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public void setCreditLimit(BigDecimal v) {
        this.creditLimit = v;
    }

    public Integer getPaymentTermsDays() {
        return paymentTermsDays;
    }

    public void setPaymentTermsDays(Integer v) {
        this.paymentTermsDays = v;
    }

    public CreditStatus getCreditStatus() {
        return creditStatus;
    }

    public void setCreditStatus(CreditStatus v) {
        this.creditStatus = v;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(BigDecimal v) {
        this.taxRate = v;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String v) {
        this.region = v;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String v) {
        this.notes = v;
    }

    public Integer getActive() {
        return active;
    }

    public void setActive(Integer v) {
        this.active = v;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long v) {
        this.userId = v;
    }
}
