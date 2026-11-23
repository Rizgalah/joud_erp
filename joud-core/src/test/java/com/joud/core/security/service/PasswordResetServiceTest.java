package com.joud.core.security.service;

import com.joud.core.db.JoudRepository;
import com.joud.core.security.model.PasswordResetToken;
import com.joud.core.security.model.User;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * يختبر منطق الخدمة دون قاعدة بيانات: الـ JoudRepository يُشغّل الدوال
 * على EntityManager وهمي.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings({ "unchecked", "rawtypes" })
class PasswordResetServiceTest {

    @Mock
    private JoudRepository db;

    @Mock
    private EntityManager em;

    @InjectMocks
    private PasswordResetService service;

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
    }

    // ─── أدوات مساعدة ───────────────────────────────

    private <T> TypedQuery<T> typedQueryReturning(Class<T> type, List<T> rows) {
        TypedQuery<T> q = mock(TypedQuery.class);
        lenient().when(q.setParameter(anyString(), any())).thenReturn(q);
        lenient().when(q.setMaxResults(anyInt())).thenReturn(q);
        lenient().when(q.setLockMode(any())).thenReturn(q);
        lenient().when(q.getResultList()).thenReturn(rows);
        lenient().when(em.createQuery(anyString(), eq(type))).thenReturn(q);
        return q;
    }

    private Query updateQuery() {
        Query q = mock(Query.class);
        lenient().when(q.setParameter(anyString(), any())).thenReturn(q);
        lenient().when(q.executeUpdate()).thenReturn(1);
        lenient().when(em.createQuery(anyString())).thenReturn(q);
        return q;
    }

    private PasswordResetToken token(String value, boolean used, LocalDateTime expiresAt) {
        PasswordResetToken t = new PasswordResetToken();
        t.setUserId(7L);
        t.setToken(value);
        t.setUsed(used);
        t.setExpiresAt(expiresAt);
        return t;
    }

    private User user(String status) {
        User u = new User();
        u.setId(7L);
        u.setUsername("sa");
        u.setEmail("sa@example.com");
        u.setPasswordHash("old-hash");
        u.setStatus(status);
        u.setFailedAttempts(4);
        return u;
    }

    // ─── generateResetToken ─────────────────────────

    @Test
    @DisplayName("بريد غير موجود: لا توكن ولا حفظ")
    void unknownEmailReturnsEmpty() {
        typedQueryReturning(Long.class, List.of());

        assertTrue(service.generateResetToken("nobody@example.com").isEmpty());
        verify(em, never()).persist(any());
    }

    @Test
    @DisplayName("بريد فارغ: لا يلمس قاعدة البيانات")
    void blankEmailReturnsEmpty() {
        assertTrue(service.generateResetToken("  ").isEmpty());
        verify(db, never()).executeInTransaction(any(Function.class));
    }

    @Test
    @DisplayName("بريد معروف: يُحفظ التوكن ويُعاد نفس القيمة (64 حرفاً)")
    void knownEmailPersistsAndReturnsToken() {
        typedQueryReturning(Long.class, List.of(7L));
        updateQuery();

        Optional<String> result = service.generateResetToken("SA@Example.com ");

        assertTrue(result.isPresent());
        assertEquals(64, result.get().length());
        verify(em).persist(any(PasswordResetToken.class));
    }

    // ─── validateToken ──────────────────────────────

    @Test
    @DisplayName("توكن غير موجود: empty")
    void validateUnknownToken() {
        typedQueryReturning(PasswordResetToken.class, List.of());

        assertTrue(service.validateToken("nope").isEmpty());
    }

    @Test
    @DisplayName("توكن منتهي أو مستخدم: empty")
    void validateExpiredOrUsedToken() {
        typedQueryReturning(PasswordResetToken.class,
                List.of(token("expired", false, LocalDateTime.now().minusMinutes(1))));
        assertTrue(service.validateToken("expired").isEmpty());

        typedQueryReturning(PasswordResetToken.class,
                List.of(token("used", true, LocalDateTime.now().plusMinutes(10))));
        assertTrue(service.validateToken("used").isEmpty());
    }

    @Test
    @DisplayName("توكن صالح: يُعاد المستخدم")
    void validateGoodToken() {
        typedQueryReturning(PasswordResetToken.class,
                List.of(token("good", false, LocalDateTime.now().plusMinutes(10))));
        User u = user("A");
        org.mockito.Mockito.when(em.find(User.class, 7L)).thenReturn(u);

        assertEquals(Optional.of(u), service.validateToken(" good "));
    }

    // ─── resetPassword ──────────────────────────────

    @Test
    @DisplayName("توكن غير صالح: لا كلمة مرور جديدة")
    void resetWithBadTokenReturnsEmpty() {
        typedQueryReturning(PasswordResetToken.class, List.of());

        assertTrue(service.resetPassword("bad").isEmpty());
    }

    @Test
    @DisplayName("توكن صالح: كلمة مرور جديدة بـ 14 حرفاً، فك القفل، وإبطال التوكن")
    void resetWithGoodTokenUpdatesUserAndToken() {
        PasswordResetToken t = token("good", false, LocalDateTime.now().plusMinutes(10));
        typedQueryReturning(PasswordResetToken.class, List.of(t));
        User u = user("L");
        org.mockito.Mockito.when(em.find(User.class, 7L)).thenReturn(u);

        Optional<String> newPassword = service.resetPassword("good");

        assertTrue(newPassword.isPresent());
        assertEquals(14, newPassword.get().length());
        assertEquals("A", u.getStatus());
        assertEquals(0, u.getFailedAttempts());
        assertNotEquals("old-hash", u.getPasswordHash());
        assertTrue(t.isUsed());
    }

    @Test
    @DisplayName("حساب مُعطَّل (I) لا يُعاد تفعيله بالتوكن، ولا يُستهلك التوكن")
    void resetDoesNotReactivateInactiveAccount() {
        PasswordResetToken t = token("good", false, LocalDateTime.now().plusMinutes(10));
        typedQueryReturning(PasswordResetToken.class, List.of(t));
        User u = user("I");
        org.mockito.Mockito.when(em.find(User.class, 7L)).thenReturn(u);

        assertTrue(service.resetPassword("good").isEmpty());
        assertEquals("I", u.getStatus());
        assertEquals("old-hash", u.getPasswordHash());
        assertFalse(t.isUsed());
    }
}
