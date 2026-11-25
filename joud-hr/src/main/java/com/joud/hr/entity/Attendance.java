package com.joud.hr.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
@Entity
@Table(name = "attendance_mast")
public class Attendance {

    public enum AttStatus {
        P, A, L, H, E
    } // حاضر, غائب, إجازة, عطلة, مأمورية

    @Id
    @Column(name = "ATT_I_ID")
    private Long id;

    @Column(name = "EMP_i_id", nullable = false)
    private Long employeeId;

    @Column(name = "AGENT_I_ID", nullable = false)
    private Long agentId;

    @Column(name = "ATT_D_DATE", nullable = false)
    private LocalDate attDate;

    @Column(name = "ATT_T_IN")
    private LocalDateTime checkIn;

    @Column(name = "ATT_T_OUT")
    private LocalDateTime checkOut;

    @Column(name = "ATT_F_HOURS", precision = 5, scale = 2)
    private BigDecimal hours = BigDecimal.ZERO;

    @Column(name = "ATT_F_OT_HOURS", precision = 5, scale = 2)
    private BigDecimal otHours = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "ATT_C_STATUS", length = 1)
    private AttStatus status = AttStatus.P;

    @Column(name = "ATT_C_NOTES", length = 250)
    private String notes;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Transient
    private String employeeName;

    public boolean isPresent() {
        return AttStatus.P == status;
    }

    /** يحسب ساعات العمل من وقتي الحضور والانصراف (إن وُجدا) */
    public void calculateHours() {
        if (checkIn != null && checkOut != null && checkOut.isAfter(checkIn)) {
            long minutes = java.time.Duration.between(checkIn, checkOut).toMinutes();
            this.hours = new BigDecimal(minutes).divide(new BigDecimal(60), 2, java.math.RoundingMode.HALF_UP);
            // ساعات إضافية بعد 8 ساعات نظامية
            BigDecimal regular = new BigDecimal("8");
            this.otHours = hours.compareTo(regular) > 0
                    ? hours.subtract(regular)
                    : BigDecimal.ZERO;
        }
    }

    public String getStatusLabel() {
        if (status == null)
            return "—";
        return switch (status) {
            case P -> "حاضر";
            case A -> "غائب";
            case L -> "إجازة";
            case H -> "عطلة";
            case E -> "مأمورية";
        };
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

    public LocalDate getAttDate() {
        return attDate;
    }

    public void setAttDate(LocalDate v) {
        this.attDate = v;
    }

    public LocalDateTime getCheckIn() {
        return checkIn;
    }

    public void setCheckIn(LocalDateTime v) {
        this.checkIn = v;
    }

    public LocalDateTime getCheckOut() {
        return checkOut;
    }

    public void setCheckOut(LocalDateTime v) {
        this.checkOut = v;
    }

    public BigDecimal getHours() {
        return hours;
    }

    public void setHours(BigDecimal v) {
        this.hours = v;
    }

    public BigDecimal getOtHours() {
        return otHours;
    }

    public void setOtHours(BigDecimal v) {
        this.otHours = v;
    }

    public AttStatus getStatus() {
        return status;
    }

    public void setStatus(AttStatus v) {
        this.status = v;
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
}
