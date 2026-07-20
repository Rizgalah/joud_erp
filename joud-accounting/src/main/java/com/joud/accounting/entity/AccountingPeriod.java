package com.joud.accounting.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AccountingPeriod — الفترة المحاسبية
 * جدول: ACCOUNTING_PERIOD
 *
 * يتحكم في فتح وإغلاق الفترات لكل فرع.
 * يمنع تسجيل قيود في فترة مغلقة أو مقفلة.
 */
@Entity
@Table(name = "ACCOUNTING_PERIOD",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_period",
        columnNames = {"AGENT_I_ID", "PERIOD_I_YEAR", "PERIOD_I_MONTH"}
    )
)
@NamedQueries({
    @NamedQuery(name = "AccountingPeriod.findOpen",
        query = """
            SELECT p FROM AccountingPeriod p
            WHERE p.agentId = :agentId
              AND p.status = 'O'
            ORDER BY p.year DESC, p.month DESC
            """),
    @NamedQuery(name = "AccountingPeriod.findByYearMonth",
        query = """
            SELECT p FROM AccountingPeriod p
            WHERE p.agentId = :agentId
              AND p.year = :year
              AND p.month = :month
            """),
    @NamedQuery(name = "AccountingPeriod.isOpen",
        query = """
            SELECT COUNT(p) FROM AccountingPeriod p
            WHERE p.agentId = :agentId
              AND p.year = :year
              AND p.month = :month
              AND p.status = 'O'
            """),
})
public class AccountingPeriod {

    /** حالة الفترة */
    public enum PeriodStatus {
        O, // مفتوحة Open
        C, // مغلقة Closed
        L  // مقفلة Locked (بعد الإغلاق السنوي)
    }

    @Id
    @Column(name = "PERIOD_I_ID")
    private Long id;

    @Column(name = "AGENT_I_ID", nullable = false)
    private Long agentId;

    @Column(name = "PERIOD_I_YEAR", nullable = false)
    private Integer year;

    @Column(name = "PERIOD_I_MONTH", nullable = false)
    private Integer month;

    @Column(name = "PERIOD_D_FROM", nullable = false)
    private LocalDate dateFrom;

    @Column(name = "PERIOD_D_TO", nullable = false)
    private LocalDate dateTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "PERIOD_C_STS", length = 1)
    private PeriodStatus status = PeriodStatus.O;

    @Column(name = "PERIOD_D_CLOSED")
    private LocalDate dateClosed;

    @Column(name = "CLOSED_BY")
    private Long closedBy;

    @Column(name = "PERIOD_C_NOTES", length = 250)
    private String notes;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt = LocalDateTime.now();

    // ─── Helpers ───────────────────────────────────

    public boolean isOpen()   { return PeriodStatus.O == status; }
    public boolean isClosed() { return PeriodStatus.C == status; }
    public boolean isLocked() { return PeriodStatus.L == status; }

    public boolean allowsPosting() { return isOpen(); }

    public String getPeriodLabel() {
        return String.format("%04d-%02d", year, month);
    }

    // ─── Getters & Setters ─────────────────────────
    public Long getId()                        { return id; }
    public void setId(Long id)                 { this.id = id; }
    public Long getAgentId()                   { return agentId; }
    public void setAgentId(Long v)             { this.agentId = v; }
    public Integer getYear()                   { return year; }
    public void setYear(Integer v)             { this.year = v; }
    public Integer getMonth()                  { return month; }
    public void setMonth(Integer v)            { this.month = v; }
    public LocalDate getDateFrom()             { return dateFrom; }
    public void setDateFrom(LocalDate v)       { this.dateFrom = v; }
    public LocalDate getDateTo()               { return dateTo; }
    public void setDateTo(LocalDate v)         { this.dateTo = v; }
    public PeriodStatus getStatus()            { return status; }
    public void setStatus(PeriodStatus v)      { this.status = v; }
    public LocalDate getDateClosed()           { return dateClosed; }
    public void setDateClosed(LocalDate v)     { this.dateClosed = v; }
    public Long getClosedBy()                  { return closedBy; }
    public void setClosedBy(Long v)            { this.closedBy = v; }
    public String getNotes()                   { return notes; }
    public void setNotes(String v)             { this.notes = v; }
    public LocalDateTime getCreatedAt()        { return createdAt; }
}
