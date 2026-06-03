package com.joud.core.security.service;

import com.joud.core.security.model.User;
import com.joud.core.security.session.SessionManager;
import com.joud.core.security.session.UserSession;
import com.joud.core.security.util.PasswordUtil;
import com.joud.core.security.util.RateLimiter;
import com.joud.core.security.util.TokenUtil;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * AuthService — خدمة المصادقة الرئيسية
 *
 * تتولى:
 *   - التحقق من بيانات الدخول
 *   - إدارة محاولات الدخول الفاشلة
 *   - إنشاء الجلسة بعد النجاح
 *   - تسجيل الخروج
 */
@ApplicationScoped
public class AuthService {

    private static final Logger LOG = Logger.getLogger(AuthService.class.getName());
    private static final int MAX_FAILED = 5;

    @PersistenceContext
    private EntityManager em;

    @Inject private SessionManager sessionManager;
    @Inject private RateLimiter    rateLimiter;

    // ─── نتيجة محاولة الدخول ───────────────────────

    public enum LoginResult {
        SUCCESS,
        INVALID_CREDENTIALS,   // بيانات خاطئة
        ACCOUNT_LOCKED,        // الحساب مقفل
        IP_BLOCKED,            // IP محظور بسبب Brute Force
        ACCOUNT_INACTIVE       // الحساب غير نشط
    }

    public record LoginOutcome(LoginResult result, UserSession session, String message) {}

    // ─── تسجيل الدخول ──────────────────────────────

    @Transactional
    public LoginOutcome login(String username, String password, String clientIp) {

        // 1. هل الـ IP محظور؟
        if (rateLimiter.isBlocked(clientIp)) {
            long mins = rateLimiter.getRemainingBlockMinutes(clientIp);
            return new LoginOutcome(LoginResult.IP_BLOCKED, null,
                "تم تجاوز عدد المحاولات المسموح بها. حاول بعد " + mins + " دقيقة.");
        }

        // 2. ابحث عن المستخدم
        Optional<User> userOpt = findByUsername(username);
        if (userOpt.isEmpty()) {
            rateLimiter.recordFailure(clientIp);
            return new LoginOutcome(LoginResult.INVALID_CREDENTIALS, null, "اسم المستخدم أو كلمة المرور غير صحيحة.");
        }

        User user = userOpt.get();

        // 3. هل الحساب نشط؟
        if (!user.isActive()) {
            return new LoginOutcome(LoginResult.ACCOUNT_INACTIVE, null, "الحساب غير نشط. تواصل مع المسؤول.");
        }

        // 4. هل الحساب مقفل؟
        if (user.isLocked()) {
            return new LoginOutcome(LoginResult.ACCOUNT_LOCKED, null, "الحساب مقفل مؤقتاً. تواصل مع المسؤول.");
        }

        // 5. تحقق من كلمة المرور
        if (!PasswordUtil.verify(password, user.getPasswordHash())) {
            handleFailedAttempt(user, clientIp);
            return new LoginOutcome(LoginResult.INVALID_CREDENTIALS, null, "اسم المستخدم أو كلمة المرور غير صحيحة.");
        }

        // 6. نجح — أعد التهيئة وأنشئ الجلسة
        rateLimiter.reset(clientIp);
        user.setFailedAttempts(0);
        user.setLastLogin(LocalDateTime.now());
        em.merge(user);

        String token = TokenUtil.generate();
        UserSession session = new UserSession(user, token);
        sessionManager.register(session);

        LOG.info("Login successful: " + username + " from " + clientIp);
        return new LoginOutcome(LoginResult.SUCCESS, session, "مرحباً " + user.getUsername());
    }

    // ─── تسجيل الخروج ──────────────────────────────

    public void logout(String token) {
        sessionManager.invalidate(token);
    }

    // ─── Private ────────────────────────────────────

    @Transactional
    private void handleFailedAttempt(User user, String ip) {
        int failed = user.getFailedAttempts() + 1;
        user.setFailedAttempts(failed);

        if (failed >= MAX_FAILED) {
            user.setStatus("L"); // قفل الحساب
            LOG.warning("Account locked after " + failed + " failed attempts: " + user.getUsername());
        }

        em.merge(user);
        rateLimiter.recordFailure(ip);
        LOG.warning("Failed login attempt #" + failed + " for: " + user.getUsername() + " from " + ip);
    }

    private Optional<User> findByUsername(String username) {
        try {
            User u = em.createNamedQuery("User.findByUsername", User.class)
                       .setParameter("username", username)
                       .getSingleResult();
            return Optional.of(u);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }
}
