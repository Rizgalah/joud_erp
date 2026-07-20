package com.joud.accounting.bean;

import com.joud.accounting.entity.*;
import com.joud.accounting.entity.JournalEntry.JvType;
import com.joud.accounting.service.GeneralLedgerService;
import com.joud.core.security.filter.AuthFilter;
import com.joud.core.security.session.UserSession;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * JournalEntryBean — الـ Backing Bean لشاشة القيد اليومي
 * @ViewScoped يحتفظ بالبيانات طوال تفاعل المستخدم مع الشاشة
 */
@Named("jvBean")
@ViewScoped
public class JournalEntryBean implements Serializable {

    // ─── رأس القيد ──────────────────────────────────
    private Long       agentId;
    private LocalDate  date         = LocalDate.now();
    private String     jvType       = "JV";
    private String     narration;
    private Long       currencyId;
    private BigDecimal exchangeRate = BigDecimal.ONE;
    private String     reference;   // رقم القيد — يُولَّد تلقائياً

    // ─── سطر القيد الجاري إدخاله ────────────────────
    private String     lineAccountType;
    private String     lineDrCr     = "DR";
    private BigDecimal lineAmount   = BigDecimal.ZERO;
    private String     lineNarration;

    // ─── قائمة الأسطر ───────────────────────────────
    private List<DayLedger> lines   = new ArrayList<>();

    // ─── الإجماليات ─────────────────────────────────
    private BigDecimal totalDebit   = BigDecimal.ZERO;
    private BigDecimal totalCredit  = BigDecimal.ZERO;

    // ─── قوائم الاختيار ─────────────────────────────
    private List<Object[]> accounts;
    private List<Object[]> currencies;
    private List<Object[]> agents;

    // ─── وضع الشاشة ─────────────────────────────────
    private boolean viewMode        = false; // عرض أم إدخال
    private List<JournalEntry> recentEntries;

    @Inject private GeneralLedgerService glService;
    @PersistenceContext private EntityManager em;

    @PostConstruct
    public void init() {
        loadLookups();
        generateReference();
        loadRecentEntries();

        // اضبط الفرع من جلسة المستخدم
        UserSession user = getCurrentUser();
        if (user != null) this.agentId = user.getAgentId();
    }

    // ════════════════════════════════════════════════
    // أوامر الشاشة
    // ════════════════════════════════════════════════

    /** إضافة سطر للقيد */
    public void addLine() {
        if (lineAccountType == null || lineAccountType.isBlank()) {
            addError("يجب اختيار الحساب");
            return;
        }
        if (lineAmount == null || lineAmount.compareTo(BigDecimal.ZERO) <= 0) {
            addError("يجب إدخال مبلغ أكبر من صفر");
            return;
        }

        DayLedger line = new DayLedger();
        line.setAccountType(lineAccountType);
        line.setDrCr(lineDrCr);
        line.setAmount(lineAmount);
        line.setNarration(lineNarration != null ? lineNarration : narration);
        line.setCurrencyId(currencyId);
        line.setExchangeRate(exchangeRate);
        line.setDate(date);
        line.setAgentId(agentId);
        line.calculateBaseAmount();

        lines.add(line);
        recalcTotals();
        clearLineInputs();
    }

    /** حذف سطر */
    public void removeLine(int index) {
        if (index >= 0 && index < lines.size()) {
            lines.remove(index);
            recalcTotals();
        }
    }

    /** حفظ القيد كمسودة */
    public void saveDraft() {
        if (lines.isEmpty()) { addError("لا توجد أسطر في القيد"); return; }
        try {
            JournalEntry jv = buildJournalEntry(JournalEntry.JvStatus.O);
            em.persist(jv);
            addInfo("تم حفظ المسودة: " + jv.getReference());
            reset();
        } catch (Exception e) {
            addError("خطأ في الحفظ: " + e.getMessage());
        }
    }

    /** إرسال للاعتماد */
    public void submitForApproval() {
        if (!isBalanced()) { addError("القيد غير متوازن. الفرق: " + getDifference()); return; }
        if (lines.size() < 2) { addError("القيد يجب أن يحتوي على سطرين على الأقل"); return; }
        try {
            JournalEntry jv = buildJournalEntry(JournalEntry.JvStatus.P);
            em.persist(jv);
            addInfo("تم إرسال القيد للاعتماد: " + jv.getReference());
            reset();
        } catch (Exception e) {
            addError("خطأ: " + e.getMessage());
        }
    }

    /** اعتماد وترحيل قيد معلق */
    public void approveAndPost(Long jvId) {
        try {
            UserSession user = getCurrentUser();
            glService.postJournalEntry(jvId, user != null ? user.getUserId() : 0L);
            addInfo("تم ترحيل القيد بنجاح");
            loadRecentEntries();
        } catch (GeneralLedgerService.AccountingException e) {
            addError(e.getMessage());
        }
    }

    /** إلغاء قيد معتمد */
    public void voidEntry(Long jvId, String reason) {
        try {
            UserSession user = getCurrentUser();
            glService.voidJournalEntry(jvId, user != null ? user.getUserId() : 0L, reason);
            addInfo("تم إلغاء القيد");
            loadRecentEntries();
        } catch (GeneralLedgerService.AccountingException e) {
            addError(e.getMessage());
        }
    }

    /** تحديث سعر الصرف عند تغيير العملة */
    public void onCurrencyChange() {
        if (currencyId != null && agentId != null) {
            exchangeRate = glService.resolveExchangeRate(currencyId, date, agentId);
        }
    }

    // ════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════

    private JournalEntry buildJournalEntry(JournalEntry.JvStatus status) {
        JournalEntry jv = new JournalEntry();
        jv.setId(generateId());
        jv.setReference(reference);
        jv.setAgentId(agentId);
        jv.setCountryCode(getCountryCode(agentId));
        jv.setDate(date);
        jv.setType(JvType.valueOf(jvType));
        jv.setNarration(narration);
        jv.setCurrencyId(currencyId);
        jv.setExchangeRate(exchangeRate);
        jv.setStatus(status);
        jv.setTotalDebit(totalDebit);
        jv.setTotalCredit(totalCredit);

        // ربط الأسطر
        for (DayLedger line : lines) {
            line.setId(generateId());
            line.setJournalEntryId(jv.getId());
            line.setCountryCode(jv.getCountryCode());
            line.setScreenName("JournalEntry");
            jv.getLines().add(line);
        }

        UserSession user = getCurrentUser();
        if (user != null) jv.setCreatedBy(user.getUserId());
        return jv;
    }

    private void recalcTotals() {
        totalDebit  = lines.stream().filter(l -> "DR".equals(l.getDrCr()))
                          .map(DayLedger::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        totalCredit = lines.stream().filter(l -> "CR".equals(l.getDrCr()))
                          .map(DayLedger::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void clearLineInputs() {
        lineAccountType = null;
        lineDrCr        = "DR";
        lineAmount      = BigDecimal.ZERO;
        lineNarration   = null;
    }

    private void reset() {
        lines.clear();
        totalDebit = totalCredit = BigDecimal.ZERO;
        narration = null;
        generateReference();
        loadRecentEntries();
    }

    private void generateReference() {
        int year  = LocalDate.now().getYear();
        // يُجلب الرقم التسلسلي من قاعدة البيانات
        long seq = 1;
        try {
            Object r = em.createNativeQuery(
                "SELECT COUNT(*)+1 FROM JOURNAL_ENTRY WHERE EXTRACT(YEAR FROM JV_D_DATE)=:y")
                .setParameter("y", year).getSingleResult();
            seq = ((Number) r).longValue();
        } catch (Exception ignored) {}
        this.reference = String.format("JV-%d-%05d", year, seq);
    }

    @SuppressWarnings("unchecked")
    private void loadLookups() {
        try {
            accounts   = em.createNativeQuery(
                "SELECT ACT_C_TYPE, ACT_C_NAME FROM ACCOUNT_MAST WHERE ACT_C_ACTSTS='A' ORDER BY ACT_C_TYPE")
                .getResultList();
            currencies = em.createNativeQuery(
                "SELECT CURRENCY_I_ID, CURRENCY_C_DESC, CURRENCY_C_SYMBOL FROM CURRENCY_MAST ORDER BY CURRENCY_C_DESC")
                .getResultList();
            agents     = em.createNativeQuery(
                "SELECT AGENT_I_ID, AGENT_C_NAME FROM AGENT_MAST ORDER BY AGENT_C_NAME")
                .getResultList();
        } catch (Exception e) {
            accounts = currencies = agents = List.of();
        }
    }

    private void loadRecentEntries() {
        try {
            recentEntries = em.createQuery(
                "SELECT j FROM JournalEntry j ORDER BY j.createdAt DESC",
                JournalEntry.class).setMaxResults(20).getResultList();
        } catch (Exception e) {
            recentEntries = List.of();
        }
    }

    private String getCountryCode(Long agentId) {
        try {
            return (String) em.createNativeQuery(
                "SELECT COUNTRY_C_CODE FROM AGENT_MAST WHERE AGENT_I_ID=:id")
                .setParameter("id", agentId).getSingleResult();
        } catch (Exception e) { return "XX"; }
    }

    private Long generateId() {
        try {
            return ((Number) em.createNativeQuery(
                "SELECT ID_I_GEN FROM KEY_GENERATION WHERE TABLE_C_NAME='JOURNAL_ENTRY'")
                .getSingleResult()).longValue();
        } catch (Exception e) { return System.currentTimeMillis(); }
    }

    private UserSession getCurrentUser() {
        return (UserSession) FacesContext.getCurrentInstance()
            .getExternalContext().getRequestMap().get(AuthFilter.SESSION_ATTR);
    }

    private void addError(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(FacesMessage.SEVERITY_ERROR, msg, null));
    }

    private void addInfo(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(FacesMessage.SEVERITY_INFO, msg, null));
    }

    // ─── Computed Properties ────────────────────────
    public boolean isBalanced()  { return totalDebit.compareTo(totalCredit) == 0; }
    public BigDecimal getDifference() { return totalDebit.subtract(totalCredit).abs(); }
    public String getBalanceStatus() {
        return isBalanced() ? "متوازن ✓" : "فرق: " + getDifference();
    }
    public String getAccountName(String actType) {
        if (accounts == null) return actType;
        return accounts.stream().filter(a -> actType.equals(a[0]))
            .map(a -> a[0] + " — " + a[1]).findFirst().orElse(actType);
    }

    // ─── Getters & Setters ─────────────────────────
    public Long getAgentId()                      { return agentId; }
    public void setAgentId(Long v)                { this.agentId = v; }
    public LocalDate getDate()                    { return date; }
    public void setDate(LocalDate v)              { this.date = v; }
    public String getJvType()                     { return jvType; }
    public void setJvType(String v)               { this.jvType = v; }
    public String getNarration()                  { return narration; }
    public void setNarration(String v)            { this.narration = v; }
    public Long getCurrencyId()                   { return currencyId; }
    public void setCurrencyId(Long v)             { this.currencyId = v; }
    public BigDecimal getExchangeRate()           { return exchangeRate; }
    public void setExchangeRate(BigDecimal v)     { this.exchangeRate = v; }
    public String getReference()                  { return reference; }
    public String getLineAccountType()            { return lineAccountType; }
    public void setLineAccountType(String v)      { this.lineAccountType = v; }
    public String getLineDrCr()                   { return lineDrCr; }
    public void setLineDrCr(String v)             { this.lineDrCr = v; }
    public BigDecimal getLineAmount()             { return lineAmount; }
    public void setLineAmount(BigDecimal v)       { this.lineAmount = v; }
    public String getLineNarration()              { return lineNarration; }
    public void setLineNarration(String v)        { this.lineNarration = v; }
    public List<DayLedger> getLines()             { return lines; }
    public BigDecimal getTotalDebit()             { return totalDebit; }
    public BigDecimal getTotalCredit()            { return totalCredit; }
    public List<Object[]> getAccounts()           { return accounts; }
    public List<Object[]> getCurrencies()         { return currencies; }
    public List<Object[]> getAgents()             { return agents; }
    public List<JournalEntry> getRecentEntries()  { return recentEntries; }
    public boolean isViewMode()                   { return viewMode; }
    public void setViewMode(boolean v)            { this.viewMode = v; }
}
