package com.joud.accounting.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DayLedger — سطر القيد اليومي
 * جدول: DAY_LEDG
 *
 * كل سطر يمثل حركة مدين أو دائن في حساب معين.
 * مجموعة الأسطر المرتبطة بـ JOURNAL_ENTRY يجب أن تكون متوازنة.
 *
 * يحمل كل سطر:
 *   - عملة الحركة
 *   - سعر الصرف وقت التنفيذ
 *   - المبلغ بعملة الحساب
 *   - المبلغ بالعملة الأساسية (للتجميع)
 *   - كود البلد (لتسريع التقارير)
 */
@Entity
@Table(name = "DAY_LEDG",
    indexes = {
        @Index(name = "idx_dayldg_agent_date", columnList = "AGENT_I_ID,TRN_D_DATE"),
        @Index(name = "idx_dayldg_country",    columnList = "COUNTRY_C_CODE,TRN_D_DATE"),
        @Index(name = "idx_dayldg_act",        columnList = "ACT_C_TYPE,TRN_D_DATE"),
        @Index(name = "idx_dayldg_currency",   columnList = "CURRENCY_I_ID,TRN_D_DATE"),
    }
)
@NamedQueries({
    @NamedQuery(name = "DayLedger.findByAccountAgent",
        query = """
            SELECT d FROM DayLedger d
            WHERE d.accountType = :accountType
              AND d.agentId     = :agentId
              AND d.date BETWEEN :from AND :to
            ORDER BY d.date ASC, d.id ASC
            """),
    @NamedQuery(name = "DayLedger.findByJournalEntry",
        query = """
            SELECT d FROM DayLedger d
            WHERE d.journalEntryId = :jvId
            ORDER BY d.id ASC
            """),
    @NamedQuery(name = "DayLedger.sumByAccountPeriod",
        query = """
            SELECT
              SUM(CASE WHEN d.drCr = 'DR' THEN d.amount ELSE 0 END),
              SUM(CASE WHEN d.drCr = 'CR' THEN d.amount ELSE 0 END)
            FROM DayLedger d
            WHERE d.accountType = :accountType
              AND d.agentId     = :agentId
              AND YEAR(d.date)  = :year
              AND MONTH(d.date) = :month
            """),
})
public class DayLedger {

    public enum TrnStatus {
        P, // معلق Pending
        A, // معتمد Approved
        V  // ملغى Void
    }

    @Id
    @Column(name = "TRN_I_NO")
    private Long id;

    @Column(name = "AGENT_I_ID", nullable = false)
    private Long agentId;

    /** مشتق من AGENT_MAST — مُخزَّن لتسريع التقارير */
    @Column(name = "COUNTRY_C_CODE", nullable = false, length = 10)
    private String countryCode;

    @Column(name = "TRN_D_DATE", nullable = false)
    private LocalDate date;

    @Column(name = "ACT_C_TYPE", nullable = false, length = 10)
    private String accountType;

    @Column(name = "CUST_I_NO")
    private Long customerId;

    @Column(name = "TRN_C_NARR", length = 250)
    private String narration;

    // ─── العملة والمبلغ ────────────────────────────

    @Column(name = "CURRENCY_I_ID", nullable = false)
    private Long currencyId;

    /** المبلغ بعملة الحساب */
    @Column(name = "TRN_F_TRNAMT", nullable = false, precision = 23, scale = 5)
    private BigDecimal amount;

    /** DR = مدين، CR = دائن */
    @Column(name = "TRN_C_DRCR", nullable = false, length = 2)
    private String drCr;

    /** سعر الصرف وقت تسجيل الحركة */
    @Column(name = "EXCH_F_RATE", precision = 23, scale = 8)
    private BigDecimal exchangeRate = BigDecimal.ONE;

    /** المبلغ بالعملة الأساسية للشركة (للتجميع) */
    @Column(name = "TRN_F_AMT_BASE", precision = 23, scale = 5)
    private BigDecimal amountBase = BigDecimal.ZERO;

    // ─── ربط بالقيد الرئيسي ────────────────────────

    @Column(name = "GEN_I_ID")
    private Long generalLedgerId;

    /** رقم القيد الرئيسي (JOURNAL_ENTRY) */
    @Column(name = "UTI", nullable = false)
    private Long journalEntryId = 0L;

    // ─── معلومات إضافية ────────────────────────────

    @Column(name = "TRT_C_TYPE", length = 10)
    private String transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "TRN_C_STS", length = 1)
    private TrnStatus status = TrnStatus.P;

    @Column(name = "SEQ_I_CODE")
    private Double sequenceCode;

    @Column(name = "TRN_C_CHQNO", length = 10)
    private String chequeNumber;

    @Column(name = "TRN_D_CHQDATE")
    private LocalDate chequeDate;

    @Column(name = "TRN_C_USRID")
    private Long userId;

    /** الرصيد التراكمي بعد هذه الحركة */
    @Column(name = "TRN_F_BALANCE", precision = 20, scale = 2)
    private BigDecimal runningBalance;

    @Column(name = "TRN_C_BALSTS", length = 2)
    private String balanceStatus;

    @Column(name = "TRN_C_TOTYPE", length = 10)
    private String toType;

    @Column(name = "TRN_I_TONO")
    private Double toNumber;

    @Column(name = "SCREEN_NAME", nullable = false, length = 40)
    private String screenName = "No Screen Name";

    // ─── Helpers ───────────────────────────────────

    /** هل الحركة مدين؟ */
    public boolean isDebit()  { return "DR".equalsIgnoreCase(drCr); }

    /** هل الحركة دائن؟ */
    public boolean isCredit() { return "CR".equalsIgnoreCase(drCr); }

    /**
     * احسب المبلغ بالعملة الأساسية تلقائياً
     */
    public void calculateBaseAmount() {
        if (amount != null && exchangeRate != null && exchangeRate.compareTo(BigDecimal.ZERO) > 0) {
            this.amountBase = amount.multiply(exchangeRate)
                                    .setScale(5, java.math.RoundingMode.HALF_UP);
        }
    }

    // ─── Getters & Setters ─────────────────────────
    public Long getId()                           { return id; }
    public void setId(Long id)                    { this.id = id; }
    public Long getAgentId()                      { return agentId; }
    public void setAgentId(Long v)                { this.agentId = v; }
    public String getCountryCode()                { return countryCode; }
    public void setCountryCode(String v)          { this.countryCode = v; }
    public LocalDate getDate()                    { return date; }
    public void setDate(LocalDate v)              { this.date = v; }
    public String getAccountType()                { return accountType; }
    public void setAccountType(String v)          { this.accountType = v; }
    public Long getCustomerId()                   { return customerId; }
    public void setCustomerId(Long v)             { this.customerId = v; }
    public String getNarration()                  { return narration; }
    public void setNarration(String v)            { this.narration = v; }
    public Long getCurrencyId()                   { return currencyId; }
    public void setCurrencyId(Long v)             { this.currencyId = v; }
    public BigDecimal getAmount()                 { return amount; }
    public void setAmount(BigDecimal v)           { this.amount = v; }
    public String getDrCr()                       { return drCr; }
    public void setDrCr(String v)                 { this.drCr = v; }
    public BigDecimal getExchangeRate()           { return exchangeRate; }
    public void setExchangeRate(BigDecimal v)     { this.exchangeRate = v; }
    public BigDecimal getAmountBase()             { return amountBase; }
    public void setAmountBase(BigDecimal v)       { this.amountBase = v; }
    public Long getGeneralLedgerId()              { return generalLedgerId; }
    public void setGeneralLedgerId(Long v)        { this.generalLedgerId = v; }
    public Long getJournalEntryId()               { return journalEntryId; }
    public void setJournalEntryId(Long v)         { this.journalEntryId = v; }
    public String getTransactionType()            { return transactionType; }
    public void setTransactionType(String v)      { this.transactionType = v; }
    public TrnStatus getStatus()                  { return status; }
    public void setStatus(TrnStatus v)            { this.status = v; }
    public Double getSequenceCode()               { return sequenceCode; }
    public void setSequenceCode(Double v)         { this.sequenceCode = v; }
    public String getChequeNumber()               { return chequeNumber; }
    public void setChequeNumber(String v)         { this.chequeNumber = v; }
    public LocalDate getChequeDate()              { return chequeDate; }
    public void setChequeDate(LocalDate v)        { this.chequeDate = v; }
    public Long getUserId()                       { return userId; }
    public void setUserId(Long v)                 { this.userId = v; }
    public BigDecimal getRunningBalance()         { return runningBalance; }
    public void setRunningBalance(BigDecimal v)   { this.runningBalance = v; }
    public String getBalanceStatus()              { return balanceStatus; }
    public void setBalanceStatus(String v)        { this.balanceStatus = v; }
    public String getToType()                     { return toType; }
    public void setToType(String v)               { this.toType = v; }
    public Double getToNumber()                   { return toNumber; }
    public void setToNumber(Double v)             { this.toNumber = v; }
    public String getScreenName()                 { return screenName; }
    public void setScreenName(String v)           { this.screenName = v; }
}
