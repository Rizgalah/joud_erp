package com.joud.core.security.service;

import com.joud.core.db.JoudRepository;
import com.joud.core.security.model.User;
import com.joud.core.security.service.AuthService.LoginOutcome;
import com.joud.core.security.service.AuthService.LoginResult;
import com.joud.core.security.session.SessionManager;
import com.joud.core.security.session.UserSession;
import com.joud.core.security.util.PasswordUtil;
import com.joud.core.security.util.RateLimiter;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({ "unchecked", "rawtypes" })
class AuthServiceTest {

    private static final String IP = "10.0.0.5";
    private static final String PASSWORD = "admin123";
    private static String passwordHash;

    @Mock
    private JoudRepository db;

    @Mock
    private SessionManager sessionManager;

    @Mock
    private RateLimiter rateLimiter;

    @InjectMocks
    private AuthService authService;

    @BeforeAll
    static void hashOnce() {
        // BCrypt بطيء، فنُجريه مرة واحدة لكل الاختبارات
        passwordHash = PasswordUtil.hash(PASSWORD);
    }

    private User user(String status) {
        User u = new User();
        u.setId(1L);
        u.setUsername("sa");
        u.setEmail("sa@example.com");
        u.setPasswordHash(passwordHash);
        u.setStatus(status);
        return u;
    }

    private void givenLookupReturns(Optional<User> result) {
        doReturn(result).when(db).execute(any(Function.class));
    }

    @Test
    @DisplayName("دخول ناجح: جلسة جديدة + تحديث آخر دخول + تصفير العدّاد")
    void loginSuccess() {
        User u = user("A");
        u.setFailedAttempts(3);
        givenLookupReturns(Optional.of(u));

        LoginOutcome outcome = authService.login("sa", PASSWORD, IP);

        assertEquals(LoginResult.SUCCESS, outcome.result());
        assertNotNull(outcome.session());
        assertEquals(0, u.getFailedAttempts());
        assertNotNull(u.getLastLogin());
        verify(rateLimiter).reset(IP);
        verify(sessionManager).register(any(UserSession.class));
        verify(db).executeInTransaction(any(Consumer.class));
    }

    @Test
    @DisplayName("كلمة مرور خاطئة: INVALID_CREDENTIALS وتسجيل المحاولة دون جلسة")
    void wrongPassword() {
        givenLookupReturns(Optional.of(user("A")));
        doReturn(1).when(db).executeInTransaction(any(Function.class));

        LoginOutcome outcome = authService.login("sa", "wrong_password", IP);

        assertEquals(LoginResult.INVALID_CREDENTIALS, outcome.result());
        assertNull(outcome.session());
        verify(rateLimiter).recordFailure(IP);
        verify(rateLimiter, never()).reset(anyString());
        verify(sessionManager, never()).register(any(UserSession.class));
    }

    @Test
    @DisplayName("مستخدم غير موجود: INVALID_CREDENTIALS وتسجيل المحاولة على الـ IP")
    void unknownUser() {
        givenLookupReturns(Optional.empty());

        LoginOutcome outcome = authService.login("ghost", PASSWORD, IP);

        assertEquals(LoginResult.INVALID_CREDENTIALS, outcome.result());
        verify(rateLimiter).recordFailure(IP);
    }

    @Test
    @DisplayName("اسم مستخدم فارغ لا يلمس قاعدة البيانات")
    void blankUsernameSkipsDatabase() {
        LoginOutcome outcome = authService.login(null, PASSWORD, IP);

        assertEquals(LoginResult.INVALID_CREDENTIALS, outcome.result());
        verifyNoInteractions(db);
        verify(rateLimiter).recordFailure(IP);
    }

    @Test
    @DisplayName("IP محظور: IP_BLOCKED دون أي وصول لقاعدة البيانات")
    void blockedIp() {
        when(rateLimiter.isBlocked(IP)).thenReturn(true);
        when(rateLimiter.getRemainingBlockMinutes(IP)).thenReturn(5L);

        LoginOutcome outcome = authService.login("sa", PASSWORD, IP);

        assertEquals(LoginResult.IP_BLOCKED, outcome.result());
        verifyNoInteractions(db);
    }

    @Test
    @DisplayName("حساب بحالة L يُعامَل كمقفل")
    void lockedStatus() {
        givenLookupReturns(Optional.of(user("L")));

        assertEquals(LoginResult.ACCOUNT_LOCKED, authService.login("sa", PASSWORD, IP).result());
    }

    @Test
    @DisplayName("حساب مقفل مؤقتاً (lockedUntil في المستقبل) يُعامَل كمقفل")
    void lockedUntilInFuture() {
        User u = user("A");
        u.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        givenLookupReturns(Optional.of(u));

        assertEquals(LoginResult.ACCOUNT_LOCKED, authService.login("sa", PASSWORD, IP).result());
    }

    @Test
    @DisplayName("حساب بحالة I غير نشط")
    void inactiveAccount() {
        givenLookupReturns(Optional.of(user("I")));

        assertEquals(LoginResult.ACCOUNT_INACTIVE, authService.login("sa", PASSWORD, IP).result());
    }

    @Test
    @DisplayName("عطل في قاعدة البيانات: رسالة عامة دون جلسة")
    void databaseFailure() {
        doThrow(new RuntimeException("db down")).when(db).execute(any(Function.class));

        LoginOutcome outcome = authService.login("sa", PASSWORD, IP);

        assertEquals(LoginResult.INVALID_CREDENTIALS, outcome.result());
        assertNull(outcome.session());
        verify(sessionManager, never()).register(any(UserSession.class));
    }
}
