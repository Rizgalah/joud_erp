package com.joud.accounting.service;

import com.joud.accounting.entity.AccountingPeriod;
import com.joud.accounting.entity.DayLedger;
import com.joud.accounting.entity.ExchangeRateHistory;
import com.joud.accounting.entity.GeneralLedgerMaster;
import com.joud.accounting.entity.GeneralLedgerMaster.DrCr;
import com.joud.accounting.entity.JournalEntry;
import com.joud.accounting.entity.JournalEntry.JvStatus;
import com.joud.core.db.JoudRepository;
import com.joud.core.event.JournalPostedEvent;
import com.joud.core.id.SequenceAllocator;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

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
 *
 * كل عملية كتابة تتم في معاملة واحدة عبر JoudRepository (فإما تُحفظ كلها
 * أو لا شيء)، لأن Tomcat لا يوفر JTA فلا يعمل @Transactional فيه.
 */
@ApplicationScoped
public class GeneralLedgerService {

    private static final Logger LOG = Logger.getLogger(GeneralLedgerService.class.getName());

    /** عملة افتراضية إن لم تُحدَّد عملة للفرع في AGENT_MAST. */
    private static final long DEFAULT_BASE_CURRENCY_ID = 1L;

    @Inject
    private JoudRepository db;

    @Inject
    private Event<JournalPostedEvent> postedEvent;

    // ════════════════════════════════════════════════
    // 1. ترحيل القيود — Posting
    // ════════════════════════════════════════════════

    /**
     * ترحيل قيد معتمد، في معاملة واحدة:
     *   1. تحقق من توازن القيد ووجود أسطر
     *   2. تحقق من أن الفترة مفتوحة
     *   3. حدّد سعر الصرف وسجّل كل سطر
     *   4. حدِّث GENLDG_MAST لكل حساب
     *   5. غيّر حالة القيد إلى Approved
     *   6. بعد نجاح الحفظ فقط: أطلق JournalPostedEvent (إشعار بريد للمراجعة)
     *
     * يُقفل صف القيد وصفوف الأرصدة أثناء المعاملة، فلا يضيع تحديث عند
     * ترحيل قيدين متزامنين على الحساب نفسه.
     */
    public void postJournalEntry(Long jvId, Long approvedByUserId) {
        JournalPostedEvent posted = db.executeInTransaction(em -> {
            JournalEntry jv = em.find(JournalEntry.class, jvId, LockModeType.PESSIMISTIC_WRITE);
            if (jv == null)
                throw new AccountingException("القيد غير موجود: " + jvId);
            if (!jv.canApprove())
                throw new AccountingException("لا يمكن اعتماد القيد بحالته الحالية: " + jv.getStatus());
            if (!jv.isBalanced())
                throw new AccountingException("القيد غير متوازن. الفرق: " + jv.getImbalance());
            if (jv.getLines().isEmpty())
                throw new AccountingException("القيد بلا أسطر: " + jvId);

            // تحقق من أن الفترة مفتوحة
            ensurePeriodOpen(em, jv.getAgentId(), jv.getDate());

            // احسب سعر الصرف
            BigDecimal exchRate = resolveExchangeRate(em, jv.getCurrencyId(), jv.getDate(), jv.getAgentId());
            jv.setExchangeRate(exchRate);

            // رحّل كل سطر وحدّث GENLDG_MAST (الكيانات مُدارة: تُحفظ عند الإتمام)
            for (DayLedger line : jv.getLines()) {
                line.setExchangeRate(exchRate);
                line.calculateBaseAmount();
                line.setStatus(DayLedger.TrnStatus.A);

                updateGeneralLedger(em, line, jv.getCountryCode());
            }

            jv.setStatus(JvStatus.A);
            jv.setApprovedBy(approvedByUserId);
            jv.setApprovedAt(LocalDate.now());

            LOG.info("Posted JV: " + jv.getReference() + " Agent: " + jv.getAgentId());

            return new JournalPostedEvent(jv.getId(), jv.getReference(), jv.getAgentId(),
                    jv.getCountryCode(), jv.getDate(), jv.getTotalDebit(), jv.getTotalCredit(),
                    jv.getCurrencyId(), approvedByUserId);
        });

        // الحفظ تم (commit)، فالإشعار عن قيد موجود فعلاً. فشله لا يؤثر على الترحيل.
        publishPosted(posted);
    }

    private void publishPosted(JournalPostedEvent event) {
        try {
            postedEvent.fireAsync(event);
        } catch (RuntimeException e) {
            LOG.log(java.util.logging.Level.WARNING, "Could not publish JournalPostedEvent", e);
        }
    }

    // ════════════════════════════════════════════════
    // 2. تحديث GENLDG_MAST
    // ════════════════════════════════════════════════

    /**
     * يُحدِّث سجل الأرصدة التراكمية عند ترحيل سطر قيد.
     * إذا لم يكن السجل موجوداً يُنشئه تلقائياً.
     */
    private void updateGeneralLedger(EntityManager em, DayLedger line, String countryCode) {
        int year = line.getDate().getYear();
        int month = line.getDate().getMonthValue();
        Long accountId = resolveAccountId(em, line);

        GeneralLedgerMaster gen = findGenLdg(em, accountId, line.getAgentId(), year, month, line.getCurrencyId());
        if (gen == null) {
            gen = createGenLdg(em, accountId, line, countryCode, year, month);
        }

        DrCr drCr = "DR".equalsIgnoreCase(line.getDrCr()) ? DrCr.D : DrCr.C;
        gen.postTransaction(line.getAmount(), line.getAmountBase(), drCr);
    }

    /**
     * معرّف الحساب (ACT_I_ID) من ACCOUNT_MAST بمفتاح: الفرع + نوع الحساب +
     * رقم العميل + العملة. سطر DAY_LEDG لا يحمل المعرّف مباشرة.
     */
    private Long resolveAccountId(EntityManager em, DayLedger line) {
        if (line.getCustomerId() == null)
            throw new AccountingException("سطر القيد بلا رقم عميل/حساب، لا يمكن تحديد الحساب (نوع: "
                    + line.getAccountType() + ")");

        List<?> ids = em.createNativeQuery(
                "SELECT ACT_I_ID FROM ACCOUNT_MAST "
                        + "WHERE AGENT_I_ID = ?1 AND ACT_C_TYPE = ?2 AND CUST_I_NO = ?3 AND CURRENCY_I_ID = ?4")
                .setParameter(1, line.getAgentId())
                .setParameter(2, line.getAccountType())
                .setParameter(3, line.getCustomerId())
                .setParameter(4, line.getCurrencyId())
                .setMaxResults(1)
                .getResultList();

        if (ids.isEmpty())
            throw new AccountingException("لا يوجد حساب مطابق: نوع " + line.getAccountType()
                    + "، عميل " + line.getCustomerId() + "، عملة " + line.getCurrencyId());

        return ((Number) ids.get(0)).longValue();
    }

    /** سجل الأرصدة لحساب وفترة، مقفول للتحديث، أو null إن لم يوجد. */
    private GeneralLedgerMaster findGenLdg(EntityManager em, Long accountId, Long agentId,
            int year, int month, Long currencyId) {
        List<GeneralLedgerMaster> results = em
                .createNamedQuery("GenLdg.findByAccountPeriodAgent", GeneralLedgerMaster.class)
                .setParameter("accountId", accountId)
                .setParameter("agentId", agentId)
                .setParameter("year", year)
                .setParameter("month", month)
                .setParameter("currencyId", currencyId)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    /** ينشئ سجلاً جديداً، ورصيد الافتتاح من إغلاق الفترة السابقة. */
    private GeneralLedgerMaster createGenLdg(EntityManager em, Long accountId, DayLedger line,
            String countryCode, int year, int month) {
        BigDecimal exchRate = line.getExchangeRate();

        GeneralLedgerMaster gen = new GeneralLedgerMaster();
        gen.setId(SequenceAllocator.nextId(em, "GENLDG_MAST", "GENLDG_MAST", "GEN_I_ID"));
        gen.setAccountId(accountId);
        gen.setAccountType(line.getAccountType());
        gen.setAgentId(line.getAgentId());
        gen.setCountryCode(countryCode);
        gen.setYear(year);
        gen.setMonth(month);
        gen.setCurrencyId(line.getCurrencyId());
        gen.setExchangeRate(exchRate != null ? exchRate : BigDecimal.ONE);
        gen.setLastUpdate(LocalDate.now());

        // انقل رصيد الإغلاق من الفترة السابقة كافتتاح
        List<GeneralLedgerMaster> prev = em
                .createNamedQuery("GenLdg.lastClosingBalance", GeneralLedgerMaster.class)
                .setParameter("accountId", accountId)
                .setParameter("agentId", line.getAgentId())
                .setParameter("currencyId", line.getCurrencyId())
                .setParameter("year", year)
                .setParameter("month", month)
                .setMaxResults(1)
                .getResultList();

        if (!prev.isEmpty()) {
            GeneralLedgerMaster last = prev.get(0);
            gen.setOpeningBalance(last.getClosingBalance());
            gen.setOpeningDrCr(last.getClosingDrCr());
            gen.setOpeningBalanceBase(last.getClosingBalanceBase());
        }

        em.persist(gen);
        return gen;
    }

    // ════════════════════════════════════════════════
    // 3. إلغاء القيد — Reversal
    // ════════════════════════════════════════════════

    public void voidJournalEntry(Long jvId, Long userId, String reason) {
        db.executeInTransaction(em -> {
            JournalEntry jv = em.find(JournalEntry.class, jvId, LockModeType.PESSIMISTIC_WRITE);
            if (jv == null)
                throw new AccountingException("القيد غير موجود: " + jvId);
            if (!jv.canVoid())
                throw new AccountingException("لا يمكن إلغاء القيد بحالته: " + jv.getStatus());

            ensurePeriodOpen(em, jv.getAgentId(), jv.getDate());

            // اعكس كل سطر في GENLDG_MAST
            for (DayLedger line : jv.getLines()) {
                Long accountId = resolveAccountId(em, line);
                GeneralLedgerMaster gen = findGenLdg(em, accountId, line.getAgentId(),
                        line.getDate().getYear(), line.getDate().getMonthValue(), line.getCurrencyId());

                if (gen != null) {
                    DrCr drCr = "DR".equalsIgnoreCase(line.getDrCr()) ? DrCr.D : DrCr.C;
                    gen.reverseTransaction(line.getAmount(), line.getAmountBase(), drCr);
                } else {
                    LOG.warning("No GL record to reverse for JV " + jv.getReference() + " line " + line.getId());
                }

                line.setStatus(DayLedger.TrnStatus.V);
            }

            jv.setStatus(JvStatus.V);
            jv.setNarration((jv.getNarration() != null ? jv.getNarration() + " | " : "") + "ملغى: " + reason);

            LOG.info("Voided JV: " + jv.getReference() + " Reason: " + reason);
        });
    }

    // ════════════════════════════════════════════════
    // 4. التقارير — Reports
    // ════════════════════════════════════════════════

    /** ميزان المراجعة — مستوى الفرع */
    public List<GeneralLedgerMaster> getTrialBalanceByAgent(Long agentId, int year, int month) {
        return db.execute(em -> em
                .createNamedQuery("GenLdg.trialBalanceByAgent", GeneralLedgerMaster.class)
                .setParameter("agentId", agentId)
                .setParameter("year", year)
                .setParameter("month", month)
                .getResultList());
    }

    /** ميزان المراجعة — مستوى البلد */
    public List<GeneralLedgerMaster> getTrialBalanceByCountry(String countryCode, int year, int month) {
        return db.execute(em -> em
                .createNamedQuery("GenLdg.trialBalanceByCountry", GeneralLedgerMaster.class)
                .setParameter("countryCode", countryCode)
                .setParameter("year", year)
                .setParameter("month", month)
                .getResultList());
    }

    /** ميزان المراجعة — مستوى الشركة (كل البلدان) */
    @SuppressWarnings("unchecked")
    public List<Object[]> getTrialBalanceCompany(int year, int month) {
        return db.execute(em -> (List<Object[]>) em.createNativeQuery(
                "SELECT * FROM V_TRIAL_BALANCE_COMPANY "
                        + "WHERE GEN_I_YEAR = :year AND GEN_I_MONTH = :month "
                        + "ORDER BY ACT_C_TYPE")
                .setParameter("year", year)
                .setParameter("month", month)
                .getResultList());
    }

    /** الميزانية العمومية — مستوى الشركة */
    @SuppressWarnings("unchecked")
    public List<Object[]> getBalanceSheetCompany(int year, int month) {
        return db.execute(em -> (List<Object[]>) em.createNativeQuery(
                "SELECT * FROM V_BALANCE_SHEET_COMPANY "
                        + "WHERE GEN_I_YEAR = :year AND GEN_I_MONTH = :month")
                .setParameter("year", year)
                .setParameter("month", month)
                .getResultList());
    }

    /** قائمة الدخل — مستوى الشركة */
    @SuppressWarnings("unchecked")
    public List<Object[]> getIncomeStatementCompany(int year, int month) {
        return db.execute(em -> (List<Object[]>) em.createNativeQuery(
                "SELECT * FROM V_INCOME_STATEMENT_COMPANY "
                        + "WHERE GEN_I_YEAR = :year AND GEN_I_MONTH = :month")
                .setParameter("year", year)
                .setParameter("month", month)
                .getResultList());
    }

    // ════════════════════════════════════════════════
    // 5. إدارة الفترات
    // ════════════════════════════════════════════════

    public void closePeriod(Long agentId, int year, int month, Long userId) {
        db.executeInTransaction(em -> {
            AccountingPeriod period = findPeriod(em, agentId, year, month)
                    .orElseThrow(() -> new AccountingException("الفترة غير موجودة"));
            if (!period.isOpen())
                throw new AccountingException("الفترة ليست مفتوحة");

            period.setStatus(AccountingPeriod.PeriodStatus.C);
            period.setDateClosed(LocalDate.now());
            period.setClosedBy(userId);

            // افتح الفترة التالية تلقائياً
            openNextPeriod(em, agentId, year, month);
            LOG.info("Closed period: " + year + "/" + month + " for agent: " + agentId);
        });
    }

    private void openNextPeriod(EntityManager em, Long agentId, int year, int month) {
        int nextMonth = month == 12 ? 1 : month + 1;
        int nextYear = month == 12 ? year + 1 : year;

        if (findPeriod(em, agentId, nextYear, nextMonth).isEmpty()) {
            LocalDate first = LocalDate.of(nextYear, nextMonth, 1);

            AccountingPeriod next = new AccountingPeriod();
            next.setId(SequenceAllocator.nextId(em, "ACCOUNTING_PERIOD", "ACCOUNTING_PERIOD", "PERIOD_I_ID"));
            next.setAgentId(agentId);
            next.setYear(nextYear);
            next.setMonth(nextMonth);
            next.setDateFrom(first);
            next.setDateTo(first.withDayOfMonth(first.lengthOfMonth()));
            next.setStatus(AccountingPeriod.PeriodStatus.O);
            em.persist(next);
        }
    }

    public Optional<AccountingPeriod> findPeriod(Long agentId, int year, int month) {
        return db.execute(em -> findPeriod(em, agentId, year, month));
    }

    private Optional<AccountingPeriod> findPeriod(EntityManager em, Long agentId, int year, int month) {
        List<AccountingPeriod> result = em
                .createNamedQuery("AccountingPeriod.findByYearMonth", AccountingPeriod.class)
                .setParameter("agentId", agentId)
                .setParameter("year", year)
                .setParameter("month", month)
                .getResultList();
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    private void ensurePeriodOpen(EntityManager em, Long agentId, LocalDate date) {
        int year = date.getYear(), month = date.getMonthValue();
        AccountingPeriod period = findPeriod(em, agentId, year, month)
                .orElseThrow(() -> new AccountingException(
                        "لا توجد فترة محاسبية مفتوحة لـ " + year + "/" + month));
        if (!period.isOpen())
            throw new AccountingException("الفترة المحاسبية " + year + "/" + month + " مغلقة");
    }

    // ════════════════════════════════════════════════
    // 6. سعر الصرف
    // ════════════════════════════════════════════════

    /** أقرب سعر صرف متاح قبل أو في تاريخ الحركة (1 إن لم يوجد). */
    public BigDecimal resolveExchangeRate(Long currencyId, LocalDate date, Long agentId) {
        return db.execute(em -> resolveExchangeRate(em, currencyId, date, agentId));
    }

    private BigDecimal resolveExchangeRate(EntityManager em, Long currencyId, LocalDate date, Long agentId) {
        List<ExchangeRateHistory> rates = em
                .createNamedQuery("ExchangeRate.findByDateAndCurrency", ExchangeRateHistory.class)
                .setParameter("currencyId", currencyId)
                .setParameter("baseCurrencyId", getBaseCurrencyId(em, agentId))
                .setParameter("date", date)
                .setParameter("type", ExchangeRateHistory.RateType.M)
                .setMaxResults(1)
                .getResultList();

        if (rates.isEmpty()) {
            LOG.warning("No exchange rate found for currency: " + currencyId + " date: " + date);
            return BigDecimal.ONE; // افتراضي
        }

        return rates.get(0).getRate();
    }

    /**
     * العملة الأساسية للفرع: AGENT_MAST.CURRENCY_I_ID (العملة المسجّلة للفرع).
     * الاستعلام يستخدم getResultList كي لا يرفع استثناء عند غياب الصف، فاستثناء
     * داخل معاملة PostgreSQL كان سيُفسدها.
     */
    private Long getBaseCurrencyId(EntityManager em, Long agentId) {
        List<?> rows = em.createNativeQuery("SELECT CURRENCY_I_ID FROM AGENT_MAST WHERE AGENT_I_ID = ?1")
                .setParameter(1, agentId)
                .setMaxResults(1)
                .getResultList();
        if (rows.isEmpty() || rows.get(0) == null) {
            return DEFAULT_BASE_CURRENCY_ID;
        }
        return ((Number) rows.get(0)).longValue();
    }

    // ─── Exception ─────────────────────────────────

    public static class AccountingException extends RuntimeException {
        public AccountingException(String msg) {
            super(msg);
        }
    }
}
