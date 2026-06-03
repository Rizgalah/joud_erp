package com.joud.accounting.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ExchangeRateHistory — تاريخ أسعار الصرف
 * جدول: EXCH_RATE_HIST
 *
 * يحفظ سعر كل عملة مقابل العملة الأساسية يومياً.
 * يُستخدم في تحويل الأرصدة عند التجميع وإعادة التقييم.
 */
@Entity
@Table(name = "EXCH_RATE_HIST",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_exch_rate",
        columnNames = {"CURRENCY_I_ID","BASE_CURRENCY_I_ID","EXCH_D_DATE","EXCH_C_RATE_TYPE","AGENT_I_ID"}
    )
)
@NamedQueries({
    @NamedQuery(name = "ExchangeRate.findByDateAndCurrency",
        query = """
            SELECT e FROM ExchangeRateHistory e
            WHERE e.currencyId = :currencyId
              AND e.baseCurrencyId = :baseCurrencyId
              AND e.rateDate <= :date
              AND e.rateType = :type
            ORDER BY e.rateDate DESC
            """),
    @NamedQuery(name = "ExchangeRate.findLatest",
        query = """
            SELECT e FROM ExchangeRateHistory e
            WHERE e.currencyId = :currencyId
              AND e.baseCurrencyId = :baseCurrencyId
              AND e.rateType = 'M'
            ORDER BY e.rateDate DESC
            """),
})
public class ExchangeRateHistory {

    /** نوع السعر */
    public enum RateType {
        B, // شراء Buy
        S, // بيع Sell
        M, // وسط Middle
        O  // رسمي Official
    }

    /** مصدر السعر */
    public enum RateSource {
        M, // يدوي Manual
        A  // تلقائي Auto/API
    }

    @Id
    @Column(name = "EXCH_I_ID")
    private Long id;

    @Column(name = "CURRENCY_I_ID", nullable = false)
    private Long currencyId;

    @Column(name = "BASE_CURRENCY_I_ID", nullable = false)
    private Long baseCurrencyId;

    @Column(name = "AGENT_I_ID")
    private Long agentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "EXCH_C_RATE_TYPE", nullable = false, length = 1)
    private RateType rateType = RateType.M;

    @Column(name = "EXCH_D_DATE", nullable = false)
    private LocalDate rateDate;

    /** سعر الصرف: 1 وحدة عملة = X وحدة عملة أساسية */
    @Column(name = "EXCH_F_RATE", nullable = false, precision = 23, scale = 8)
    private BigDecimal rate;

    /** السعر العكسي: 1 وحدة أساسية = X وحدة عملة */
    @Column(name = "EXCH_F_INVERSE", precision = 23, scale = 8)
    private BigDecimal inverseRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "EXCH_C_SOURCE", length = 1)
    private RateSource source = RateSource.M;

    @Column(name = "EXCH_C_NOTES", length = 250)
    private String notes;

    @Column(name = "EXCH_D_ENTRY")
    private LocalDate entryDate;

    @Column(name = "user_i_id")
    private Long userId;

    // ─── Helper ────────────────────────────────────

    /**
     * تحويل مبلغ بالعملة الأجنبية إلى العملة الأساسية
     */
    public BigDecimal convertToBase(BigDecimal amount) {
        if (amount == null || rate == null) return BigDecimal.ZERO;
        return amount.multiply(rate).setScale(5, java.math.RoundingMode.HALF_UP);
    }

    /**
     * تحويل مبلغ من العملة الأساسية إلى العملة الأجنبية
     */
    public BigDecimal convertFromBase(BigDecimal baseAmount) {
        if (baseAmount == null || rate == null || rate.compareTo(BigDecimal.ZERO) == 0)
            return BigDecimal.ZERO;
        return baseAmount.divide(rate, 5, java.math.RoundingMode.HALF_UP);
    }

    // ─── Getters & Setters ─────────────────────────
    public Long getId()                          { return id; }
    public void setId(Long id)                   { this.id = id; }
    public Long getCurrencyId()                  { return currencyId; }
    public void setCurrencyId(Long v)            { this.currencyId = v; }
    public Long getBaseCurrencyId()              { return baseCurrencyId; }
    public void setBaseCurrencyId(Long v)        { this.baseCurrencyId = v; }
    public Long getAgentId()                     { return agentId; }
    public void setAgentId(Long v)               { this.agentId = v; }
    public RateType getRateType()                { return rateType; }
    public void setRateType(RateType v)          { this.rateType = v; }
    public LocalDate getRateDate()               { return rateDate; }
    public void setRateDate(LocalDate v)         { this.rateDate = v; }
    public BigDecimal getRate()                  { return rate; }
    public void setRate(BigDecimal v)            { this.rate = v; }
    public BigDecimal getInverseRate()           { return inverseRate; }
    public void setInverseRate(BigDecimal v)     { this.inverseRate = v; }
    public RateSource getSource()                { return source; }
    public void setSource(RateSource v)          { this.source = v; }
    public String getNotes()                     { return notes; }
    public void setNotes(String v)               { this.notes = v; }
    public LocalDate getEntryDate()              { return entryDate; }
    public void setEntryDate(LocalDate v)        { this.entryDate = v; }
    public Long getUserId()                      { return userId; }
    public void setUserId(Long v)                { this.userId = v; }
}
