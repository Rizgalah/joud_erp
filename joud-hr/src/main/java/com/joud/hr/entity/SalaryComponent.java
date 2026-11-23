package com.joud.hr.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

// ══════════════════════════════════════════════════════════════
// SalaryComponent — مكون الراتب
// ══════════════════════════════════════════════════════════════
@Entity
@Table(name = "SALARY_COMPONENT")
public class SalaryComponent {

    public enum ComponentType {
        E, D, I
    } // Earning, Deduction, Info

    public enum CalcMethod {
        F, P, A
    } // Fixed, Percentage, Auto

    @Id
    @Column(name = "COMP_I_ID")
    private Long id;

    @Column(name = "AGENT_I_ID", nullable = false)
    private Long agentId;

    @Column(name = "COMP_C_CODE", nullable = false, length = 20)
    private String code;

    @Column(name = "COMP_C_NAME", nullable = false, length = 100)
    private String name;

    @Column(name = "COMP_C_NAME_EN", length = 100)
    private String nameEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "COMP_C_TYPE", nullable = false, length = 1)
    private ComponentType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "COMP_C_CALC", length = 1)
    private CalcMethod calcMethod = CalcMethod.F;

    @Column(name = "COMP_F_PERCENT", precision = 5, scale = 2)
    private BigDecimal percent = BigDecimal.ZERO;

    @Column(name = "COMP_C_TAXABLE", length = 1)
    private String taxable = "N";

    @Column(name = "COMP_C_STATUS", length = 1)
    private String status = "A";

    @Column(name = "COMP_I_ORDER")
    private Integer displayOrder = 10;

    public boolean isEarning() {
        return ComponentType.E == type;
    }

    public boolean isDeduction() {
        return ComponentType.D == type;
    }

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

    public ComponentType getType() {
        return type;
    }

    public void setType(ComponentType v) {
        this.type = v;
    }

    public CalcMethod getCalcMethod() {
        return calcMethod;
    }

    public void setCalcMethod(CalcMethod v) {
        this.calcMethod = v;
    }

    public BigDecimal getPercent() {
        return percent;
    }

    public void setPercent(BigDecimal v) {
        this.percent = v;
    }

    public String getTaxable() {
        return taxable;
    }

    public void setTaxable(String v) {
        this.taxable = v;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String v) {
        this.status = v;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer v) {
        this.displayOrder = v;
    }
}
