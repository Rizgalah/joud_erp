package com.joud.hr.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
@Entity
@Table(name = "salary_struct")
public class SalaryStruct {

    @Id
    @Column(name = "STRUCT_I_ID")
    private Long id;

    @Column(name = "EMP_i_id", nullable = false)
    private Long employeeId;

    @Column(name = "AGENT_I_ID", nullable = false)
    private Long agentId;

    @Column(name = "COMP_I_ID", nullable = false)
    private Long componentId;

    @Column(name = "CURRENCY_I_ID", nullable = false)
    private Long currencyId;

    @Column(name = "STRUCT_F_AMOUNT", precision = 23, scale = 5)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "STRUCT_D_FROM", nullable = false)
    private LocalDate dateFrom;

    @Column(name = "STRUCT_D_TO")
    private LocalDate dateTo;

    @Column(name = "STRUCT_C_STATUS", length = 1)
    private String status = "A";

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "user_i_id")
    private Long userId;

    // أسماء للعرض فقط — لا تُحفظ في قاعدة البيانات
    @Transient
    private String componentName;
    @Transient
    private String componentType; // E=إضافة, D=خصم
    @Transient
    private String currencySymbol;

    public boolean isActive() {
        return "A".equals(status);
    }

    public boolean isCurrent() {
        LocalDate today = LocalDate.now();
        return isActive() && !dateFrom.isAfter(today)
                && (dateTo == null || !dateTo.isBefore(today));
    }

    // Getters & Setters
    public Long getId() {
        return id;
    }

    public void setId(Long v) {
        this.id = v;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long v) {
        this.employeeId = v;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long v) {
        this.agentId = v;
    }

    public Long getComponentId() {
        return componentId;
    }

    public void setComponentId(Long v) {
        this.componentId = v;
    }

    public Long getCurrencyId() {
        return currencyId;
    }

    public void setCurrencyId(Long v) {
        this.currencyId = v;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal v) {
        this.amount = v;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public void setDateFrom(LocalDate v) {
        this.dateFrom = v;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public void setDateTo(LocalDate v) {
        this.dateTo = v;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String v) {
        this.status = v;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long v) {
        this.userId = v;
    }

    public String getComponentName() {
        return componentName;
    }

    public void setComponentName(String v) {
        this.componentName = v;
    }

    public String getComponentType() {
        return componentType;
    }

    public void setComponentType(String v) {
        this.componentType = v;
    }

    public String getCurrencySymbol() {
        return currencySymbol;
    }

    public void setCurrencySymbol(String v) {
        this.currencySymbol = v;
    }
}
