package com.joud.hr.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity
@Table(name = "payroll_line")
public class PayrollLine {

    @Id
    @Column(name = "LINE_I_ID")
    private Long id;

    @Column(name = "PAY_I_ID", nullable = false)
    private Long payrollId;

    @Column(name = "EMP_i_id", nullable = false)
    private Long employeeId;

    @Column(name = "COMP_I_ID", nullable = false)
    private Long componentId;

    @Column(name = "LINE_C_TYPE", nullable = false, length = 1)
    private String type; // E=إضافة, D=خصم

    @Column(name = "LINE_F_AMOUNT", precision = 23, scale = 5)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "LINE_F_DAYS", precision = 5, scale = 2)
    private BigDecimal workDays = new BigDecimal("30");

    @Column(name = "LINE_C_NOTES", length = 250)
    private String notes;

    @Column(name = "COMP_I_ORDER")
    private Integer componentOrder = 10;

    public boolean isEarning() {
        return "E".equals(type);
    }

    public boolean isDeduction() {
        return "D".equals(type);
    }

    // Getters & Setters
    public Long getId() {
        return id;
    }

    public void setId(Long v) {
        this.id = v;
    }

    public Long getPayrollId() {
        return payrollId;
    }

    public void setPayrollId(Long v) {
        this.payrollId = v;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long v) {
        this.employeeId = v;
    }

    public Long getComponentId() {
        return componentId;
    }

    public void setComponentId(Long v) {
        this.componentId = v;
    }

    public String getType() {
        return type;
    }

    public void setType(String v) {
        this.type = v;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal v) {
        this.amount = v;
    }

    public BigDecimal getWorkDays() {
        return workDays;
    }

    public void setWorkDays(BigDecimal v) {
        this.workDays = v;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String v) {
        this.notes = v;
    }

    public Integer getComponentOrder() {
        return componentOrder;
    }

    public void setComponentOrder(Integer v) {
        this.componentOrder = v;
    }
}
