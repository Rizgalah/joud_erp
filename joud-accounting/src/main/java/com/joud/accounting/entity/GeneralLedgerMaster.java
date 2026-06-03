package com.joud.accounting.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * GeneralLedgerMaster — الأرصدة التراكمية
 * جدول: GENLDG_MAST
 *
 * القلب الفعلي لاستخراج التقارير الختامية.
 * يُحدَّث تلقائياً عند كل قيد في DAY_LEDG أو BATCH.
 *
 * يحتوي على:
 *   - رصيد الافتتاح  (من إغلاق الفترة السابقة)
 *   - حركة الفترة    (DR + CR)
 *   - رصيد الإغلاق   (بعملة الحساب)
 *   - كل ما سبق بالعملة الأساسية للشركة (للتجميع)
 *
 * مستويات التجميع:
 *   - مستوى الفرع:   GROUP BY AGENT_I_ID
 *   - مستوى البلد:   GROUP BY COUNTRY_C_CODE
 *   - مستوى الشركة:  بدون GROUP BY (كل السجلات)
 */
@Entity
@Table(name = "GENLDG_MAST",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_genldg",
        columnNames = {"ACT_I_ID","AGENT_I_ID","GEN_I_YEAR","GEN_I_MONTH","CURRENCY_I_ID"}
    ),
    indexes = {
        @Index(name = "idx_gen_agent_period", columnList = "AGENT_I_ID,GEN_I_YEAR,GEN_I_MONTH"),
        @Index(name = "idx_gen_country",       columnList = "COUNTRY_C_CODE,GEN_I_YEAR,GEN_I_MONTH"),
        @Index(name = "idx_gen_act_period",    columnList = "ACT_I_ID,GEN_I_YEAR,GEN_I_MONTH"),
        @Index(name = "idx_gen_period",        columnList = "GEN_I_YEAR,GEN_I_MONTH"),
    }
)
@NamedQueries({
    // ─── استخراج ميزان المراجعة — فرع واحد ─────────────
    @NamedQuery(name = "GenLdg.trialBalanceByAgent",
        query = """
            SELECT g FROM GeneralLedgerMaster g
            WHERE g.agentId = :agentId
              AND g.year    = :year
              AND g.month   = :month
            ORDER BY g.accountType
            """),
    // ─── استخراج ميزان المراجعة — مستوى البلد ──────────
    @NamedQuery(name = "GenLdg.trialBalanceByCountry",
        query = """
            SELECT g FROM GeneralLedgerMaster g
            WHERE g.countryCode = :countryCode
              AND g.year        = :year
              AND g.month       = :month
            ORDER BY g.accountType
            """),
    // ─── جلب سجل فترة محددة لحساب معين ─────────────────
    @NamedQuery(name = "GenLdg.findByAccountPeriodAgent",
        query = """
            SELECT g FROM GeneralLedgerMaster g
            WHERE g.accountId  = :accountId
              AND g.agentId    = :agentId
              AND g.year       = :year
              AND g.month      = :month
              AND g.currencyId = :currencyId
            """),
    // ─── رصيد إغلاق الفترة السابقة (لفتح فترة جديدة) ───
    @NamedQuery(name = "GenLdg.lastClosingBalance",
        query = """
            SELECT g FROM GeneralLedgerMaster g
            WHERE g.accountId  = :accountId
              AND g.agentId    = :agentId
              AND g.currencyId = :currencyId
              AND (g.year < :year OR (g.year = :year AND g.month < :month))
            ORDER BY g.year DESC, g.month DESC
            """),
})
public class GeneralLedgerMaster {

    /** حالة الفترة */
    public enum PeriodStatus { O, C, L }

    /** اتجاه الرصيد */
    public enum DrCr { D, C }

    @Id
    @Column(name = "GEN_I_ID")
    private Long id;

    // ─── الحساب ────────────────────────────────────
    @Column(name = "ACT_I_ID", nullable = false)
    private Long accountId;

    @Column(name = "ACT_C_TYPE", nullable = false, length = 10)
    private String accountType;

    // ─── الفرع والبلد ──────────────────────────────
    @Column(name = "AGENT_I_ID", nullable = false)
    private Long agentId;

    @Column(name = "COUNTRY_C_CODE", nullable = false, length = 10)
    private String countryCode;

    // ─── الفترة المحاسبية ──────────────────────────
    @Column(name = "GEN_I_YEAR", nullable = false)
    private Integer year;

    /** الشهر: 0 = رصيد افتتاح السنة، 1-12 = الشهر العادي */
    @Column(name = "GEN_I_MONTH", nullable = false)
    private Integer month;

    // ─── العملات ───────────────────────────────────
    @Column(name = "CURRENCY_I_ID", nullable = false)
    private Long currencyId;

    @Column(name = "BASE_CURRENCY_I_ID")
    private Long baseCurrencyId;

    // ─── أرصدة بعملة الحساب ────────────────────────

    @Column(name = "GEN_F_OP_BAL", precision = 23, scale = 5)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "GEN_C_OP_DRCR", length = 1)
    private DrCr openingDrCr = DrCr.D;

    @Column(name = "GEN_F_DR_AMT", precision = 23, scale = 5)
    private BigDecimal totalDebit = BigDecimal.ZERO;

    @Column(name = "GEN_F_CR_AMT", precision = 23, scale = 5)
    private BigDecimal totalCredit = BigDecimal.ZERO;

    @Column(name = "GEN_F_CL_BAL", precision = 23, scale = 5)
    private BigDecimal closingBalance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "GEN_C_CL_DRCR", length = 1)
    private DrCr closingDrCr = DrCr.D;

    // ─── أرصدة بالعملة الأساسية (للتجميع) ─────────

    @Column(name = "GEN_F_OP_BAL_BASE", precision = 23, scale = 5)
    private BigDecimal openingBalanceBase = BigDecimal.ZERO;

    @Column(name = "GEN_F_DR_AMT_BASE", precision = 23, scale = 5)
    private BigDecimal totalDebitBase = BigDecimal.ZERO;

    @Column(name = "GEN_F_CR_AMT_BASE", precision = 23, scale = 5)
    private BigDecimal totalCreditBase = BigDecimal.ZERO;

    @Column(name = "GEN_F_CL_BAL_BASE", precision = 23, scale = 5)
    private BigDecimal closingBalanceBase = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "GEN_C_CL_DRCR_BASE", length = 1)
    private DrCr closingDrCrBase = DrCr.D;

    @Column(name = "GEN_F_EXCH_RATE", precision = 23, scale = 8)
    private BigDecimal exchangeRate = BigDecimal.ONE;

    /** فروق إعادة التقييم (Forex Revaluation) */
    @Column(name = "GEN_F_FOREX_DIFF", precision = 23, scale = 5)
    private BigDecimal forexDiff = BigDecimal.ZERO;

    // ─── معلومات التدقيق ───────────────────────────

    @Column(name = "GEN_I_TRN_COUNT")
    private Integer transactionCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "GEN_C_PERIOD_STS", length = 1)
    private PeriodStatus periodStatus = PeriodStatus.O;

    @Column(name = "GEN_D_LAST_UPDATE")
    private LocalDate lastUpdate;

    @Column(name = "user_i_id")
    private Long userId;

    // ─── Business Logic ────────────────────────────

    /**
     * تحديث أرصدة الفترة عند إضافة حركة جديدة
     * يُستدعى من GeneralLedgerService عند ترحيل قيد
     *
     * @param amount    مبلغ الحركة بعملة الحساب
     * @param amountBase مبلغ الحركة بالعملة الأساسية
     * @param drCr      DR أو CR
     */
    public void postTransaction(BigDecimal amount, BigDecimal amountBase, DrCr drCr) {
        if (DrCr.D == drCr) {
            this.totalDebit     = safeAdd(this.totalDebit, amount);
            this.totalDebitBase = safeAdd(this.totalDebitBase, amountBase);
        } else {
            this.totalCredit     = safeAdd(this.totalCredit, amount);
            this.totalCreditBase = safeAdd(this.totalCreditBase, amountBase);
        }
        this.transactionCount++;
        this.lastUpdate = LocalDate.now();
        recalculateClosingBalance();
    }

    /**
     * عكس حركة (في حالة الإلغاء)
     */
    public void reverseTransaction(BigDecimal amount, BigDecimal amountBase, DrCr drCr) {
        if (DrCr.D == drCr) {
            this.totalDebit     = safeSub(this.totalDebit, amount);
            this.totalDebitBase = safeSub(this.totalDebitBase, amountBase);
        } else {
            this.totalCredit     = safeSub(this.totalCredit, amount);
            this.totalCreditBase = safeSub(this.totalCreditBase, amountBase);
        }
        this.transactionCount = Math.max(0, this.transactionCount - 1);
        this.lastUpdate = LocalDate.now();
        recalculateClosingBalance();
    }

    /**
     * حساب رصيد الإغلاق:
     *   إذا كان رصيد الافتتاح مدين:  إغلاق = افتتاح + DR - CR
     *   إذا كان رصيد الافتتاح دائن:  إغلاق = افتتاح + CR - DR
     */
    private void recalculateClosingBalance() {
        // بعملة الحساب
        BigDecimal netDr  = safeAdd(openingDrCr == DrCr.D ? openingBalance : BigDecimal.ZERO, totalDebit);
        BigDecimal netCr  = safeAdd(openingDrCr == DrCr.C ? openingBalance : BigDecimal.ZERO, totalCredit);
        BigDecimal net    = netDr.subtract(netCr);

        if (net.compareTo(BigDecimal.ZERO) >= 0) {
            this.closingBalance = net;
            this.closingDrCr    = DrCr.D;
        } else {
            this.closingBalance = net.negate();
            this.closingDrCr    = DrCr.C;
        }

        // بالعملة الأساسية
        BigDecimal netDrBase = safeAdd(openingDrCr == DrCr.D ? openingBalanceBase : BigDecimal.ZERO, totalDebitBase);
        BigDecimal netCrBase = safeAdd(openingDrCr == DrCr.C ? openingBalanceBase : BigDecimal.ZERO, totalCreditBase);
        BigDecimal netBase   = netDrBase.subtract(netCrBase);

        if (netBase.compareTo(BigDecimal.ZERO) >= 0) {
            this.closingBalanceBase = netBase;
            this.closingDrCrBase    = DrCr.D;
        } else {
            this.closingBalanceBase = netBase.negate();
            this.closingDrCrBase    = DrCr.C;
        }
    }

    /** الرصيد الصافي بإشارة (+مدين / -دائن) للعمليات الحسابية */
    public BigDecimal getNetBalance() {
        BigDecimal bal = closingBalance != null ? closingBalance : BigDecimal.ZERO;
        return DrCr.D == closingDrCr ? bal : bal.negate();
    }

    public BigDecimal getNetBalanceBase() {
        BigDecimal bal = closingBalanceBase != null ? closingBalanceBase : BigDecimal.ZERO;
        return DrCr.D == closingDrCrBase ? bal : bal.negate();
    }

    private BigDecimal safeAdd(BigDecimal a, BigDecimal b) {
        return (a == null ? BigDecimal.ZERO : a)
             .add(b == null ? BigDecimal.ZERO : b)
             .setScale(5, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal safeSub(BigDecimal a, BigDecimal b) {
        return (a == null ? BigDecimal.ZERO : a)
             .subtract(b == null ? BigDecimal.ZERO : b)
             .setScale(5, java.math.RoundingMode.HALF_UP);
    }

    // ─── Getters & Setters ─────────────────────────
    public Long getId()                               { return id; }
    public void setId(Long id)                        { this.id = id; }
    public Long getAccountId()                        { return accountId; }
    public void setAccountId(Long v)                  { this.accountId = v; }
    public String getAccountType()                    { return accountType; }
    public void setAccountType(String v)              { this.accountType = v; }
    public Long getAgentId()                          { return agentId; }
    public void setAgentId(Long v)                    { this.agentId = v; }
    public String getCountryCode()                    { return countryCode; }
    public void setCountryCode(String v)              { this.countryCode = v; }
    public Integer getYear()                          { return year; }
    public void setYear(Integer v)                    { this.year = v; }
    public Integer getMonth()                         { return month; }
    public void setMonth(Integer v)                   { this.month = v; }
    public Long getCurrencyId()                       { return currencyId; }
    public void setCurrencyId(Long v)                 { this.currencyId = v; }
    public Long getBaseCurrencyId()                   { return baseCurrencyId; }
    public void setBaseCurrencyId(Long v)             { this.baseCurrencyId = v; }
    public BigDecimal getOpeningBalance()             { return openingBalance; }
    public void setOpeningBalance(BigDecimal v)       { this.openingBalance = v; }
    public DrCr getOpeningDrCr()                      { return openingDrCr; }
    public void setOpeningDrCr(DrCr v)                { this.openingDrCr = v; }
    public BigDecimal getTotalDebit()                 { return totalDebit; }
    public void setTotalDebit(BigDecimal v)           { this.totalDebit = v; }
    public BigDecimal getTotalCredit()                { return totalCredit; }
    public void setTotalCredit(BigDecimal v)          { this.totalCredit = v; }
    public BigDecimal getClosingBalance()             { return closingBalance; }
    public void setClosingBalance(BigDecimal v)       { this.closingBalance = v; }
    public DrCr getClosingDrCr()                      { return closingDrCr; }
    public void setClosingDrCr(DrCr v)                { this.closingDrCr = v; }
    public BigDecimal getOpeningBalanceBase()         { return openingBalanceBase; }
    public void setOpeningBalanceBase(BigDecimal v)   { this.openingBalanceBase = v; }
    public BigDecimal getTotalDebitBase()             { return totalDebitBase; }
    public void setTotalDebitBase(BigDecimal v)       { this.totalDebitBase = v; }
    public BigDecimal getTotalCreditBase()            { return totalCreditBase; }
    public void setTotalCreditBase(BigDecimal v)      { this.totalCreditBase = v; }
    public BigDecimal getClosingBalanceBase()         { return closingBalanceBase; }
    public void setClosingBalanceBase(BigDecimal v)   { this.closingBalanceBase = v; }
    public DrCr getClosingDrCrBase()                  { return closingDrCrBase; }
    public void setClosingDrCrBase(DrCr v)            { this.closingDrCrBase = v; }
    public BigDecimal getExchangeRate()               { return exchangeRate; }
    public void setExchangeRate(BigDecimal v)         { this.exchangeRate = v; }
    public BigDecimal getForexDiff()                  { return forexDiff; }
    public void setForexDiff(BigDecimal v)            { this.forexDiff = v; }
    public Integer getTransactionCount()              { return transactionCount; }
    public void setTransactionCount(Integer v)        { this.transactionCount = v; }
    public PeriodStatus getPeriodStatus()             { return periodStatus; }
    public void setPeriodStatus(PeriodStatus v)       { this.periodStatus = v; }
    public LocalDate getLastUpdate()                  { return lastUpdate; }
    public void setLastUpdate(LocalDate v)            { this.lastUpdate = v; }
    public Long getUserId()                           { return userId; }
    public void setUserId(Long v)                     { this.userId = v; }
}
