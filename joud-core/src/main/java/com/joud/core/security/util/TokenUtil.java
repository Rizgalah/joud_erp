package com.joud.core.security.util;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * TokenUtil — توليد رموز الجلسات الآمنة
 * يستخدم SecureRandom لضمان العشوائية الكاملة
 */
public final class TokenUtil {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32; // 256 bit

    private TokenUtil() {}

    /**
     * توليد رمز جلسة عشوائي آمن
     * النتيجة: 43 حرف Base64 URL-safe
     */
    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * توليد رمز CSRF
     */
    public static String generateCsrf() {
        return generate();
    }
}
