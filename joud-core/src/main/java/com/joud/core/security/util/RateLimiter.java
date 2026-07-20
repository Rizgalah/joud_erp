package com.joud.core.security.util;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RateLimiter — حماية ضد Brute Force
 *
 * يحسب عدد محاولات تسجيل الدخول الفاشلة لكل IP.
 * بعد MAX_ATTEMPTS يحجب الـ IP لمدة BLOCK_MINUTES دقيقة.
 */
@ApplicationScoped
public class RateLimiter {

    private static final int MAX_ATTEMPTS   = 5;
    private static final int BLOCK_MINUTES  = 15;
    private static final int WINDOW_MINUTES = 10;

    private record Attempt(int count, LocalDateTime firstAttempt, LocalDateTime blockedUntil) {}

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    /**
     * هل هذا الـ IP محظور حالياً؟
     */
    public boolean isBlocked(String ip) {
        Attempt a = attempts.get(ip);
        if (a == null) return false;
        if (a.blockedUntil() != null && a.blockedUntil().isAfter(LocalDateTime.now())) return true;
        // انتهى الحجب — أعد التهيئة
        if (a.blockedUntil() != null) {
            attempts.remove(ip);
            return false;
        }
        // انتهت نافذة العد
        if (a.firstAttempt().plusMinutes(WINDOW_MINUTES).isBefore(LocalDateTime.now())) {
            attempts.remove(ip);
            return false;
        }
        return false;
    }

    /**
     * تسجيل محاولة فاشلة — يعيد true إذا تم الحجب الآن
     */
    public boolean recordFailure(String ip) {
        Attempt current = attempts.getOrDefault(ip,
            new Attempt(0, LocalDateTime.now(), null));

        int newCount = current.count() + 1;
        LocalDateTime blockedUntil = null;

        if (newCount >= MAX_ATTEMPTS) {
            blockedUntil = LocalDateTime.now().plusMinutes(BLOCK_MINUTES);
        }

        attempts.put(ip, new Attempt(newCount, current.firstAttempt(), blockedUntil));
        return blockedUntil != null;
    }

    /**
     * إعادة تهيئة عداد الـ IP عند تسجيل الدخول الناجح
     */
    public void reset(String ip) {
        attempts.remove(ip);
    }

    /**
     * الوقت المتبقي للحجب بالدقائق (0 إذا لم يكن محجوباً)
     */
    public long getRemainingBlockMinutes(String ip) {
        Attempt a = attempts.get(ip);
        if (a == null || a.blockedUntil() == null) return 0;
        long mins = java.time.Duration.between(LocalDateTime.now(), a.blockedUntil()).toMinutes();
        return Math.max(0, mins);
    }
}
