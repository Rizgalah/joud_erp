package com.joud.accounting.bean;

import com.joud.accounting.entity.DayLedger;
import com.joud.accounting.entity.JournalEntry;
import com.joud.accounting.entity.JournalEntry.JvType;
import com.joud.accounting.service.CountryCodes;
import com.joud.accounting.service.GeneralLedgerService;
import com.joud.core.db.JoudRepository;
import com.joud.core.id.SequenceAllocator;
import com.joud.core.security.filter.AuthFilter;
import com.joud.core.security.session.UserSession;

import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JournalEntryBean — الـ Backing Bean لشاشة القيد اليومي
 * @ViewScoped يحتفظ بالبيانات طوال تفاعل المستخدم مع الشاشة.
 *
 * الحفظ عبر JoudRepository: معاملة واحدة تُنشئ الرقم المرجعي والمعرّفات وتحفظ
 * القيد وأسطره معاً (أو لا شيء). أخطاء قاعدة البيانات تُسجَّل وتظهر للمستخدم،
 * ولا تُبتلع بصمت.
 */
@Named("jvBean")
@ViewScoped
public class JournalEntryBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(JournalEntryBean.class.getName());
    private static final String REFERENCE_PLACEHOLDER = "(يُولَّد عند الحفظ)";
    private static final int ACCOUNT_LIST_LIMIT = 2000;

    // ─── رأس القيد ──────────────────────────────────
    private Long       agentId;
    private LocalDate  date         = LocalDate.now();
    private String     jvType       = "JV";
    private String     narration;
    private Long       currencyId;
    private BigDecimal exchangeRate = BigDecimal.ONE;

    // ─── سطر القيد الجاري إدخاله ────────────────────
    private Long       lineAccountId;
    private String     lineDrCr     = "DR";
    private BigDecimal lineAmount   = BigDecimal.ZERO;
    private String     lineNarration;

    // ─── قائمة الأسطر ───────────────────────────────
    private List<DayLedger> lines   = new ArrayList<>();

    // ─── الإجماليات ─────────────────────────────────
    private BigDecimal totalDebit   = BigDecimal.ZERO;
    private BigDecimal totalCredit  = BigDecimal.ZERO;

    // ─── قوائم الاختيار ─────────────────────────────
    private List<AccountOption> accounts   = List.of();
    private List<Object[]>      currencies = List.of();
    private List<Object[]>      agents     = List.of();

    // ─── وضع الشاشة ─────────────────────────────────
    private boolean viewMode        = false; // عرض أم إدخال
    private List<JournalEntry> recentEntries = List.of();

    @Inject private GeneralLedgerService glService;
    @Inject private JoudRepository db;

    /** حساب قابل للاختيار في القيد (صف من ACCOUNT_MAST). */
    public static class AccountOption implements Serializable {
        private final Long id;
        private final String type;
        private final Long customerId;
        private final Long currencyId;
        private final Long agentId;
        private final String label;

        public AccountOption(Long id, String type, Long customerId, Long currencyId, Long agentId, String name) {
            this.id = id;
            this.type = type;
            this.customerId = customerId;
            this.currencyId = currencyId;
            this.agentId = agentId;
            this.label = (type != null ? type + " — " : "") + (name != null ? name : "")
                    + (currencyId != null ? " [" + currencyId + "]" : "");
        }

        public Long getId()         { return id; }
        public String getType()     { return type; }
        public Long getCustomerId() { return customerId; }
        public Long getCurrencyId() { return currencyId; }
        public Long getAgentId()    { return agentId; }
        public String getLabel()    { return label; }
    }

    /** نتيجة تحميل القوائم (استعلام واحد لكل القوائم). */
    static class Lookups {
        final List<AccountOption> accounts;
        final List<Object[]> currencies;
        final List<Object[]> agents;

        Lookups(List<AccountOption> accounts, List<Object[]> currencies, List<Object[]> agents) {
            this.accounts = accounts;
            this.currencies = currencies;
            this.agents = agents;
        }
    }

    @PostConstruct
    public void init() {
        // اضبط الفرع من جلسة المستخدم قبل تحميل حساباته
        UserSession user = getCurrentUser();
        if (user != null) this.agentId = user.getAgentId();

        loadLookups();
        loadRecentEntries();
    }

    // ════════════════════════════════════════════════
    // أوامر الشاشة
    // ════════════════════════════════════════════════

    /** إضافة سطر للقيد */
    public void addLine() {
        if (lineAccountId == null) {
            addError("يجب اختيار الحساب");
            return;
        }
        if (lineAmount == null || lineAmount.compareTo(BigDecimal.ZERO) <= 0) {
            addError("يجب إدخال مبلغ أكبر من صفر");
            return;
        }
        if (agentId == null) {
            addError("يجب اختيار الفرع أولاً");
            return;
        }
        if (currencyId == null) {
            addError("يجب اختيار عملة القيد أولاً");
            return;
        }
        if (exchangeRate == null || exchangeRate.compareTo(BigDecimal.ZERO) <= 0) {
            addError("سعر الصرف غير صالح");
            return;
        }

        AccountOption account = findAccount(lineAccountId);
        if (account == null) {
            addError("الحساب المختار غير موجود في القائمة");
            return;
        }
        if (!agentId.equals(account.getAgentId())) {
            addError("الحساب المختار يتبع فرعاً آخر");
            return;
        }
        if (!currencyId.equals(account.getCurrencyId())) {
            addError("عملة الحساب تختلف عن عملة القيد");
            return;
        }

        DayLedger line = new DayLedger();
        line.setAccountType(account.getType());
        line.setCustomerId(account.getCustomerId());
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
        save(JournalEntry.JvStatus.O, "تم حفظ المسودة: ");
    }

    /** إرسال للاعتماد */
    public void submitForApproval() {
        if (!isBalanced()) { addError("القيد غير متوازن. الفرق: " + getDifference()); return; }
        if (lines.size() < 2) { addError("القيد يجب أن يحتوي على سطرين على الأقل"); return; }
        save(JournalEntry.JvStatus.P, "تم إرسال القيد للاعتماد: ");
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
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Unexpected error while posting journal entry " + jvId, e);
            addError("حدث خطأ غير متوقع أثناء الترحيل. لم يُحفظ أي تغيير.");
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
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Unexpected error while voiding journal entry " + jvId, e);
            addError("حدث خطأ غير متوقع أثناء الإلغاء. لم يُحفظ أي تغيير.");
        }
    }

    /** تحديث سعر الصرف عند تغيير العملة */
    public void onCurrencyChange() {
        if (currencyId != null && agentId != null) {
            exchangeRate = glService.resolveExchangeRate(currencyId, date, agentId);
        }
    }

    // ════════════════════════════════════════════════
    // الحفظ
    // ════════════════════════════════════════════════

    private void save(JournalEntry.JvStatus status, String successPrefix) {
        try {
            JournalEntry saved = db.executeInTransaction(em -> {
                return persistJournal(em, status);
            });
            addInfo(successPrefix + saved.getReference());
            reset();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to save journal entry", e);
            addError("تعذّر حفظ القيد، ولم يُحفظ أي شيء. حاول مرة أخرى أو راجع المسؤول.");
        }
    }

    /** يُنفَّذ داخل المعاملة: الرقم المرجعي، المعرّفات، رمز البلد، ثم الحفظ. */
    private JournalEntry persistJournal(EntityManager em, JournalEntry.JvStatus status) {
        JournalEntry jv = new JournalEntry();
        jv.setId(SequenceAllocator.nextId(em, "JOURNAL_ENTRY", "JOURNAL_ENTRY", "JV_I_ID"));
        jv.setReference(nextReference(em, date.getYear()));
        jv.setAgentId(agentId);
        jv.setCountryCode(countryCodeOf(em, agentId));
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
            line.setId(SequenceAllocator.nextId(em, "DAY_LEDG", "DAY_LEDG", "TRN_I_NO"));
            line.setJournalEntryId(jv.getId());
            line.setCountryCode(jv.getCountryCode());
            line.setScreenName("JournalEntry");
            jv.getLines().add(line);
        }

        UserSession user = getCurrentUser();
        if (user != null) jv.setCreatedBy(user.getUserId());

        em.persist(jv);
        return jv;
    }

    /**
     * الرقم المرجعي JV-سنة-00001 من تسلسل لكل سنة. يبدأ بعد أكبر رقم موجود
     * فعلاً في JOURNAL_ENTRY (حتى لا يتصادم مع قيود سابقة).
     */
    private String nextReference(EntityManager em, int year) {
        Object max = em.createNativeQuery(
                "SELECT MAX(CAST(SUBSTRING(JV_C_REF FROM '[0-9]+$') AS BIGINT)) "
                        + "FROM JOURNAL_ENTRY WHERE JV_C_REF LIKE ?1")
                .setParameter(1, "JV-" + year + "-%")
                .getSingleResult();
        long floor = max == null ? 0L : ((Number) max).longValue();
        long seq = SequenceAllocator.nextValue(em, "JV_REF_" + year, floor);
        return String.format("JV-%d-%05d", year, seq);
    }

    private String countryCodeOf(EntityManager em, Long agent) {
        List<?> rows = em.createNativeQuery("SELECT COUNTRY FROM AGENT_MAST WHERE AGENT_I_ID = ?1")
                .setParameter(1, agent)
                .setMaxResults(1)
                .getResultList();
        return CountryCodes.fromName(rows.isEmpty() ? null : (String) rows.get(0));
    }

    // ════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════

    private void recalcTotals() {
        totalDebit  = lines.stream().filter(l -> "DR".equals(l.getDrCr()))
                          .map(DayLedger::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        totalCredit = lines.stream().filter(l -> "CR".equals(l.getDrCr()))
                          .map(DayLedger::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void clearLineInputs() {
        lineAccountId   = null;
        lineDrCr        = "DR";
        lineAmount      = BigDecimal.ZERO;
        lineNarration   = null;
    }

    private void reset() {
        lines = new ArrayList<>();
        totalDebit = totalCredit = BigDecimal.ZERO;
        narration = null;
        loadRecentEntries();
    }

    private AccountOption findAccount(Long id) {
        return accounts.stream().filter(a -> id.equals(a.getId())).findFirst().orElse(null);
    }

    private void loadLookups() {
        final Long agent = agentId;
        try {
            Lookups lookups = db.execute(em -> readLookups(em, agent));
            accounts = lookups.accounts;
            currencies = lookups.currencies;
            agents = lookups.agents;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to load journal entry lookups", e);
            addError("تعذّر تحميل قوائم الاختيار (الحسابات والعملات والفروع).");
            accounts = List.of();
            currencies = List.of();
            agents = List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static Lookups readLookups(EntityManager em, Long agent) {
        // الحسابات النشطة (الحالة قد تكون بحرف كبير أو صغير في القاعدة)
        String sql = "SELECT ACT_I_ID, ACT_C_TYPE, CUST_I_NO, CURRENCY_I_ID, AGENT_I_ID, ACT_C_NAME "
                + "FROM ACCOUNT_MAST WHERE UPPER(ACT_C_ACTSTS) = 'A'"
                + (agent != null ? " AND AGENT_I_ID = ?1" : "")
                + " ORDER BY ACT_C_NAME";
        Query accountQuery = em.createNativeQuery(sql);
        if (agent != null) accountQuery.setParameter(1, agent);
        List<Object[]> accountRows = accountQuery.setMaxResults(ACCOUNT_LIST_LIMIT).getResultList();

        List<AccountOption> options = new ArrayList<>();
        for (Object[] r : accountRows) {
            options.add(new AccountOption(asLong(r[0]), (String) r[1], asLong(r[2]),
                    asLong(r[3]), asLong(r[4]), (String) r[5]));
        }

        List<Object[]> currencyRows = em.createNativeQuery(
                "SELECT CURRENCY_I_ID, CURRENCY_C_DESC, CURRENCY_C_SYMBOL FROM CURRENCY_MAST ORDER BY CURRENCY_C_DESC")
                .getResultList();
        List<Object[]> agentRows = em.createNativeQuery(
                "SELECT AGENT_I_ID, AGENT_C_NAME FROM AGENT_MAST ORDER BY AGENT_C_NAME")
                .getResultList();

        return new Lookups(options, currencyRows, agentRows);
    }

    private static Long asLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private void loadRecentEntries() {
        try {
            recentEntries = db.execute(em -> em.createQuery(
                    "SELECT j FROM JournalEntry j ORDER BY j.createdAt DESC", JournalEntry.class)
                    .setMaxResults(20)
                    .getResultList());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to load recent journal entries", e);
            recentEntries = List.of();
        }
    }

    // ─── الجلسة والرسائل (package-private: قابلة للاستبدال في الاختبارات) ───

    UserSession getCurrentUser() {
        FacesContext ctx = FacesContext.getCurrentInstance();
        if (ctx == null) return null;
        return (UserSession) ctx.getExternalContext().getRequestMap().get(AuthFilter.SESSION_ATTR);
    }

    void addError(String msg) {
        addMessage(FacesMessage.SEVERITY_ERROR, msg);
    }

    void addInfo(String msg) {
        addMessage(FacesMessage.SEVERITY_INFO, msg);
    }

    private void addMessage(FacesMessage.Severity severity, String msg) {
        FacesContext ctx = FacesContext.getCurrentInstance();
        if (ctx == null) {
            LOG.log(Level.FINE, msg);
            return;
        }
        ctx.addMessage(null, new FacesMessage(severity, msg, null));
    }

    // ─── Computed Properties ────────────────────────
    public boolean isBalanced()  { return totalDebit.compareTo(totalCredit) == 0; }
    public BigDecimal getDifference() { return totalDebit.subtract(totalCredit).abs(); }
    public String getBalanceStatus() {
        return isBalanced() ? "متوازن ✓" : "فرق: " + getDifference();
    }

    /** اسم الحساب لسطر في الجدول (بالنوع + رقم العميل). */
    public String getAccountName(DayLedger line) {
        if (line == null) return "";
        return accounts.stream()
                .filter(a -> a.getType() != null && a.getType().equals(line.getAccountType())
                        && a.getCustomerId() != null && a.getCustomerId().equals(line.getCustomerId()))
                .map(AccountOption::getLabel)
                .findFirst()
                .orElse(line.getAccountType());
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
    /** الرقم الفعلي يُولَّد داخل معاملة الحفظ، وتُعرض هنا عبارة توضيحية. */
    public String getReference()                  { return REFERENCE_PLACEHOLDER; }
    public Long getLineAccountId()                { return lineAccountId; }
    public void setLineAccountId(Long v)          { this.lineAccountId = v; }
    public String getLineDrCr()                   { return lineDrCr; }
    public void setLineDrCr(String v)             { this.lineDrCr = v; }
    public BigDecimal getLineAmount()             { return lineAmount; }
    public void setLineAmount(BigDecimal v)       { this.lineAmount = v; }
    public String getLineNarration()              { return lineNarration; }
    public void setLineNarration(String v)        { this.lineNarration = v; }
    public List<DayLedger> getLines()             { return lines; }
    public BigDecimal getTotalDebit()             { return totalDebit; }
    public BigDecimal getTotalCredit()            { return totalCredit; }
    public List<AccountOption> getAccounts()      { return accounts; }
    public List<Object[]> getCurrencies()         { return currencies; }
    public List<Object[]> getAgents()             { return agents; }
    public List<JournalEntry> getRecentEntries()  { return recentEntries; }
    public boolean isViewMode()                   { return viewMode; }
    public void setViewMode(boolean v)            { this.viewMode = v; }
}
