package com.joud.accounting.bean;

import com.joud.accounting.entity.ExchangeRateHistory;
import com.joud.accounting.entity.ExchangeRateHistory.RateType;
import com.joud.accounting.entity.ExchangeRateHistory.RateSource;
import com.joud.core.security.filter.AuthFilter;
import com.joud.core.security.session.UserSession;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * ExchangeRateBean — إدارة أسعار الصرف اليومية
 *
 * يتيح إدخال سعر صرف كل عملة مقابل العملة الأساسية لكل يوم،
 * مع تعبئة تلقائية للسعر العكسي، وعرض جدول تاريخي قابل للفلترة
 * بالعملة والفترة، بالإضافة إلى لوحة سريعة بآخر سعر لكل عملة.
 */
@Named("exchRateBean")
@ViewScoped
public class ExchangeRateBean implements Serializable {

    // ─── نموذج الإدخال ──────────────────────────────
    private Long       currencyId;
    private Long       baseCurrencyId;
    private LocalDate  rateDate = LocalDate.now();
    private RateType   rateType = RateType.M;
    private BigDecimal rate;
    private BigDecimal inverseRate;
    private String     notes;

    // ─── فلاتر الجدول ───────────────────────────────
    private Long       filterCurrencyId;
    private LocalDate  filterFrom = LocalDate.now().minusDays(30);
    private LocalDate  filterTo   = LocalDate.now();

    // ─── البيانات ────────────────────────────────────
    private List<ExchangeRateHistory> rateHistory;
    private List<Object[]> latestRates; // آخر سعر مسجَّل لكل عملة (للوحة سريعة)

    // ─── قوائم الاختيار ─────────────────────────────
    private List<Object[]> currencies;
    private Long agentId;

    @PersistenceContext
    private EntityManager em;

    @PostConstruct
    public void init() {
        UserSession user = getCurrentUser();
        if (user != null) agentId = user.getAgentId();
        loadCurrencies();
        resolveBaseCurrency();
        loadLatestRates();
        loadHistory();
    }

    // ════════════════════════════════════════════════
    // حفظ سعر جديد
    // ════════════════════════════════════════════════

    @Transactional
    public void save() {
        if (currencyId == null) { addError("يجب اختيار العملة"); return; }
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            addError("يجب إدخال سعر أكبر من صفر"); return;
        }
        if (currencyId.equals(baseCurrencyId)) {
            addError("لا يمكن إدخال سعر صرف للعملة الأساسية نفسها"); return;
        }

        try {
            // تحقق: هل يوجد سعر مسجَّل مسبقاً لنفس العملة/التاريخ/النوع؟
            List<ExchangeRateHistory> existing = em.createQuery(
                "SELECT e FROM ExchangeRateHistory e " +
                "WHERE e.currencyId=:c AND e.baseCurrencyId=:b AND e.rateDate=:d AND e.rateType=:t",
                ExchangeRateHistory.class)
                .setParameter("c", currencyId).setParameter("b", baseCurrencyId)
                .setParameter("d", rateDate).setParameter("t", rateType)
                .getResultList();

            ExchangeRateHistory ex;
            boolean isUpdate = !existing.isEmpty();
            if (isUpdate) {
                ex = existing.get(0);
            } else {
                ex = new ExchangeRateHistory();
                ex.setId(generateId());
                ex.setCurrencyId(currencyId);
                ex.setBaseCurrencyId(baseCurrencyId);
                ex.setAgentId(agentId);
                ex.setRateDate(rateDate);
                ex.setRateType(rateType);
            }

            ex.setRate(rate);
            ex.setInverseRate(inverseRate != null ? inverseRate : computeInverse(rate));
            ex.setSource(RateSource.M);
            ex.setNotes(notes);
            ex.setEntryDate(LocalDate.now());
            ex.setUserId(getCurrentUserId());

            if (isUpdate) em.merge(ex); else em.persist(ex);

            addInfo(isUpdate ? "تم تحديث سعر الصرف" : "تم حفظ سعر الصرف الجديد");
            clearForm();
            loadLatestRates();
            loadHistory();
        } catch (Exception e) {
            addError("خطأ في الحفظ: " + e.getMessage());
        }
    }

    /** يُحسب السعر العكسي تلقائياً عند تغيير السعر الرئيسي */
    public void onRateChange() {
        if (rate != null && rate.compareTo(BigDecimal.ZERO) > 0) {
            inverseRate = computeInverse(rate);
        }
    }

    private BigDecimal computeInverse(BigDecimal r) {
        return BigDecimal.ONE.divide(r, 8, RoundingMode.HALF_UP);
    }

    /** تعبئة سريعة من آخر سعر مسجَّل لعملة معيَّنة لتسهيل تحديث اليوم */
    public void prefillFromLatest(Long curId) {
        try {
            ExchangeRateHistory latest = em.createNamedQuery(
                "ExchangeRate.findLatest", ExchangeRateHistory.class)
                .setParameter("currencyId", curId)
                .setParameter("baseCurrencyId", baseCurrencyId)
                .setMaxResults(1).getResultList()
                .stream().findFirst().orElse(null);

            currencyId = curId;
            rateDate   = LocalDate.now();
            rateType   = RateType.M;
            if (latest != null) {
                rate        = latest.getRate();
                inverseRate = latest.getInverseRate();
            } else {
                rate = null; inverseRate = null;
            }
            notes = null;
        } catch (Exception e) {
            addError("تعذّر تحميل آخر سعر: " + e.getMessage());
        }
    }

    public void clearForm() {
        currencyId  = null;
        rate        = null;
        inverseRate = null;
        notes       = null;
        rateDate    = LocalDate.now();
        rateType    = RateType.M;
    }

    // ════════════════════════════════════════════════
    // حذف سعر
    // ════════════════════════════════════════════════

    @Transactional
    public void delete(ExchangeRateHistory ex) {
        try {
            ExchangeRateHistory e = em.find(ExchangeRateHistory.class, ex.getId());
            if (e != null) {
                em.remove(e);
                addInfo("تم حذف السعر");
                loadLatestRates();
                loadHistory();
            }
        } catch (Exception e) {
            addError("خطأ في الحذف: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════
    // تحميل البيانات
    // ════════════════════════════════════════════════

    public void search() { loadHistory(); }

    public void resetFilters() {
        filterCurrencyId = null;
        filterFrom = LocalDate.now().minusDays(30);
        filterTo   = LocalDate.now();
        loadHistory();
    }

    private void loadHistory() {
        try {
            StringBuilder jpql = new StringBuilder(
                "SELECT e FROM ExchangeRateHistory e " +
                "WHERE e.baseCurrencyId = :base " +
                "AND e.rateDate BETWEEN :f AND :t ");
            if (filterCurrencyId != null) jpql.append("AND e.currencyId = :cur ");
            jpql.append("ORDER BY e.rateDate DESC, e.currencyId");

            var q = em.createQuery(jpql.toString(), ExchangeRateHistory.class)
                .setParameter("base", baseCurrencyId)
                .setParameter("f", filterFrom).setParameter("t", filterTo);
            if (filterCurrencyId != null) q.setParameter("cur", filterCurrencyId);

            rateHistory = q.getResultList();
        } catch (Exception e) {
            rateHistory = List.of();
        }
    }

    /** آخر سعر مسجَّل (وسط) لكل عملة غير العملة الأساسية — لوحة سريعة بالأعلى */
    @SuppressWarnings("unchecked")
    private void loadLatestRates() {
        try {
            latestRates = em.createNativeQuery(
                "SELECT DISTINCT ON (CURRENCY_I_ID) CURRENCY_I_ID, EXCH_F_RATE, EXCH_F_INVERSE, EXCH_D_DATE " +
                "FROM EXCH_RATE_HIST " +
                "WHERE BASE_CURRENCY_I_ID = :base AND EXCH_C_RATE_TYPE = 'M' " +
                "ORDER BY CURRENCY_I_ID, EXCH_D_DATE DESC")
                .setParameter("base", baseCurrencyId)
                .getResultList();
        } catch (Exception e) {
            // بعض إصدارات قاعدة البيانات قد لا تدعم DISTINCT ON — استعلام بديل آمن
            try {
                latestRates = em.createNativeQuery(
                    "SELECT CURRENCY_I_ID, EXCH_F_RATE, EXCH_F_INVERSE, MAX(EXCH_D_DATE) " +
                    "FROM EXCH_RATE_HIST WHERE BASE_CURRENCY_I_ID=:base AND EXCH_C_RATE_TYPE='M' " +
                    "GROUP BY CURRENCY_I_ID, EXCH_F_RATE, EXCH_F_INVERSE")
                    .setParameter("base", baseCurrencyId).getResultList();
            } catch (Exception ex) { latestRates = List.of(); }
        }
    }

    @SuppressWarnings("unchecked")
    private void loadCurrencies() {
        try {
            currencies = (List<Object[]>) em.createNativeQuery(
                "SELECT CURRENCY_I_ID, CURRENCY_C_DESC, CURRENCY_C_SYMBOL " +
                "FROM CURRENCY_MAST ORDER BY CURRENCY_C_DESC")
                .getResultList();
        } catch (Exception e) { currencies = List.of(); }
    }

    /** العملة الأساسية للفرع الحالي من AGENT_BROFILE */
    private void resolveBaseCurrency() {
        try {
            Object r = em.createNativeQuery(
                "SELECT BASE_CURRENCY_I_ID FROM AGENT_BROFILE WHERE AGENT_I_ID=:a")
                .setParameter("a", agentId).getSingleResult();
            baseCurrencyId = ((Number) r).longValue();
        } catch (Exception e) {
            // افتراضي: أول عملة في القائمة كعملة أساسية إن تعذَّر التحديد
            baseCurrencyId = currencies != null && !currencies.isEmpty()
                ? ((Number) currencies.get(0)[0]).longValue() : null;
        }
    }

    // ════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════

    public String getCurrencyName(Long id) {
        if (id == null || currencies == null) return "—";
        return currencies.stream().filter(c -> id.equals(((Number) c[0]).longValue()))
            .map(c -> c[1].toString()).findFirst().orElse("—");
    }
    public String getCurrencySymbol(Long id) {
        if (id == null || currencies == null) return "";
        return currencies.stream().filter(c -> id.equals(((Number) c[0]).longValue()))
            .map(c -> c[2] != null ? c[2].toString() : "").findFirst().orElse("");
    }
    public String getRateTypeLabel(RateType t) {
        if (t == null) return "—";
        return switch (t) {
            case B -> "شراء"; case S -> "بيع"; case M -> "وسط"; case O -> "رسمي";
        };
    }

    private Long generateId() {
        try {
            return ((Number) em.createNativeQuery(
                "SELECT ID_I_GEN FROM KEY_GENERATION WHERE TABLE_C_NAME='EXCH_RATE_HIST'")
                .getSingleResult()).longValue();
        } catch (Exception e) { return System.currentTimeMillis(); }
    }

    private UserSession getCurrentUser() {
        return (UserSession) FacesContext.getCurrentInstance()
            .getExternalContext().getRequestMap().get(AuthFilter.SESSION_ATTR);
    }
    private Long getCurrentUserId() {
        UserSession u = getCurrentUser();
        return u != null ? u.getUserId() : 0L;
    }
    private void addInfo(String m) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(FacesMessage.SEVERITY_INFO, m, null));
    }
    private void addError(String m) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(FacesMessage.SEVERITY_ERROR, m, null));
    }

    // ─── Getters & Setters ─────────────────────────
    public Long getCurrencyId()                  { return currencyId; }
    public void setCurrencyId(Long v)            { this.currencyId = v; }
    public Long getBaseCurrencyId()               { return baseCurrencyId; }
    public void setBaseCurrencyId(Long v)        { this.baseCurrencyId = v; }
    public LocalDate getRateDate()               { return rateDate; }
    public void setRateDate(LocalDate v)         { this.rateDate = v; }
    public RateType getRateType()                { return rateType; }
    public void setRateType(RateType v)          { this.rateType = v; }
    public BigDecimal getRate()                  { return rate; }
    public void setRate(BigDecimal v)            { this.rate = v; }
    public BigDecimal getInverseRate()           { return inverseRate; }
    public void setInverseRate(BigDecimal v)     { this.inverseRate = v; }
    public String getNotes()                      { return notes; }
    public void setNotes(String v)               { this.notes = v; }
    public Long getFilterCurrencyId()             { return filterCurrencyId; }
    public void setFilterCurrencyId(Long v)      { this.filterCurrencyId = v; }
    public LocalDate getFilterFrom()              { return filterFrom; }
    public void setFilterFrom(LocalDate v)       { this.filterFrom = v; }
    public LocalDate getFilterTo()                { return filterTo; }
    public void setFilterTo(LocalDate v)         { this.filterTo = v; }
    public List<ExchangeRateHistory> getRateHistory() { return rateHistory; }
    public List<Object[]> getLatestRates()        { return latestRates; }
    public List<Object[]> getCurrencies()         { return currencies; }
}
