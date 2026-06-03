package com.joud.accounting.service;

import com.joud.accounting.entity.*;
import com.joud.accounting.entity.GeneralLedgerMaster.DrCr;
import com.joud.accounting.entity.JournalEntry.JvStatus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.*;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * GeneralLedgerService — ماكنة المحاسبة الرئيسية
 *
 * تتولى:
 *   1. ترحيل القيود (Posting) وتحديث GENLDG_MAST تلقائياً
 *   2. إلغاء القيود (Reversal)
 *   3. استخراج التقارير الختامية (ميزان، ميزانية، دخل)
 *   4. إدارة الفترات المحاسبية
 *   5. تحويل العملات عبر EXCH_RATE_HIST
 */
@ApplicationScoped
public class GeneralLedgerService {

    private static final Logger LOG = Logger.getLogger(GeneralLedgerService.class.getName());

    @PersistenceContext
    private EntityManager em;

    // ════════════════════════════════════════════════
    // 1. ترحيل القيود — Posting
    // ════════════════════════════════════════════════

    /**
     * ترحيل قيد معتمد:
     *   1. تحقق من توازن القيد
     *   2. تحقق من أن الفترة مفتوحة
     *   3. سجّل كل سطر في DAY_LEDG
     *   4. حدِّث GENLDG_MAST لكل حساب
     *   5. غيّر حالة القيد إلى Approved
     */
    @Transactional
    public void postJournalEntry(Long jvId, Long approvedByUserId) {
        JournalEntry jv = em.find(JournalEntry.class, jvId);
        if (jv == null)
            throw new AccountingException("القيد غير موجود: " + jvId);
        if (!jv.canApprove())
            throw new AccountingException("لا يمكن اعتماد القيد بحالته الحالية: " + jv.getStatus());
        if (!jv.isBalanced())
            throw new AccountingException("القيد غير متوازن. الفرق: " + jv.getImbalance());

        // تحقق من أن الفترة مفتوحة
        ensurePeriodOpen(jv.getAgentId(), jv.getDate());

        // احسب سعر الصرف إذا لم يكن محدداً
        BigDecimal exchRate = resolveExchangeRate(
            jv.getCurrencyId(), jv.getDate(), jv.getAgentId()
        );
        jv.setExchangeRate(exchRate);

        // رحّل كل سطر وحدّث GENLDG_MAST
        for (DayLedger line : jv.getLines()) {
            line.setExchangeRate(exchRate);
            line.calculateBaseAmount();
            line.setStatus(DayLedger.TrnStatus.A);
            em.merge(line);

            updateGeneralLedger(line, jv.getCountryCode());
        }

        jv.setStatus(JvStatus.A);
        jv.setApprovedBy(approvedByUserId);
        jv.setApprovedAt(LocalDate.now());
        em.merge(jv);

        LOG.info("Posted JV: " + jv.getReference() + " Agent: " + jv.getAgentId());
    }

    // ════════════════════════════════════════════════
    // 2. تحديث GENLDG_MAST
    // ════════════════════════════════════════════════

    /**
     * يُحدِّث سجل الأرصدة التراكمية عند ترحيل سطر قيد.
     * إذا لم يكن السجل موجوداً يُنشئه تلقائياً.
     */
    private void updateGeneralLedger(DayLedger line, String countryCode) {
        int year  = line.getDate().getYear();
        int month = line.getDate().getMonthValue();

        GeneralLedgerMaster gen = findOrCreateGenLdg(
            line.getAccountType(), line.getAgentId(),
            countryCode, year, month,
            line.getCurrencyId(), line.getExchangeRate()
        );

        DrCr drCr = "DR".equalsIgnoreCase(line.getDrCr()) ? DrCr.D : DrCr.C;
        gen.postTransaction(line.getAmount(), line.getAmountBase(), drCr);
        em.merge(gen);
    }

    /**
     * ابحث عن سجل GENLDG أو أنشئه
     */
    private GeneralLedgerMaster findOrCreateGenLdg(
            String accountType, Long agentId, String countryCode,
            int year, int month, Long currencyId, BigDecimal exchRate) {

        List<GeneralLedgerMaster> results = em
            .createNamedQuery("GenLdg.findByAccountPeriodAgent", GeneralLedgerMaster.class)
            .setParameter("accountId",  accountType)
            .setParameter("agentId",    agentId)
            .setParameter("year",       year)
            .setParameter("month",      month)
            .setParameter("currencyId", currencyId)
            .getResultList();

        if (!results.isEmpty()) return results.get(0);

        // أنشئ سجلاً جديداً — رصيد الافتتاح من إغلاق الشهر السابق
        GeneralLedgerMaster gen = new GeneralLedgerMaster();
        gen.setId(generateId("GENLDG_MAST"));
        gen.setAccountType(accountType);
        gen.setAgentId(agentId);
        gen.setCountryCode(countryCode);
        gen.setYear(year);
        gen.setMonth(month);
        gen.setCurrencyId(currencyId);
        gen.setExchangeRate(exchRate != null ? exchRate : BigDecimal.ONE);
        gen.setLastUpdate(LocalDate.now());

        // انقل رصيد الإغلاق من الشهر السابق كافتتاح
        carryForwardOpeningBalance(gen, accountType, agentId, currencyId, year, month);

        em.persist(gen);
        return gen;
    }

    /**
     * نقل رصيد الإغلاق من الفترة السابقة كافتتاح للفترة الجديدة
     */
    private void carryForwardOpeningBalance(
            GeneralLedgerMaster gen, String accountType, Long agentId,
            Long currencyId, int year, int month) {

        List<GeneralLedgerMaster> prev = em
            .createNamedQuery("GenLdg.lastClosingBalance", GeneralLedgerMaster.class)
            .setParameter("accountId",  accountType)
            .setParameter("agentId",    agentId)
            .setParameter("currencyId", currencyId)
            .setParameter("year",       year)
            .setParameter("month",      month)
            .setMaxResults(1)
            .getResultList();

        if (!prev.isEmpty()) {
            GeneralLedgerMaster last = prev.get(0);
            gen.setOpeningBalance(last.getClosingBalance());
            gen.setOpeningDrCr(last.getClosingDrCr());
            gen.setOpeningBalanceBase(last.getClosingBalanceBase());
        }
    }

    // ════════════════════════════════════════════════
    // 3. إلغاء القيد — Reversal
    // ════════════════════════════════════════════════

    @Transactional
    public void voidJournalEntry(Long jvId, Long userId, String reason) {
        JournalEntry jv = em.find(JournalEntry.class, jvId);
        if (jv == null)
            throw new AccountingException("القيد غير موجود: " + jvId);
        if (!jv.canVoid())
            throw new AccountingException("لا يمكن إلغاء القيد بحالته: " + jv.getStatus());

        ensurePeriodOpen(jv.getAgentId(), jv.getDate());

        // اعكس كل سطر في GENLDG_MAST
        for (DayLedger line : jv.getLines()) {
            List<GeneralLedgerMaster> genList = em
                .createNamedQuery("GenLdg.findByAccountPeriodAgent", GeneralLedgerMaster.class)
                .setParameter("accountId",  line.getAccountType())
                .setParameter("agentId",    line.getAgentId())
                .setParameter("year",       line.getDate().getYear())
                .setParameter("month",      line.getDate().getMonthValue())
                .setParameter("currencyId", line.getCurrencyId())
                .getResultList();

            if (!genList.isEmpty()) {
                DrCr drCr = "DR".equalsIgnoreCase(line.getDrCr()) ? DrCr.D : DrCr.C;
                genList.get(0).reverseTransaction(line.getAmount(), line.getAmountBase(), drCr);
                em.merge(genList.get(0));
            }

            line.setStatus(DayLedger.TrnStatus.V);
            em.merge(line);
        }

        jv.setStatus(JvStatus.V);
        jv.setNarration((jv.getNarration() != null ? jv.getNarration() + " | " : "") + "ملغى: " + reason);
        em.merge(jv);

        LOG.info("Voided JV: " + jv.getReference() + " Reason: " + reason);
    }

    // ════════════════════════════════════════════════
    // 4. التقارير — Reports
    // ════════════════════════════════════════════════

    /** ميزان المراجعة — مستوى الفرع */
    public List<GeneralLedgerMaster> getTrialBalanceByAgent(
            Long agentId, int year, int month) {
        return em.createNamedQuery("GenLdg.trialBalanceByAgent", GeneralLedgerMaster.class)
            .setParameter("agentId", agentId)
            .setParameter("year",    year)
            .setParameter("month",   month)
            .getResultList();
    }

    /** ميزان المراجعة — مستوى البلد */
    public List<GeneralLedgerMaster> getTrialBalanceByCountry(
            String countryCode, int year, int month) {
        return em.createNamedQuery("GenLdg.trialBalanceByCountry", GeneralLedgerMaster.class)
            .setParameter("countryCode", countryCode)
            .setParameter("year",        year)
            .setParameter("month",       month)
            .getResultList();
    }

    /** ميزان المراجعة — مستوى الشركة (كل البلدان) */
    public List<Object[]> getTrialBalanceCompany(int year, int month) {
        return em.createNativeQuery(
            "SELECT * FROM V_TRIAL_BALANCE_COMPANY " +
            "WHERE GEN_I_YEAR = :year AND GEN_I_MONTH = :month " +
            "ORDER BY ACT_C_TYPE"
        ).setParameter("year", year).setParameter("month", month).getResultList();
    }

    /** الميزانية العمومية — مستوى الشركة */
    public List<Object[]> getBalanceSheetCompany(int year, int month) {
        return em.createNativeQuery(
            "SELECT * FROM V_BALANCE_SHEET_COMPANY " +
            "WHERE GEN_I_YEAR = :year AND GEN_I_MONTH = :month"
        ).setParameter("year", year).setParameter("month", month).getResultList();
    }

    /** قائمة الدخل — مستوى الشركة */
    public List<Object[]> getIncomeStatementCompany(int year, int month) {
        return em.createNativeQuery(
            "SELECT * FROM V_INCOME_STATEMENT_COMPANY " +
            "WHERE GEN_I_YEAR = :year AND GEN_I_MONTH = :month"
        ).setParameter("year", year).setParameter("month", month).getResultList();
    }

    // ════════════════════════════════════════════════
    // 5. إدارة الفترات
    // ════════════════════════════════════════════════

    @Transactional
    public void closePeriod(Long agentId, int year, int month, Long userId) {
        AccountingPeriod period = findPeriod(agentId, year, month)
            .orElseThrow(() -> new AccountingException("الفترة غير موجودة"));
        if (!period.isOpen())
            throw new AccountingException("الفترة ليست مفتوحة");

        period.setStatus(AccountingPeriod.PeriodStatus.C);
        period.setDateClosed(LocalDate.now());
        period.setClosedBy(userId);
        em.merge(period);

        // افتح الفترة التالية تلقائياً
        openNextPeriod(agentId, year, month);
        LOG.info("Closed period: " + year + "/" + month + " for agent: " + agentId);
    }

    private void openNextPeriod(Long agentId, int year, int month) {
        int nextMonth = month == 12 ? 1  : month + 1;
        int nextYear  = month == 12 ? year + 1 : year;

        if (findPeriod(agentId, nextYear, nextMonth).isEmpty()) {
            AccountingPeriod next = new AccountingPeriod();
            next.setId(generateId("ACCOUNTING_PERIOD"));
            next.setAgentId(agentId);
            next.setYear(nextYear);
            next.setMonth(nextMonth);
            next.setDateFrom(LocalDate.of(nextYear, nextMonth, 1));
            next.setDateTo(LocalDate.of(nextYear, nextMonth, 1).withDayOfMonth(
                LocalDate.of(nextYear, nextMonth, 1).lengthOfMonth()));
            next.setStatus(AccountingPeriod.PeriodStatus.O);
            em.persist(next);
        }
    }

    public Optional<AccountingPeriod> findPeriod(Long agentId, int year, int month) {
        List<AccountingPeriod> result = em
            .createNamedQuery("AccountingPeriod.findByYearMonth", AccountingPeriod.class)
            .setParameter("agentId", agentId)
            .setParameter("year",    year)
            .setParameter("month",   month)
            .getResultList();
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    private void ensurePeriodOpen(Long agentId, LocalDate date) {
        int year = date.getYear(), month = date.getMonthValue();
        AccountingPeriod period = findPeriod(agentId, year, month)
            .orElseThrow(() -> new AccountingException(
                "لا توجد فترة محاسبية مفتوحة لـ " + year + "/" + month));
        if (!period.isOpen())
            throw new AccountingException("الفترة المحاسبية " + year + "/" + month + " مغلقة");
    }

    // ════════════════════════════════════════════════
    // 6. سعر الصرف
    // ════════════════════════════════════════════════

    /**
     * البحث عن أقرب سعر صرف متاح قبل أو في تاريخ الحركة
     */
    public BigDecimal resolveExchangeRate(Long currencyId, LocalDate date, Long agentId) {
        // إذا كانت العملة هي العملة الأساسية — سعر الصرف = 1
        // (يتحقق منها الـ Service العليا)

        List<ExchangeRateHistory> rates = em
            .createNamedQuery("ExchangeRate.findByDateAndCurrency", ExchangeRateHistory.class)
            .setParameter("currencyId",     currencyId)
            .setParameter("baseCurrencyId", getBaseCurrencyId(agentId))
            .setParameter("date",           date)
            .setParameter("type",           ExchangeRateHistory.RateType.M)
            .setMaxResults(1)
            .getResultList();

        if (rates.isEmpty()) {
            LOG.warning("No exchange rate found for currency: " + currencyId + " date: " + date);
            return BigDecimal.ONE; // افتراضي
        }

        return rates.get(0).getRate();
    }

    private Long getBaseCurrencyId(Long agentId) {
        // يُجلب من AGENT_BROFILE — مبسط هنا
        try {
            return (Long) em.createNativeQuery(
                "SELECT CURRENCY_I_ID FROM AGENT_BROFILE WHERE AGENT_I_ID = :aid")
                .setParameter("aid", agentId)
                .setMaxResults(1)
                .getSingleResult();
        } catch (Exception e) {
            return 1L; // العملة الأساسية الافتراضية
        }
    }

    // ════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════

    private Long generateId(String tableName) {
        try {
            return (Long) em.createQuery(
                "SELECT k.generatedId FROM KeyGeneration k WHERE k.tableName = :t")
                .setParameter("t", tableName)
                .getSingleResult();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    // ─── Exception ─────────────────────────────────

    public static class AccountingException extends RuntimeException {
        public AccountingException(String msg) { super(msg); }
    }
}
