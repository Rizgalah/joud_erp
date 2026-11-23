package com.joud.accounting.bean;

import com.joud.accounting.bean.JournalEntryBean.AccountOption;
import com.joud.accounting.entity.DayLedger;
import com.joud.accounting.entity.JournalEntry;
import com.joud.accounting.service.GeneralLedgerService;
import com.joud.accounting.service.GeneralLedgerService.AccountingException;
import com.joud.core.db.JoudRepository;
import com.joud.core.security.session.UserSession;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({ "unchecked", "rawtypes" })
class JournalEntryBeanTest {

    private static final Long AGENT = 1L;
    private static final Long CURRENCY = 2L;

    /** يلتقط الرسائل بدل FacesContext. */
    static class TestBean extends JournalEntryBean {
        final List<String> errors = new ArrayList<>();
        final List<String> infos = new ArrayList<>();

        @Override
        UserSession getCurrentUser() {
            return null;
        }

        @Override
        void addError(String msg) {
            errors.add(msg);
        }

        @Override
        void addInfo(String msg) {
            infos.add(msg);
        }

        boolean hasError(String fragment) {
            return errors.stream().anyMatch(e -> e.contains(fragment));
        }
    }

    @Mock
    private JoudRepository db;

    @Mock
    private GeneralLedgerService glService;

    @Mock
    private EntityManager em;

    @InjectMocks
    private TestBean bean;

    // ─── أدوات مساعدة ───────────────────────────────

    private AccountOption account(long id, long customer, Long currency, Long agent) {
        return new AccountOption(id, "CUST", customer, currency, agent, "حساب " + id);
    }

    /** يحمّل الشاشة بحسابات معيّنة ثم يضبط الفرع والعملة والتاريخ. */
    private void openScreenWith(AccountOption... accounts) {
        JournalEntryBean.Lookups lookups = new JournalEntryBean.Lookups(List.of(accounts), List.of(), List.of());
        // الاستدعاء الأول: القوائم، وما بعده: القيود الأخيرة
        doReturn(lookups, List.of()).when(db).execute(any(Function.class));
        bean.init();
        bean.setAgentId(AGENT);
        bean.setCurrencyId(CURRENCY);
        bean.setDate(LocalDate.of(2026, 11, 17));
    }

    private void addLineOf(long accountId, String drCr, String amount) {
        bean.setLineAccountId(accountId);
        bean.setLineDrCr(drCr);
        bean.setLineAmount(new BigDecimal(amount));
        bean.addLine();
    }

    private void addBalancedLines() {
        addLineOf(10L, "DR", "1000");
        addLineOf(11L, "CR", "1000");
    }

    private Query nativeQuery(String sqlPrefix) {
        Query q = mock(Query.class);
        lenient().when(q.setParameter(anyInt(), any())).thenReturn(q);
        lenient().when(q.setMaxResults(anyInt())).thenReturn(q);
        lenient().when(q.executeUpdate()).thenReturn(1);
        lenient().when(em.createNativeQuery(startsWith(sqlPrefix))).thenReturn(q);
        return q;
    }

    // ─── إضافة الأسطر ───────────────────────────────

    @Test
    @DisplayName("لا يُضاف سطر بلا حساب")
    void addLineRequiresAccount() {
        openScreenWith(account(10L, 77L, CURRENCY, AGENT));
        bean.setLineAmount(new BigDecimal("100"));

        bean.addLine();

        assertTrue(bean.hasError("اختيار الحساب"));
        assertTrue(bean.getLines().isEmpty());
    }

    @Test
    @DisplayName("لا يُضاف سطر بمبلغ صفر")
    void addLineRequiresPositiveAmount() {
        openScreenWith(account(10L, 77L, CURRENCY, AGENT));
        bean.setLineAccountId(10L);
        bean.setLineAmount(BigDecimal.ZERO);

        bean.addLine();

        assertTrue(bean.hasError("مبلغ"));
        assertTrue(bean.getLines().isEmpty());
    }

    @Test
    @DisplayName("لا يُضاف سطر قبل اختيار عملة القيد")
    void addLineRequiresCurrency() {
        openScreenWith(account(10L, 77L, CURRENCY, AGENT));
        bean.setCurrencyId(null);

        addLineOf(10L, "DR", "100");

        assertTrue(bean.hasError("عملة القيد"));
        assertTrue(bean.getLines().isEmpty());
    }

    @Test
    @DisplayName("حساب يتبع فرعاً آخر يُرفض")
    void addLineRejectsAccountOfAnotherBranch() {
        openScreenWith(account(10L, 77L, CURRENCY, 9L));

        addLineOf(10L, "DR", "100");

        assertTrue(bean.hasError("فرعاً آخر"));
        assertTrue(bean.getLines().isEmpty());
    }

    @Test
    @DisplayName("حساب بعملة مختلفة عن عملة القيد يُرفض")
    void addLineRejectsAccountInAnotherCurrency() {
        openScreenWith(account(10L, 77L, 3L, AGENT));

        addLineOf(10L, "DR", "100");

        assertTrue(bean.hasError("عملة الحساب"));
        assertTrue(bean.getLines().isEmpty());
    }

    @Test
    @DisplayName("حساب غير موجود في القائمة يُرفض")
    void addLineRejectsUnknownAccount() {
        openScreenWith(account(10L, 77L, CURRENCY, AGENT));

        addLineOf(999L, "DR", "100");

        assertTrue(bean.hasError("غير موجود"));
        assertTrue(bean.getLines().isEmpty());
    }

    @Test
    @DisplayName("سطر صحيح: يحمل نوع الحساب ورقم العميل، وتتحدّث الإجماليات وتُفرَّغ المدخلات")
    void addValidLine() {
        openScreenWith(account(10L, 77L, CURRENCY, AGENT));

        addLineOf(10L, "DR", "1000");

        assertEquals(1, bean.getLines().size());
        DayLedger line = bean.getLines().get(0);
        assertEquals("CUST", line.getAccountType());
        assertEquals(77L, line.getCustomerId());
        assertEquals(0, new BigDecimal("1000").compareTo(bean.getTotalDebit()));
        assertNull(bean.getLineAccountId());
        assertTrue(bean.errors.isEmpty());
    }

    @Test
    @DisplayName("حذف سطر يُعيد حساب الإجماليات")
    void removeLineRecalculates() {
        openScreenWith(account(10L, 77L, CURRENCY, AGENT), account(11L, 78L, CURRENCY, AGENT));
        addBalancedLines();
        assertTrue(bean.isBalanced());

        bean.removeLine(1);

        assertEquals(1, bean.getLines().size());
        assertEquals(0, BigDecimal.ZERO.compareTo(bean.getTotalCredit()));
    }

    // ─── الحفظ ──────────────────────────────────────

    @Test
    @DisplayName("حفظ مسودة بلا أسطر لا يلمس قاعدة البيانات")
    void saveDraftWithoutLines() {
        openScreenWith(account(10L, 77L, CURRENCY, AGENT));

        bean.saveDraft();

        assertTrue(bean.hasError("لا توجد أسطر"));
        verify(db, never()).executeInTransaction(any(Function.class));
    }

    @Test
    @DisplayName("قيد غير متوازن لا يُرسل للاعتماد")
    void submitUnbalancedIsRejected() {
        openScreenWith(account(10L, 77L, CURRENCY, AGENT), account(11L, 78L, CURRENCY, AGENT));
        addLineOf(10L, "DR", "1000");
        addLineOf(11L, "CR", "900");

        bean.submitForApproval();

        assertTrue(bean.hasError("غير متوازن"));
        verify(db, never()).executeInTransaction(any(Function.class));
    }

    @Test
    @DisplayName("الإرسال للاعتماد يحتاج سطرين على الأقل")
    void submitNeedsTwoLines() {
        openScreenWith(account(10L, 77L, CURRENCY, AGENT));

        bean.submitForApproval();

        assertTrue(bean.hasError("سطرين"));
        verify(db, never()).executeInTransaction(any(Function.class));
    }

    @Test
    @DisplayName("إرسال ناجح: رقم مرجعي JV-2026-00001، معرّفات، رمز بلد EG، وأسطر مربوطة بالقيد")
    void submitPersistsJournalWithReferenceIdsAndCountry() {
        openScreenWith(account(10L, 77L, CURRENCY, AGENT), account(11L, 78L, CURRENCY, AGENT));
        addBalancedLines();

        // تشغيل دالة المعاملة على EntityManager وهمي
        when(db.executeInTransaction(any(Function.class))).thenAnswer(inv -> {
            Function<EntityManager, Object> action = inv.getArgument(0);
            return action.apply(em);
        });
        Query maxId = nativeQuery("SELECT COALESCE");
        lenient().when(maxId.getSingleResult()).thenReturn(0L);
        Query registry = nativeQuery("SELECT NEXT_VAL");
        lenient().when(registry.getResultList()).thenReturn(List.of());
        nativeQuery("INSERT INTO JOUD_SEQUENCE_REGISTRY");
        nativeQuery("UPDATE JOUD_SEQUENCE_REGISTRY");
        Query maxRef = nativeQuery("SELECT MAX(CAST");
        lenient().when(maxRef.getSingleResult()).thenReturn(null);
        Query country = nativeQuery("SELECT COUNTRY");
        lenient().when(country.getResultList()).thenReturn(List.of("Egypt"));

        bean.submitForApproval();

        ArgumentCaptor<JournalEntry> saved = ArgumentCaptor.forClass(JournalEntry.class);
        verify(em).persist(saved.capture());
        JournalEntry jv = saved.getValue();
        assertEquals("JV-2026-00001", jv.getReference());
        assertEquals("EG", jv.getCountryCode());
        assertEquals(JournalEntry.JvStatus.P, jv.getStatus());
        assertEquals(0, new BigDecimal("1000").compareTo(jv.getTotalDebit()));
        assertEquals(0, new BigDecimal("1000").compareTo(jv.getTotalCredit()));
        assertEquals(2, jv.getLines().size());
        jv.getLines().forEach(l -> {
            assertEquals(jv.getId(), l.getJournalEntryId());
            assertEquals("EG", l.getCountryCode());
        });

        // بعد النجاح: رسالة بالرقم الفعلي وتفريغ الشاشة
        assertTrue(bean.infos.stream().anyMatch(m -> m.contains("JV-2026-00001")));
        assertTrue(bean.getLines().isEmpty());
        assertEquals(0, BigDecimal.ZERO.compareTo(bean.getTotalDebit()));
    }

    @Test
    @DisplayName("فشل الحفظ: رسالة خطأ، والأسطر تبقى كي لا يضيع إدخال المستخدم")
    void saveFailureKeepsLines() {
        openScreenWith(account(10L, 77L, CURRENCY, AGENT), account(11L, 78L, CURRENCY, AGENT));
        addBalancedLines();
        when(db.executeInTransaction(any(Function.class))).thenThrow(new RuntimeException("db down"));

        bean.submitForApproval();

        assertTrue(bean.hasError("تعذّر حفظ القيد"));
        assertEquals(2, bean.getLines().size());
        assertTrue(bean.infos.isEmpty());
    }

    // ─── الاعتماد وتحميل القوائم ────────────────────

    @Test
    @DisplayName("اعتماد ناجح يستدعي خدمة الترحيل ويعرض رسالة")
    void approveAndPostSuccess() {
        openScreenWith(account(10L, 77L, CURRENCY, AGENT));

        bean.approveAndPost(5L);

        verify(glService).postJournalEntry(5L, 0L);
        assertTrue(bean.infos.stream().anyMatch(m -> m.contains("تم ترحيل")));
    }

    @Test
    @DisplayName("خطأ محاسبي أثناء الاعتماد يظهر نصه للمستخدم")
    void approveAndPostShowsAccountingError() {
        openScreenWith(account(10L, 77L, CURRENCY, AGENT));
        doThrow(new AccountingException("الفترة المحاسبية مغلقة")).when(glService).postJournalEntry(5L, 0L);

        bean.approveAndPost(5L);

        assertTrue(bean.hasError("الفترة المحاسبية مغلقة"));
    }

    @Test
    @DisplayName("فشل تحميل القوائم يظهر للمستخدم بدل أن تبقى القوائم فارغة بصمت")
    void lookupFailureIsReported() {
        doThrow(new RuntimeException("db down")).when(db).execute(any(Function.class));

        bean.init();

        assertTrue(bean.hasError("تعذّر تحميل قوائم"));
        assertTrue(bean.getAccounts().isEmpty());
    }
}
