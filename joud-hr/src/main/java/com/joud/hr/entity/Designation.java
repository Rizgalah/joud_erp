package com.joud.hr.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

// ══════════════════════════════════════════════════════════════
// Designation — المسمى الوظيفي
// ══════════════════════════════════════════════════════════════
@Entity
@Table(name = "DESIGNATION_MAST")
public class Designation {

    @Id
    @Column(name = "DESIG_I_ID")
    private Long id;

    @Column(name = "AGENT_I_ID", nullable = false)
    private Long agentId;

    @Column(name = "DESIG_C_CODE", nullable = false, length = 20)
    private String code;

    @Column(name = "DESIG_C_NAME", nullable = false, length = 100)
    private String name;

    @Column(name = "DESIG_C_NAME_EN", length = 100)
    private String nameEn;

    @Column(name = "DEPT_I_ID")
    private Long deptId;

    @Column(name = "DESIG_C_GRADE", length = 10)
    private String grade;

    @Column(name = "DESIG_F_MIN_SAL", precision = 23, scale = 5)
    private BigDecimal minSalary = BigDecimal.ZERO;

    @Column(name = "DESIG_F_MAX_SAL", precision = 23, scale = 5)
    private BigDecimal maxSalary = BigDecimal.ZERO;

    @Column(name = "CURRENCY_I_ID")
    private Long currencyId;

    @Column(name = "DESIG_C_STATUS", length = 1)
    private String status = "A";

    @Column(name = "user_i_id")
    private Long userId;

    // Getters & Setters
    public Long getId() {
        return id;
    }

    public void setId(Long v) {
        this.id = v;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long v) {
        this.agentId = v;
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

    public String getNameEn() {
        return nameEn;
    }

    public void setNameEn(String v) {
        this.nameEn = v;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long v) {
        this.deptId = v;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String v) {
        this.grade = v;
    }

    public BigDecimal getMinSalary() {
        return minSalary;
    }

    public void setMinSalary(BigDecimal v) {
        this.minSalary = v;
    }

    public BigDecimal getMaxSalary() {
        return maxSalary;
    }

    public void setMaxSalary(BigDecimal v) {
        this.maxSalary = v;
    }

    public Long getCurrencyId() {
        return currencyId;
    }

    public void setCurrencyId(Long v) {
        this.currencyId = v;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String v) {
        this.status = v;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long v) {
        this.userId = v;
    }
}
