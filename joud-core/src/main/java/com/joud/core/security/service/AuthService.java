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
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * AuthService — خدمة المصادقة الرئيسية
 *
 * تتولى:
 * - التحقق من بيانات الدخول
 * - إدارة محاولات الدخول الفاشلة
 * - إنشاء الجلسة بعد النجاح
 * - تسجيل الخروج
 */
@ApplicationScoped
public class AuthService {

    private static final Logger LOG = Logger.getLogger(AuthService.class.getName());
    private static final int MAX_FAILED = 5;

    @Inject
    private EntityManager em;

    @Inject
    private SessionManager sessionManager;
    @Inject
    private RateLimiter rateLimiter;

    // ─── نتيجة محاولة الدخول ───────────────────────

    public enum LoginResult {
        SUCCESS,
        INVALID_CREDENTIALS, // بيانات خاطئة
        ACCOUNT_LOCKED, // الحساب مقفل
        IP_BLOCKED, // IP محظور بسبب Brute Force
        ACCOUNT_INACTIVE // الحساب غير نشط
    }

    public record LoginOutcome(LoginResult result, UserSession session, String message) {
    }

    // ─── تسجيل الدخول ──────────────────────────────

    @Transactional
    public LoginOutcome login(String username, String password, String clientIp) {
        try {
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
                return new LoginOutcome(LoginResult.INVALID_CREDENTIALS, null,
                        "اسم المستخدم أو كلمة المرور غير صحيحة.");
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
            if (!PasswordUtil.verify(password, user.getPasswordHash().trim())) {
                handleFailedAttempt(user, clientIp);
                return new LoginOutcome(LoginResult.INVALID_CREDENTIALS, null,
                        "اسم المستخدم أو كلمة المرور غير صحيحة.");
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
        } catch (Exception e) {

            e.printStackTrace();
            LOG.severe("Error during login: " + e.getMessage());
            return new LoginOutcome(LoginResult.INVALID_CREDENTIALS, null,
                    "حدث خطأ أثناء تسجيل الدخول. يرجى المحاولة مرة أخرى.");
        }
    }

    public LoginResult authenticate(String username, String password) {

        // 2. ابحث عن المستخدم
        Optional<User> userOpt = findByUsername(username);
        if (userOpt.isEmpty()) {
            return LoginResult.INVALID_CREDENTIALS;

        }

        User user = userOpt.get();

        // 3. هل الحساب نشط؟
        if (!user.isActive()) {
            return LoginResult.ACCOUNT_INACTIVE;

        }

        // 4. هل الحساب مقفل؟
        if (user.isLocked()) {
            return LoginResult.ACCOUNT_LOCKED;

        }

        System.out.println("👉 PASSWORD FROM UI: " + password);
        System.out.println("👉 HASH FROM DB: [" + user.getPasswordHash() + "] LENGTH: "
                + (user.getPasswordHash() != null ? user.getPasswordHash().length() : 0));
        // 🔥 أضف هذا السطر المؤقت هنا:
        System.out.println("🔥 GENERATED VALID HASH: " + com.joud.core.security.util.PasswordUtil.hash("admin123"));
        // 5. تحقق من كلمة المرور
        if (!PasswordUtil.verify(password, user.getPasswordHash().trim())) {
            return LoginResult.INVALID_CREDENTIALS;

        }

        // 6. نجح — أعد التهيئة وأنشئ الجلسة
        // rateLimiter.reset(clientIp);
        user.setFailedAttempts(0);
        user.setLastLogin(LocalDateTime.now());
        em.merge(user);

        String token = TokenUtil.generate();
        UserSession session = new UserSession(user, token);
        sessionManager.register(session);

        LOG.info("Login successful: " + username + " from ");
        // return new LoginOutcome(LoginResult.SUCCESS, session, "مرحباً " +
        // user.getUsername());
        return LoginResult.SUCCESS;

    }
    // ─── تسجيل الخروج ──────────────────────────────

    public void logout(String token) {
        sessionManager.invalidate(token);
    }

    // ─── Private ────────────────────────────────────

    @Transactional
    private void handleFailedAttempt(User user, String ip) {
        int failed = (user.getFailedAttempts() != null) ? user.getFailedAttempts() : 0;
        // if (failed >= 3) { ... }
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
