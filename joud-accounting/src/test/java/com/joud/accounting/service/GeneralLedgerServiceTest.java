package com.joud.accounting.service;

import com.joud.accounting.entity.AccountingPeriod;
import com.joud.accounting.entity.AccountingPeriod.PeriodStatus;
import com.joud.accounting.entity.DayLedger;
import com.joud.accounting.entity.ExchangeRateHistory;
import com.joud.accounting.entity.GeneralLedgerMaster;
import com.joud.accounting.entity.GeneralLedgerMaster.DrCr;
import com.joud.accounting.entity.JournalEntry;
import com.joud.accounting.entity.JournalEntry.JvStatus;
import com.joud.accounting.service.GeneralLedgerService.AccountingException;
import com.joud.core.db.JoudRepository;
import com.joud.core.event.JournalPostedEvent;

import jakarta.enterprise.event.Event;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * يختبر منطق الترحيل والإلغاء والفترات دون قاعدة بيانات: الـ JoudRepository
 * يُشغّل الدوال على EntityManager وهمي.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings({ "unchecked", "rawtypes" })
class GeneralLedgerServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 17);

    @Mock
    private JoudRepository db;

    @Mock
    private EntityManager em;

    @Mock
    private Event<JournalPostedEvent> postedEvent;

    @InjectMocks
    private GeneralLedgerService service;

    @BeforeEach
    void runRepositoryActionsAgainstMockEntityManager() {
        lenient().when(db.execute(any(Function.class))).thenAnswer(inv -> {
            Function<EntityManager, Object> action = inv.getArgument(0);
            return action.apply(em);
        });
        lenient().when(db.executeInTransaction(any(Function.class))).thenAnswer(inv -> {
            Function<EntityManager, Object> action = inv.getArgument(0);
            return action.apply(em);
        });
        lenient().doAnswer(inv -> {
            Consumer<EntityManager> action = inv.getArgument(0);
            action.accept(em);
            return null;
        }).when(db).executeInTransaction(any(Consumer.class));

        // عملة الفرع الأساسية (AGENT_MAST.CURRENCY_I_ID)
        Query baseCurrency = nativeQuery("SELECT CURRENCY_I_ID");
        lenient().when(baseCurrency.getResultList()).thenReturn((List) List.of(1L));
    }

    // ─── أدوات مساعدة ───────────────────────────────

    private <T> TypedQuery<T> namedQuery(String name, Class<T> type, List<T> rows) {
        TypedQuery<T> q = mock(TypedQuery.class);
        lenient().when(q.setParameter(anyString(), any())).thenReturn(q);
        lenient().when(q.setMaxResults(anyInt())).thenReturn(q);
        lenient().when(q.setLockMode(any())).thenReturn(q);
        lenient().when(q.getResultList()).thenReturn(rows);
        lenient().when(em.createNamedQuery(eq(name), eq(type))).thenReturn(q);
        return q;
    }

    private Query nativeQuery(String sqlPrefix) {
        Query q = mock(Query.class);
        lenient().when(q.setParameter(anyInt(), any())).thenReturn(q);
        lenient().when(q.setParameter(anyString(), any())).thenReturn(q);
        lenient().when(q.setMaxResults(anyInt())).thenReturn(q);
        lenient().when(q.executeUpdate()).thenReturn(1);
        lenient().when(em.createNativeQuery(startsWith(sqlPrefix))).thenReturn(q);
        return q;
    }

    /** استعلام تحديد الحساب (ACCOUNT_MAST). */
    private void givenAccountLookupReturns(List<?> ids) {
        Query q = nativeQuery("SELECT ACT_I_ID");
        lenient().when(q.getResultList()).thenReturn((List) ids);
    }

    /** محاكاة SequenceAllocator: لا صفوف في السجل والجدول فارغ. */
    private void givenEmptySequenceTables() {
        Query max = nativeQuery("SELECT COALESCE");
        lenient().when(max.getSingleResult()).thenReturn(0L);
        Query registry = nativeQuery("SELECT NEXT_VAL");
        lenient().when(registry.getResultList()).thenReturn(List.of());
        nativeQuery("INSERT INTO JOUD_SEQUENCE_REGISTRY");
        nativeQuery("UPDATE JOUD_SEQUENCE_REGISTRY");
    }

    private AccountingPeriod period(PeriodStatus status) {
        AccountingPeriod p = new AccountingPeriod();
        p.setId(3L);
        p.setAgentId(1L);
        p.setYear(DATE.getYear());
        p.setMonth(DATE.getMonthValue());
        p.setStatus(status);
        return p;
    }

    private DayLedger line(long id, String drCr, String amount, Long customerId) {
        DayLedger l = new DayLedger();
        l.setId(id);
        l.setAgentId(1L);
        l.setDate(DATE);
        l.setAccountType("CUST");
        l.setCustomerId(customerId);
        l.setCurrencyId(2L);
        l.setAmount(new BigDecimal(amount));
        l.setAmountBase(new BigDecimal(amount));
        l.setDrCr(drCr);
        return l;
    }

    private JournalEntry journal(JvStatus status, String debit, String credit) {
        JournalEntry jv = new JournalEntry();
        jv.setId(10L);
        jv.setReference("JV-10");
        jv.setAgentId(1L);
        jv.setCountryCode("EG");
        jv.setDate(DATE);
        jv.setCurrencyId(2L);
        jv.setStatus(status);
        jv.setTotalDebit(new BigDecimal(debit));
        jv.setTotalCredit(new BigDecimal(credit));
        return jv;
    }

    private JournalEntry balancedJournalWithTwoLines(JvStatus status) {
        JournalEntry jv = journal(status, "1000", "1000");
        jv.getLines().add(line(1L, "DR", "1000", 5L));
        jv.getLines().add(line(2L, "CR", "1000", 6L));
        return jv;
    }

    private void givenJournal(JournalEntry jv) {
        when(em.find(JournalEntry.class, jv.getId(), LockModeType.PESSIMISTIC_WRITE)).thenReturn(jv);
    }

    // ─── ترحيل القيود: حالات الرفض ───────────────────

    @Test
    @DisplayName("ترحيل قيد غير موجود يرفع AccountingException")
    void postMissingEntry() {
        when(em.find(JournalEntry.class, 999L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(null);

        AccountingException ex = assertThrows(AccountingException.class,
                () -> service.postJournalEntry(999L, 1L));
        assertTrue(ex.getMessage().contains("غير موجود"));
    }

    @Test
    @DisplayName("المسودة (غير المعلّقة) لا يمكن اعتمادها")
    void postDraftEntryRejected() {
        givenJournal(journal(JvStatus.O, "1000", "1000"));

        assertThrows(AccountingException.class, () -> service.postJournalEntry(10L, 1L));
        verify(postedEvent, never()).fireAsync(any(JournalPostedEvent.class));
    }

    @Test
    @DisplayName("قيد غير متوازن يُرفض")
    void postUnbalancedEntryRejected() {
        givenJournal(journal(JvStatus.P, "1000", "900"));

        AccountingException ex = assertThrows(AccountingException.class,
                () -> service.postJournalEntry(10L, 1L));
        assertTrue(ex.getMessage().contains("غير متوازن"));
    }

    @Test
    @DisplayName("قيد بلا أسطر يُرفض")
    void postEntryWithoutLinesRejected() {
        givenJournal(journal(JvStatus.P, "0", "0"));

        AccountingException ex = assertThrows(AccountingException.class,
                () -> service.postJournalEntry(10L, 1L));
        assertTrue(ex.getMessage().contains("بلا أسطر"));
    }

    @Test
    @DisplayName("فترة مغلقة تمنع الترحيل")
    void postIntoClosedPeriodRejected() {
        givenJournal(balancedJournalWithTwoLines(JvStatus.P));
        namedQuery("AccountingPeriod.findByYearMonth", AccountingPeriod.class, List.of(period(PeriodStatus.C)));

        AccountingException ex = assertThrows(AccountingException.class,
                () -> service.postJournalEntry(10L, 1L));
        assertTrue(ex.getMessage().contains("مغلقة"));
    }

    @Test
    @DisplayName("غياب الفترة المحاسبية يمنع الترحيل")
    void postWithoutPeriodRejected() {
        givenJournal(balancedJournalWithTwoLines(JvStatus.P));
        namedQuery("AccountingPeriod.findByYearMonth", AccountingPeriod.class, List.of());

        AccountingException ex = assertThrows(AccountingException.class,
                () -> service.postJournalEntry(10L, 1L));
        assertTrue(ex.getMessage().contains("لا توجد فترة"));
    }

    @Test
    @DisplayName("سطر بلا رقم عميل: لا يمكن تحديد الحساب")
    void postLineWithoutCustomerRejected() {
        JournalEntry jv = journal(JvStatus.P, "1000", "1000");
        jv.getLines().add(line(1L, "DR", "1000", null));
        jv.getLines().add(line(2L, "CR", "1000", 6L));
        givenJournal(jv);
        namedQuery("AccountingPeriod.findByYearMonth", AccountingPeriod.class, List.of(period(PeriodStatus.O)));
        namedQuery("ExchangeRate.findByDateAndCurrency", ExchangeRateHistory.class, List.of());

        assertThrows(AccountingException.class, () -> service.postJournalEntry(10L, 1L));
    }

    @Test
    @DisplayName("حساب غير موجود في ACCOUNT_MAST يُوقف الترحيل برسالة واضحة")
    void postAccountNotFoundRejected() {
        givenJournal(balancedJournalWithTwoLines(JvStatus.P));
        namedQuery("AccountingPeriod.findByYearMonth", AccountingPeriod.class, List.of(period(PeriodStatus.O)));
        namedQuery("ExchangeRate.findByDateAndCurrency", ExchangeRateHistory.class, List.of());
        givenAccountLookupReturns(List.of());

        AccountingException ex = assertThrows(AccountingException.class,
                () -> service.postJournalEntry(10L, 1L));
        assertTrue(ex.getMessage().contains("لا يوجد حساب"));
        verify(postedEvent, never()).fireAsync(any(JournalPostedEvent.class));
    }

    // ─── ترحيل القيود: النجاح ────────────────────────

    @Test
    @DisplayName("ترحيل ناجح: القيد والأسطر Approved، وإنشاء سجلات GL جديدة بالأرصدة الصحيحة")
    void postSuccessCreatesLedgerRecords() {
        JournalEntry jv = balancedJournalWithTwoLines(JvStatus.P);
        givenJournal(jv);
        namedQuery("AccountingPeriod.findByYearMonth", AccountingPeriod.class, List.of(period(PeriodStatus.O)));
        namedQuery("ExchangeRate.findByDateAndCurrency", ExchangeRateHistory.class, List.of());
        namedQuery("GenLdg.findByAccountPeriodAgent", GeneralLedgerMaster.class, List.of());
        namedQuery("GenLdg.lastClosingBalance", GeneralLedgerMaster.class, List.of());
        givenAccountLookupReturns(List.of(500L));
        givenEmptySequenceTables();

        service.postJournalEntry(10L, 99L);

        assertEquals(JvStatus.A, jv.getStatus());
        assertEquals(99L, jv.getApprovedBy());
        assertNotNull(jv.getApprovedAt());
        jv.getLines().forEach(l -> assertEquals(DayLedger.TrnStatus.A, l.getStatus()));

        ArgumentCaptor<GeneralLedgerMaster> saved = ArgumentCaptor.forClass(GeneralLedgerMaster.class);
        verify(em, times(2)).persist(saved.capture());
        GeneralLedgerMaster first = saved.getAllValues().get(0);
        GeneralLedgerMaster second = saved.getAllValues().get(1);
        assertEquals(500L, first.getAccountId());
        assertEquals(0, new BigDecimal("1000").compareTo(first.getTotalDebit()));
        assertEquals(0, new BigDecimal("1000").compareTo(second.getTotalCredit()));

        // الإشعار يُطلق بعد نجاح الحفظ، ويحمل لقطة من القيد المعتمد
        ArgumentCaptor<JournalPostedEvent> event = ArgumentCaptor.forClass(JournalPostedEvent.class);
        verify(postedEvent).fireAsync(event.capture());
        assertEquals(10L, event.getValue().journalEntryId());
        assertEquals("JV-10", event.getValue().reference());
        assertEquals(99L, event.getValue().approvedBy());
        assertEquals(0, new BigDecimal("1000").compareTo(event.getValue().totalDebit()));
    }

    @Test
    @DisplayName("فشل إطلاق الإشعار لا يُفشل الترحيل بعد حفظه")
    void eventFailureDoesNotBreakPosting() {
        JournalEntry jv = balancedJournalWithTwoLines(JvStatus.P);
        givenJournal(jv);
        namedQuery("AccountingPeriod.findByYearMonth", AccountingPeriod.class, List.of(period(PeriodStatus.O)));
        namedQuery("ExchangeRate.findByDateAndCurrency", ExchangeRateHistory.class, List.of());
        namedQuery("GenLdg.findByAccountPeriodAgent", GeneralLedgerMaster.class,
                List.of(new GeneralLedgerMaster()));
        givenAccountLookupReturns(List.of(500L));
        when(postedEvent.fireAsync(any(JournalPostedEvent.class))).thenThrow(new IllegalStateException("cdi down"));

        service.postJournalEntry(10L, 99L);

        assertEquals(JvStatus.A, jv.getStatus());
    }

    @Test
    @DisplayName("سجل GL موجود يُحدَّث ولا يُنشأ سجل جديد")
    void postUpdatesExistingLedgerRecord() {
        JournalEntry jv = balancedJournalWithTwoLines(JvStatus.P);
        givenJournal(jv);
        GeneralLedgerMaster existing = new GeneralLedgerMaster();
        existing.setId(7L);
        namedQuery("AccountingPeriod.findByYearMonth", AccountingPeriod.class, List.of(period(PeriodStatus.O)));
        namedQuery("ExchangeRate.findByDateAndCurrency", ExchangeRateHistory.class, List.of());
        namedQuery("GenLdg.findByAccountPeriodAgent", GeneralLedgerMaster.class, List.of(existing));
        givenAccountLookupReturns(List.of(500L));

        service.postJournalEntry(10L, 99L);

        verify(em, never()).persist(any(GeneralLedgerMaster.class));
        assertEquals(0, new BigDecimal("1000").compareTo(existing.getTotalDebit()));
        assertEquals(0, new BigDecimal("1000").compareTo(existing.getTotalCredit()));
        assertEquals(2, existing.getTransactionCount());
    }

    @Test
    @DisplayName("سعر الصرف من التاريخ يُطبَّق على القيد وأسطره")
    void postAppliesExchangeRateFromHistory() {
        JournalEntry jv = balancedJournalWithTwoLines(JvStatus.P);
        givenJournal(jv);
        ExchangeRateHistory rate = new ExchangeRateHistory();
        rate.setRate(new BigDecimal("3.5"));
        namedQuery("AccountingPeriod.findByYearMonth", AccountingPeriod.class, List.of(period(PeriodStatus.O)));
        namedQuery("ExchangeRate.findByDateAndCurrency", ExchangeRateHistory.class, List.of(rate));
        namedQuery("GenLdg.findByAccountPeriodAgent", GeneralLedgerMaster.class,
                List.of(new GeneralLedgerMaster()));
        givenAccountLookupReturns(List.of(500L));

        service.postJournalEntry(10L, 99L);

        assertEquals(0, new BigDecimal("3.5").compareTo(jv.getExchangeRate()));
        jv.getLines().forEach(l -> {
            assertEquals(0, new BigDecimal("3.5").compareTo(l.getExchangeRate()));
            assertEquals(0, l.getAmount().multiply(new BigDecimal("3.5")).compareTo(l.getAmountBase()));
        });
    }

    // ─── إلغاء القيد ────────────────────────────────

    @Test
    @DisplayName("لا يُلغى إلا القيد المعتمد")
    void voidRequiresApprovedEntry() {
        givenJournal(journal(JvStatus.P, "1000", "1000"));

        assertThrows(AccountingException.class, () -> service.voidJournalEntry(10L, 1L, "خطأ"));
    }

    @Test
    @DisplayName("إلغاء ناجح: عكس الرصيد، حالة الأسطر والقيد V، والسبب في البيان")
    void voidReversesLedgerAndMarksVoid() {
        JournalEntry jv = journal(JvStatus.A, "1000", "1000");
        jv.setNarration("دفعة");
        jv.getLines().add(line(1L, "DR", "1000", 5L));
        givenJournal(jv);
        GeneralLedgerMaster gen = new GeneralLedgerMaster();
        gen.postTransaction(new BigDecimal("1000"), new BigDecimal("1000"), DrCr.D);
        namedQuery("AccountingPeriod.findByYearMonth", AccountingPeriod.class, List.of(period(PeriodStatus.O)));
        namedQuery("GenLdg.findByAccountPeriodAgent", GeneralLedgerMaster.class, List.of(gen));
        givenAccountLookupReturns(List.of(500L));

        service.voidJournalEntry(10L, 1L, "إدخال خاطئ");

        assertEquals(JvStatus.V, jv.getStatus());
        assertEquals(DayLedger.TrnStatus.V, jv.getLines().get(0).getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(gen.getTotalDebit()));
        assertEquals(0, gen.getTransactionCount());
        assertTrue(jv.getNarration().contains("دفعة"));
        assertTrue(jv.getNarration().contains("إدخال خاطئ"));
    }

    @Test
    @DisplayName("إلغاء قيد لا يوجد له سجل GL لا يفشل")
    void voidWithoutLedgerRecordStillVoids() {
        JournalEntry jv = journal(JvStatus.A, "1000", "1000");
        jv.getLines().add(line(1L, "DR", "1000", 5L));
        givenJournal(jv);
        namedQuery("AccountingPeriod.findByYearMonth", AccountingPeriod.class, List.of(period(PeriodStatus.O)));
        namedQuery("GenLdg.findByAccountPeriodAgent", GeneralLedgerMaster.class, List.of());
        givenAccountLookupReturns(List.of(500L));

        service.voidJournalEntry(10L, 1L, "سبب");

        assertEquals(JvStatus.V, jv.getStatus());
    }

    // ─── الفترات ────────────────────────────────────

    @Test
    @DisplayName("إغلاق فترة غير موجودة يفشل")
    void closeMissingPeriod() {
        namedQuery("AccountingPeriod.findByYearMonth", AccountingPeriod.class, List.of());

        assertThrows(AccountingException.class, () -> service.closePeriod(1L, 2026, 11, 9L));
    }

    @Test
    @DisplayName("إغلاق فترة مغلقة أصلاً يفشل")
    void closeAlreadyClosedPeriod() {
        namedQuery("AccountingPeriod.findByYearMonth", AccountingPeriod.class, List.of(period(PeriodStatus.C)));

        assertThrows(AccountingException.class, () -> service.closePeriod(1L, 2026, 11, 9L));
    }

    @Test
    @DisplayName("إغلاق ديسمبر يغلق الفترة ويفتح يناير من السنة التالية")
    void closeDecemberOpensNextYear() {
        AccountingPeriod december = period(PeriodStatus.O);
        december.setMonth(12);
        TypedQuery<AccountingPeriod> q = namedQuery("AccountingPeriod.findByYearMonth",
                AccountingPeriod.class, List.of());
        // الاستدعاء الأول يجد الفترة الحالية، والثاني لا يجد الفترة التالية
        lenient().when(q.getResultList()).thenReturn(List.of(december), List.of());
        givenEmptySequenceTables();

        service.closePeriod(1L, 2026, 12, 9L);

        assertEquals(PeriodStatus.C, december.getStatus());
        assertEquals(9L, december.getClosedBy());
        assertNotNull(december.getDateClosed());

        ArgumentCaptor<AccountingPeriod> next = ArgumentCaptor.forClass(AccountingPeriod.class);
        verify(em).persist(next.capture());
        assertEquals(2027, next.getValue().getYear());
        assertEquals(1, next.getValue().getMonth());
        assertEquals(LocalDate.of(2027, 1, 31), next.getValue().getDateTo());
        assertEquals(PeriodStatus.O, next.getValue().getStatus());
    }

    @Test
    @DisplayName("لا تُكرَّر الفترة التالية إن كانت موجودة")
    void closeDoesNotDuplicateNextPeriod() {
        AccountingPeriod november = period(PeriodStatus.O);
        TypedQuery<AccountingPeriod> q = namedQuery("AccountingPeriod.findByYearMonth",
                AccountingPeriod.class, List.of());
        lenient().when(q.getResultList()).thenReturn(List.of(november), List.of(period(PeriodStatus.O)));

        service.closePeriod(1L, 2026, 11, 9L);

        assertEquals(PeriodStatus.C, november.getStatus());
        verify(em, never()).persist(any(AccountingPeriod.class));
    }

    @Test
    @DisplayName("findPeriod تُرجع الفترة أو empty")
    void findPeriodReturnsOptional() {
        namedQuery("AccountingPeriod.findByYearMonth", AccountingPeriod.class, List.of(period(PeriodStatus.O)));
        assertTrue(service.findPeriod(1L, 2026, 11).isPresent());

        namedQuery("AccountingPeriod.findByYearMonth", AccountingPeriod.class, List.of());
        Optional<AccountingPeriod> none = service.findPeriod(1L, 2026, 11);
        assertTrue(none.isEmpty());
    }

    // ─── سعر الصرف ──────────────────────────────────

    @Test
    @DisplayName("لا سعر صرف مسجّل: القيمة 1")
    void exchangeRateDefaultsToOne() {
        namedQuery("ExchangeRate.findByDateAndCurrency", ExchangeRateHistory.class, List.of());

        assertEquals(0, BigDecimal.ONE.compareTo(service.resolveExchangeRate(2L, DATE, 1L)));
    }

    @Test
    @DisplayName("سعر الصرف المسجّل يُستخدم")
    void exchangeRateFromHistory() {
        ExchangeRateHistory h = new ExchangeRateHistory();
        h.setRate(new BigDecimal("48.25"));
        namedQuery("ExchangeRate.findByDateAndCurrency", ExchangeRateHistory.class, List.of(h));

        assertEquals(0, new BigDecimal("48.25").compareTo(service.resolveExchangeRate(2L, DATE, 1L)));
    }
}
