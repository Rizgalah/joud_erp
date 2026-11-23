package com.joud.accounting.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JournalEntry — رأس القيد اليومي (Journal Voucher)
 * جدول: JOURNAL_ENTRY
 *
 * يجمع أسطر DAY_LEDG في مجموعة واحدة متوازنة (DR = CR).
 * لا يُحفظ القيد إلا إذا كان متوازناً.
 *
 * أنواع القيود (JV_C_TYPE):
 *   JV  = قيد يدوي Journal Voucher
 *   INV = فاتورة Invoice
 *   PAY = دفعة Payment
 *   SAL = راتب Salary
 *   BNK = بنك Bank
 *   ADJ = تسوية Adjustment
 */
@Entity
@Table(name = "JOURNAL_ENTRY",
    indexes = {
        @Index(name = "idx_jv_agent_date", columnList = "AGENT_I_ID,JV_D_DATE"),
        @Index(name = "idx_jv_period",     columnList = "PERIOD_I_ID"),
        @Index(name = "idx_jv_type",       columnList = "JV_C_TYPE,JV_D_DATE"),
    }
)
@NamedQueries({
    @NamedQuery(name = "JournalEntry.findByAgent",
        query = """
            SELECT j FROM JournalEntry j
            WHERE j.agentId = :agentId
              AND j.date BETWEEN :from AND :to
            ORDER BY j.date DESC, j.id DESC
            """),
    @NamedQuery(name = "JournalEntry.findByRef",
        query = "SELECT j FROM JournalEntry j WHERE j.reference = :ref"),
    @NamedQuery(name = "JournalEntry.findPending",
        query = """
            SELECT j FROM JournalEntry j
            WHERE j.agentId = :agentId
              AND j.status = 'P'
            ORDER BY j.date ASC
            """),
})
public class JournalEntry {

    public enum JvStatus {
        O, // مسودة Draft
        P, // معلق للاعتماد Pending
        A, // معتمد Approved
        V  // ملغى Void
    }

    public enum JvType {
        JV, INV, PAY, SAL, BNK, TRS, ADJ
    }

    @Id
    @Column(name = "JV_I_ID")
    private Long id;

    /** رقم القيد — مثل JV-2026-00001 — فريد دائماً */
    @Column(name = "JV_C_REF", unique = true, nullable = false, length = 30)
    private String reference;

    @Column(name = "AGENT_I_ID", nullable = false)
    private Long agentId;

    @Column(name = "COUNTRY_C_CODE", nullable = false, length = 10)
    private String countryCode;

    @Column(name = "JV_D_DATE", nullable = false)
    private LocalDate date;

    @Column(name = "PERIOD_I_ID")
    private Long periodId;

    @Enumerated(EnumType.STRING)
    @Column(name = "JV_C_TYPE", nullable = false, length = 20)
    private JvType type = JvType.JV;

    @Column(name = "JV_C_NARR", length = 500)
    private String narration;

    /** إجمالي المدين — يجب أن يساوي إجمالي الدائن */
    @Column(name = "JV_F_TOTAL_DR", precision = 23, scale = 5)
    private BigDecimal totalDebit = BigDecimal.ZERO;

    @Column(name = "JV_F_TOTAL_CR", precision = 23, scale = 5)
    private BigDecimal totalCredit = BigDecimal.ZERO;

    @Column(name = "CURRENCY_I_ID", nullable = false)
    private Long currencyId;

    @Column(name = "EXCH_F_RATE", precision = 23, scale = 8)
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "JV_C_STS", length = 1)
    private JvStatus status = JvStatus.O;

    @Column(name = "APPROVED_BY")
    private Long approvedBy;

    @Column(name = "APPROVED_AT")
    private LocalDate approvedAt;

    @Column(name = "CREATED_BY")
    private Long createdBy;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt = LocalDateTime.now();

    /** أسطر القيد — مرتبطة بـ DAY_LEDG */
    // DayLedger.journalEntryId حقل Long عادي وليس @ManyToOne، فلا يصح mappedBy عليه.
    // نربط العمود UTI للقراءة فقط، والكتابة تتم عبر DayLedger.journalEntryId نفسه.
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "UTI", referencedColumnName = "JV_I_ID", insertable = false, updatable = false)
    @OrderBy("id ASC")
    private List<DayLedger> lines = new ArrayList<>();

    // ─── Business Logic ────────────────────────────

    /** هل القيد متوازن؟ (DR = CR) */
    public boolean isBalanced() {
        return totalDebit != null
            && totalCredit != null
            && totalDebit.compareTo(totalCredit) == 0;
    }

    /** فرق التوازن (0 = متوازن) */
    public BigDecimal getImbalance() {
        BigDecimal dr = totalDebit  != null ? totalDebit  : BigDecimal.ZERO;
        BigDecimal cr = totalCredit != null ? totalCredit : BigDecimal.ZERO;
        return dr.subtract(cr).abs();
    }

    public boolean isDraft()    { return JvStatus.O == status; }
    public boolean isPending()  { return JvStatus.P == status; }
    public boolean isApproved() { return JvStatus.A == status; }
    public boolean isVoid()     { return JvStatus.V == status; }
    public boolean canEdit()    { return isDraft(); }
    public boolean canPost()    { return isDraft() && isBalanced(); }
    public boolean canApprove() { return JvStatus.P == status; }
    public boolean canVoid()    { return JvStatus.A == status; }

    /** إضافة سطر وتحديث الإجماليات */
    public void addLine(DayLedger line) {
        lines.add(line);
        line.setJournalEntryId(this.id);
        recalcTotals();
    }

    public void recalcTotals() {
        this.totalDebit  = lines.stream()
            .filter(l -> "DR".equals(l.getDrCr()))
            .map(DayLedger::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.totalCredit = lines.stream()
            .filter(l -> "CR".equals(l.getDrCr()))
            .map(DayLedger::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ─── Getters & Setters ─────────────────────────
    public Long getId()                          { return id; }
    public void setId(Long id)                   { this.id = id; }
    public String getReference()                 { return reference; }
    public void setReference(String v)           { this.reference = v; }
    public Long getAgentId()                     { return agentId; }
    public void setAgentId(Long v)               { this.agentId = v; }
    public String getCountryCode()               { return countryCode; }
    public void setCountryCode(String v)         { this.countryCode = v; }
    public LocalDate getDate()                   { return date; }
    public void setDate(LocalDate v)             { this.date = v; }
    public Long getPeriodId()                    { return periodId; }
    public void setPeriodId(Long v)              { this.periodId = v; }
    public JvType getType()                      { return type; }
    public void setType(JvType v)                { this.type = v; }
    public String getNarration()                 { return narration; }
    public void setNarration(String v)           { this.narration = v; }
    public BigDecimal getTotalDebit()            { return totalDebit; }
    public void setTotalDebit(BigDecimal v)      { this.totalDebit = v; }
    public BigDecimal getTotalCredit()           { return totalCredit; }
    public void setTotalCredit(BigDecimal v)     { this.totalCredit = v; }
    public Long getCurrencyId()                  { return currencyId; }
    public void setCurrencyId(Long v)            { this.currencyId = v; }
    public BigDecimal getExchangeRate()          { return exchangeRate; }
    public void setExchangeRate(BigDecimal v)    { this.exchangeRate = v; }
    public JvStatus getStatus()                  { return status; }
    public void setStatus(JvStatus v)            { this.status = v; }
    public Long getApprovedBy()                  { return approvedBy; }
    public void setApprovedBy(Long v)            { this.approvedBy = v; }
    public LocalDate getApprovedAt()             { return approvedAt; }
    public void setApprovedAt(LocalDate v)       { this.approvedAt = v; }
    public Long getCreatedBy()                   { return createdBy; }
    public void setCreatedBy(Long v)             { this.createdBy = v; }
    public LocalDateTime getCreatedAt()          { return createdAt; }
    public List<DayLedger> getLines()            { return lines; }
    public void setLines(List<DayLedger> v)      { this.lines = v; }
}
