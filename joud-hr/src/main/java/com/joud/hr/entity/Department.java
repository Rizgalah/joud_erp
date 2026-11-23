package com.joud.hr.entity;

import jakarta.persistence.*;

// ══════════════════════════════════════════════════════════════
// Department — القسم
// ══════════════════════════════════════════════════════════════
@Entity
@Table(name = "DEPARTMENT_MAST")
public class Department {

    @Id
    @Column(name = "DEPT_I_ID")
    private Long id;

    @Column(name = "AGENT_I_ID", nullable = false)
    private Long agentId;

    @Column(name = "DEPT_C_CODE", unique = true, nullable = false, length = 20)
    private String code;

    @Column(name = "DEPT_C_NAME", nullable = false, length = 100)
    private String name;

    @Column(name = "DEPT_C_NAME_EN", length = 100)
    private String nameEn;

    @Column(name = "MANAGER_EMP_ID")
    private Long managerId;

    @Column(name = "PARENT_DEPT_ID")
    private Long parentId;

    @Column(name = "DEPT_C_STATUS", length = 1)
    private String status = "A";

    @Column(name = "DEPT_C_NOTES", length = 500)
    private String notes;

    @Column(name = "user_i_id")
    private Long userId;

    public boolean isActive() {
        return "A".equals(status);
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

    public Long getManagerId() {
        return managerId;
    }

    public void setManagerId(Long v) {
        this.managerId = v;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long v) {
        this.parentId = v;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String v) {
        this.status = v;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String v) {
        this.notes = v;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long v) {
        this.userId = v;
    }
}
