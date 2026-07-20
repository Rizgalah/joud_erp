package com.joud.core.security.session;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.joud.security.util.SecurityResponseUtil;

/**
 * SessionManager — مدير الجلسات (Singleton على مستوى التطبيق)
 *
 * يحتفظ بجميع الجلسات النشطة في الذاكرة.
 * يتحقق من انتهاء الجلسة تلقائياً عند كل طلب.
 * مدة الجلسة الافتراضية: 30 دقيقة (قابلة للضبط من AGENT_BROFILE).
 */
@ApplicationScoped
@Named("sessionManager")
public class SessionManager {

    private static final Logger LOG = Logger.getLogger(SessionManager.class.getName());

    // مدة الجلسة الافتراضية بالدقائق
    private static final int DEFAULT_TIMEOUT_MINUTES = 30;

    // الجلسات النشطة: token → UserSession
    private final Map<String, UserSession> activeSessions = new ConcurrentHashMap<>();

    // ─── تسجيل جلسة جديدة ──────────────────────────

    public void register(UserSession session) {
        activeSessions.put(session.getSessionToken(), session);
        LOG.info("Session registered for user: " + session.getUsername());
    }

    // ─── البحث عن جلسة ─────────────────────────────

    public Optional<UserSession> find(String token) {
        if (token == null || token.isBlank())
            return Optional.empty();

        UserSession session = activeSessions.get(token);
        if (session == null)
            return Optional.empty();

        // تحقق من انتهاء الجلسة
        if (isExpired(session)) {
            invalidate(token);
            SecurityResponseUtil.createErrorResponse(
                    Response.Status.UNAUTHORIZED,
                    "صلاحية الوصول انتهت أو غير موجودة. يرجى تسجيل الدخول مجدداً.",
                    "/auth/reset-password");
            LOG.info("Session expired for user: " + session.getUsername());

            return Optional.empty();
        }

        // تجديد وقت آخر نشاط
        session.touch();
        return Optional.of(session);
    }

    // ─── إلغاء جلسة (تسجيل الخروج) ────────────────

    public void invalidate(String token) {
        UserSession removed = activeSessions.remove(token);
        if (removed != null) {
            LOG.info("Session invalidated for user: " + removed.getUsername());
        }
    }

    // ─── تنظيف الجلسات المنتهية ─────────────────────
    // يُستدعى من Scheduler كل 10 دقائق

    public void cleanup() {
        int before = activeSessions.size();
        activeSessions.entrySet().removeIf(e -> isExpired(e.getValue()));
        int removed = before - activeSessions.size();
        if (removed > 0)
            LOG.info("Session cleanup: removed " + removed + " expired session(s).");
    }

    // ─── إحصائيات ───────────────────────────────────

    public int getActiveCount() {
        return activeSessions.size();
    }

    public boolean isActive(String token) {
        return find(token).isPresent();
    }

    // ─── Private ────────────────────────────────────

    private boolean isExpired(UserSession session) {
        return session.getLastActivity()
                .plusMinutes(DEFAULT_TIMEOUT_MINUTES)
                .isBefore(LocalDateTime.now());
    }
}
