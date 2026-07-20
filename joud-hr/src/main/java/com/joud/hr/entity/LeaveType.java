package com.joud.hr.entity;

import jakarta.persistence.*;

public class LeaveType {

    @Id
    @Column(name = "LVT_I_ID")
    private Long id;

    @Column(name = "AGENT_I_ID", nullable = false)
    private Long agentId;

    @Column(name = "LVT_C_CODE", nullable = false, length = 20)
    private String code;

    @Column(name = "LVT_C_NAME", nullable = false, length = 100)
    private String name;

    @Column(name = "LVT_I_ANNUAL_DAYS")
    private Integer annualDays = 0;

    @Column(name = "LVT_C_PAID", length = 1)
    private String paid = "Y";

    @Column(name = "LVT_C_STATUS", length = 1)
    private String status = "A";

    public boolean isActive() {
        return "A".equals(status);
    }

    public boolean isPaid() {
        return "Y".equals(paid);
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

    public Integer getAnnualDays() {
        return annualDays;
    }

    public void setAnnualDays(Integer v) {
        this.annualDays = v;
    }

    public String getPaid() {
        return paid;
    }

    public void setPaid(String v) {
        this.paid = v;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String v) {
        this.status = v;
    }
}
