package com.joud.hr.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class PayrollMast {

    public enum PayrollStatus { D, A, P, V }

    @Id @Column(name = "PAY_I_ID")
    private Long id;

    @Column(name = "AGENT_I_ID", nullable = false)
    private Long agentId;

    @Column(name = "PAY_C_DESC", nullable = false, length = 200)
    private String description;

    @Column(name = "PAY_I_YEAR", nullable = false)
    private Integer year;

    @Column(name = "PAY_I_MONTH", nullable = false)
    private Integer month;

    @Column(name = "PAY_D_FROM", nullable = false)
    private LocalDate dateFrom;

    @Column(name = "PAY_D_TO", nullable = false)
    private LocalDate dateTo;

    @Column(name = "PAY_D_PAYMENT")
    private LocalDate paymentDate;

    @Column(name = "CURRENCY_I_ID", nullable = false)
    private Long currencyId;

    @Column(name = "PAY_F_TOTAL_EARN", precision = 23, scale = 5)
    private BigDecimal totalEarnings = BigDecimal.ZERO;

    @Column(name = "PAY_F_TOTAL_DED", precision = 23, scale = 5)
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    @Column(name = "PAY_F_NET_PAY", precision = 23, scale = 5)
    private BigDecimal netPay = BigDecimal.ZERO;

    @Column(name = "PAY_I_EMP_COUNT")
    private Integer employeeCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "PAY_C_STATUS", length = 1)
    private PayrollStatus status = PayrollStatus.D;

    @Column(name = "APPROVED_BY")
    private Long approvedBy;

    @Column(name = "APPROVED_AT")
    private LocalDate approvedAt;

    @Column(name = "JV_I_ID")
    private Long journalVoucherId;

    @Column(name = "CREATED_BY")
    private Long createdBy;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "payrollId", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("employeeId ASC, componentOrder ASC")
    private List<PayrollLine> lines = new java.util.ArrayList<>();

    public boolean isDraft()    { return PayrollStatus.D == status; }
    public boolean isApproved() { return PayrollStatus.A == status; }
    public boolean isPaid()     { return PayrollStatus.P == status; }
    public boolean isVoid()     { return PayrollStatus.V == status; }
    public boolean canApprove() { return isDraft(); }
    public boolean canPay()     { return isApproved(); }

    public String getPeriodLabel() {
        return String.format("%04d/%02d", year, month);
    }

    // Getters & Setters
    public Long getId()                           { return id; }
    public void setId(Long v)                     { this.id = v; }
    public Long getAgentId()                      { return agentId; }
    public void setAgentId(Long v)                { this.agentId = v; }
    public String getDescription()                { return description; }
    public void setDescription(String v)          { this.description = v; }
    public Integer getYear()                      { return year; }
    public void setYear(Integer v)                { this.year = v; }
    public Integer getMonth()                     { return month; }
    public void setMonth(Integer v)               { this.month = v; }
    public LocalDate getDateFrom()                { return dateFrom; }
    public void setDateFrom(LocalDate v)          { this.dateFrom = v; }
    public LocalDate getDateTo()                  { return dateTo; }
    public void setDateTo(LocalDate v)            { this.dateTo = v; }
    public LocalDate getPaymentDate()             { return paymentDate; }
    public void setPaymentDate(LocalDate v)       { this.paymentDate = v; }
    public Long getCurrencyId()                   { return currencyId; }
    public void setCurrencyId(Long v)             { this.currencyId = v; }
    public BigDecimal getTotalEarnings()          { return totalEarnings; }
    public void setTotalEarnings(BigDecimal v)    { this.totalEarnings = v; }
    public BigDecimal getTotalDeductions()        { return totalDeductions; }
    public void setTotalDeductions(BigDecimal v)  { this.totalDeductions = v; }
    public BigDecimal getNetPay()                 { return netPay; }
    public void setNetPay(BigDecimal v)           { this.netPay = v; }
    public Integer getEmployeeCount()             { return employeeCount; }
    public void setEmployeeCount(Integer v)       { this.employeeCount = v; }
    public PayrollStatus getStatus()              { return status; }
    public void setStatus(PayrollStatus v)        { this.status = v; }
    public Long getApprovedBy()                   { return approvedBy; }
    public void setApprovedBy(Long v)             { this.approvedBy = v; }
    public LocalDate getApprovedAt()              { return approvedAt; }
    public void setApprovedAt(LocalDate v)        { this.approvedAt = v; }
    public Long getJournalVoucherId()             { return journalVoucherId; }
    public void setJournalVoucherId(Long v)       { this.journalVoucherId = v; }
    public Long getCreatedBy()                    { return createdBy; }
    public void setCreatedBy(Long v)              { this.createdBy = v; }
    public LocalDateTime getCreatedAt()           { return createdAt; }
    public List<PayrollLine> getLines()           { return lines; }
    public void setLines(List<PayrollLine> v)     { this.lines = v; }
}
