package com.joud.hr.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
@Entity
@Table(name = "leave_request")
public class LeaveRequest {

    public enum LeaveStatus {
        P, A, R, C
    } // معلق, موافق, مرفوض, ملغى

    @Id
    @Column(name = "LVR_I_ID")
    private Long id;

    @Column(name = "EMP_i_id", nullable = false)
    private Long employeeId;

    @Column(name = "AGENT_I_ID", nullable = false)
    private Long agentId;

    @Column(name = "LVT_I_ID", nullable = false)
    private Long leaveTypeId;

    @Column(name = "LVR_D_FROM", nullable = false)
    private LocalDate dateFrom;

    @Column(name = "LVR_D_TO", nullable = false)
    private LocalDate dateTo;

    @Column(name = "LVR_I_DAYS", nullable = false, precision = 5, scale = 2)
    private BigDecimal days;

    @Column(name = "LVR_C_REASON", length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "LVR_C_STATUS", length = 1)
    private LeaveStatus status = LeaveStatus.P;

    @Column(name = "APPROVED_BY")
    private Long approvedBy;

    @Column(name = "APPROVED_AT")
    private LocalDate approvedAt;

    @Column(name = "LVR_C_NOTES", length = 500)
    private String notes;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Transient
    private String employeeName;
    @Transient
    private String leaveTypeName;

    public boolean isPending() {
        return LeaveStatus.P == status;
    }

    public boolean isApproved() {
        return LeaveStatus.A == status;
    }

    public boolean isRejected() {
        return LeaveStatus.R == status;
    }

    public boolean canCancel() {
        return isPending() || isApproved();
    }

    public String getStatusLabel() {
        if (status == null)
            return "—";
        return switch (status) {
            case P -> "معلق";
            case A -> "موافق";
            case R -> "مرفوض";
            case C -> "ملغى";
        };
    }

    /** يحسب عدد الأيام شاملاً اليومين (من وإلى) */
    public void calculateDays() {
        if (dateFrom != null && dateTo != null && !dateTo.isBefore(dateFrom)) {
            this.days = new BigDecimal(
                    java.time.temporal.ChronoUnit.DAYS.between(dateFrom, dateTo) + 1);
        }
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

    public Long getLeaveTypeId() {
        return leaveTypeId;
    }

    public void setLeaveTypeId(Long v) {
        this.leaveTypeId = v;
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

    public BigDecimal getDays() {
        return days;
    }

    public void setDays(BigDecimal v) {
        this.days = v;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String v) {
        this.reason = v;
    }

    public LeaveStatus getStatus() {
        return status;
    }

    public void setStatus(LeaveStatus v) {
        this.status = v;
    }

    public Long getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(Long v) {
        this.approvedBy = v;
    }

    public LocalDate getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDate v) {
        this.approvedAt = v;
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

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String v) {
        this.employeeName = v;
    }

    public String getLeaveTypeName() {
        return leaveTypeName;
    }

    public void setLeaveTypeName(String v) {
        this.leaveTypeName = v;
    }
}
