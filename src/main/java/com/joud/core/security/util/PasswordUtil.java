package com.joud.core.security.util;

import at.favre.lib.crypto.bcrypt.BCrypt;

/**
 * PasswordUtil — أداة تشفير وتحقق كلمات المرور باستخدام BCrypt
 *
 * BCrypt آمن لأنه:
 *   - يضيف salt عشوائي تلقائياً
 *   - بطيء بالتصميم (يصعب Brute Force)
 *   - كل hash مختلف حتى لنفس كلمة المرور
 */
public final class PasswordUtil {

    // cost factor: 12 = توازن جيد بين الأمان والسرعة
    private static final int COST = 12;

    private PasswordUtil() {}

    /**
     * تشفير كلمة مرور جديدة
     * استخدم عند إنشاء مستخدم أو تغيير كلمة المرور
     */
    public static String hash(String plainPassword) {
        if (plainPassword == null || plainPassword.isBlank())
            throw new IllegalArgumentException("Password cannot be empty");
        return BCrypt.withDefaults().hashToString(COST, plainPassword.toCharArray());
    }

    /**
     * التحقق من كلمة مرور مدخلة مقابل الـ hash المحفوظ
     */
    public static boolean verify(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null) return false;
        BCrypt.Result result = BCrypt.verifyer().verify(plainPassword.toCharArray(), storedHash);
        return result.verified;
    }
}
