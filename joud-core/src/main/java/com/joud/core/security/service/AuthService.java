package com.joud.core.security.service;

import com.joud.core.db.JoudRepository;
import com.joud.core.security.model.User;
import com.joud.core.security.session.SessionManager;
import com.joud.core.security.session.UserSession;
import com.joud.core.security.util.PasswordUtil;
import com.joud.core.security.util.RateLimiter;
import com.joud.core.security.util.TokenUtil;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AuthService — خدمة المصادقة الرئيسية
 *
 * تتولى:
 * - التحقق من بيانات الدخول
 * - إدارة محاولات الدخول الفاشلة
 * - إنشاء الجلسة بعد النجاح
 * - تسجيل الخروج
 *
 * الحفظ يتم عبر JoudRepository (معاملات يدوية)، لأن Tomcat لا يوفر JTA
 * فلا يعمل @Transactional فيه.
 *
 * ملاحظة أمنية: لا تسجّل كلمات المرور أو الـ Hash في أي سجل (Log).
 */
@ApplicationScoped
public class AuthService {

    private static final Logger LOG = Logger.getLogger(AuthService.class.getName());
    private static final int MAX_FAILED = 5;

    @Inject
    private JoudRepository db;

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

    public LoginOutcome login(String username, String password, String clientIp) {
        try {
            // 1. هل الـ IP محظور؟
            if (rateLimiter.isBlocked(clientIp)) {
                long mins = rateLimiter.getRemainingBlockMinutes(clientIp);
                return new LoginOutcome(LoginResult.IP_BLOCKED, null,
                        "تم تجاوز عدد المحاولات المسموح بها. حاول بعد " + mins + " دقيقة.");
            }

            // 2. ابحث عن المستخدم
            Optional<User> userOpt = (username == null || username.isBlank())
                    ? Optional.empty()
                    : findByUsername(username);
            if (userOpt.isEmpty()) {
                rateLimiter.recordFailure(clientIp);
                return new LoginOutcome(LoginResult.INVALID_CREDENTIALS, null,
                        "اسم المستخدم أو كلمة المرور غير صحيحة.");
            }

            User user = userOpt.get();

            // 3. هل الحساب مقفل؟
            if (user.isLocked()) {
                return new LoginOutcome(LoginResult.ACCOUNT_LOCKED, null, "الحساب مقفل مؤقتاً. تواصل مع المسؤول.");
            }

            // 4. هل الحساب نشط؟
            if (!user.isActive()) {
                return new LoginOutcome(LoginResult.ACCOUNT_INACTIVE, null, "الحساب غير نشط. تواصل مع المسؤول.");
            }

            // 5. تحقق من كلمة المرور
            if (!verifyPassword(password, user)) {
                handleFailedAttempt(user, clientIp);
                return new LoginOutcome(LoginResult.INVALID_CREDENTIALS, null,
                        "اسم المستخدم أو كلمة المرور غير صحيحة.");
            }

            // 6. نجح — صفّر العدّاد، سجّل آخر دخول، وأنشئ الجلسة
            rateLimiter.reset(clientIp);
            recordSuccessfulLogin(user);

            String token = TokenUtil.generate();
            UserSession session = new UserSession(user, token);
            sessionManager.register(session);

            LOG.info("Login successful: " + username + " from " + clientIp);
            return new LoginOutcome(LoginResult.SUCCESS, session, "مرحباً " + user.getUsername());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error during login", e);
            return new LoginOutcome(LoginResult.INVALID_CREDENTIALS, null,
                    "حدث خطأ أثناء تسجيل الدخول. يرجى المحاولة مرة أخرى.");
        }
    }

    // ─── تسجيل الخروج ──────────────────────────────

    public void logout(String token) {
        sessionManager.invalidate(token);
    }

    // ─── Private ────────────────────────────────────

    /** يتحقق من كلمة المرور دون تسجيل أي شيء منها أو من الـ Hash. */
    private boolean verifyPassword(String password, User user) {
        String hash = user.getPasswordHash();
        if (password == null || hash == null || hash.isBlank()) {
            return false;
        }
        return PasswordUtil.verify(password, hash.trim());
    }

    /** يبحث بالاسم المسمّى User.findByUsername (حسابات الحالة A فقط). */
    private Optional<User> findByUsername(String username) {
        return db.execute(em -> em.createNamedQuery("User.findByUsername", User.class)
                .setParameter("username", username)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst());
    }

    /** تحديث مباشر (UPDATE) كي لا نعيد كتابة صف المستخدم كاملاً من نسخة قديمة. */
    private void recordSuccessfulLogin(User user) {
        final LocalDateTime now = LocalDateTime.now();
        final Long userId = user.getId();

        db.executeInTransaction(em -> {
            em.createQuery("UPDATE User u SET u.failedAttempts = 0, u.lastLogin = :now WHERE u.id = :id")
                    .setParameter("now", now)
                    .setParameter("id", userId)
                    .executeUpdate();
        });

        user.setFailedAttempts(0);
        user.setLastLogin(now);
    }

    /**
     * يزيد عدّاد المحاولات الفاشلة بشكل ذرّي، ويقفل الحساب عند بلوغ الحد.
     * العدّاد يُزاد في قاعدة البيانات نفسها فلا تضيع محاولات متزامنة.
     */
    private void handleFailedAttempt(User user, String ip) {
        final Long userId = user.getId();

        Integer failed = db.executeInTransaction(em -> {
            em.createQuery("UPDATE User u SET u.failedAttempts = u.failedAttempts + 1 WHERE u.id = :id")
                    .setParameter("id", userId)
                    .executeUpdate();

            Integer count = em.createQuery("SELECT u.failedAttempts FROM User u WHERE u.id = :id", Integer.class)
                    .setParameter("id", userId)
                    .getSingleResult();

            if (count != null && count >= MAX_FAILED) {
                em.createQuery("UPDATE User u SET u.status = 'L' WHERE u.id = :id")
                        .setParameter("id", userId)
                        .executeUpdate();
            }
            return count;
        });

        int attempts = failed != null ? failed : 0;
        if (attempts >= MAX_FAILED) {
            LOG.warning("Account locked after " + attempts + " failed attempts: " + user.getUsername());
        }
        rateLimiter.recordFailure(ip);
        LOG.warning("Failed login attempt #" + attempts + " for: " + user.getUsername() + " from " + ip);
    }
}
