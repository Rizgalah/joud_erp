package com.joud.core.security.service;

import com.joud.core.security.model.PasswordResetToken;
import com.joud.core.security.model.User;
import com.joud.core.security.util.PasswordUtil;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * PasswordResetService — خدمة إعادة تعيين كلمة المرور
 *
 * المسؤوليات:
 *   1. توليد توكن عشوائي آمن ومؤقت وحفظه
 *   2. التحقق من صلاحية التوكن وإيجاد المستخدم
 *   3. توليد كلمة مرور جديدة وتحديثها
 */
@ApplicationScoped
public class PasswordResetService {

    private static final Logger LOG = Logger.getLogger(PasswordResetService.class.getName());
    private static final int TOKEN_EXPIRY_MINUTES = 15;

    @PersistenceContext
    private EntityManager em;

    // ─── 1. توليد وحفظ التوكن ─────────────────────

    /**
     * يبحث عن المستخدم بالبريد الإلكتروني ويولّد توكناً مؤقتاً.
     *
     * @param email البريد الإلكتروني للمستخدم
     * @return التوكن المُولَّد، أو empty إذا لم يُوجد المستخدم
     */
    @Transactional
    public Optional<String> generateResetToken(String email) {
        if (email == null || email.isBlank()) return Optional.empty();

        // البحث عن المستخدم بالبريد
        Optional<User> userOpt = findUserByEmail(email.trim().toLowerCase());
        if (userOpt.isEmpty()) {
            LOG.warning("Password reset requested for unknown email: " + email);
            return Optional.empty();
        }

        Long userId = userOpt.get().getId();

        // إلغاء أي توكنات قديمة لنفس المستخدم
        invalidateOldTokens(userId);

        // توليد توكن عشوائي آمن (URL-safe Base64)
        String rawToken = generateSecureToken();

        // حفظ التوكن في قاعدة البيانات
        PasswordResetToken prt = new PasswordResetToken();
        prt.setUserId(userId);
        prt.setToken(rawToken);
        prt.setExpiresAt(LocalDateTime.now().plusMinutes(TOKEN_EXPIRY_MINUTES));
        em.persist(prt);

        LOG.info("Password reset token generated for userId=" + userId);
        return Optional.of(rawToken);
    }

    // ─── 2. التحقق من التوكن وإيجاد المستخدم ─────

    /**
     * يتحقق من صلاحية التوكن ويُعيد المستخدم المرتبط به.
     *
     * @param rawToken التوكن من الرابط في البريد الإلكتروني
     * @return المستخدم إذا كان التوكن صالحاً، أو empty
     */
    public Optional<User> validateToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return Optional.empty();

        try {
            PasswordResetToken prt = em.createQuery(
                    "SELECT t FROM PasswordResetToken t WHERE t.token = :tok",
                    PasswordResetToken.class)
                    .setParameter("tok", rawToken.trim())
                    .getSingleResult();

            if (!prt.isValid()) {
                LOG.warning("Invalid or expired reset token used.");
                return Optional.empty();
            }

            // جلب المستخدم
            return Optional.ofNullable(em.find(User.class, prt.getUserId()));

        } catch (NoResultException e) {
            LOG.warning("Reset token not found: " + rawToken);
            return Optional.empty();
        }
    }

    // ─── 3. توليد كلمة مرور جديدة وتطبيقها ───────

    /**
     * يولّد كلمة مرور عشوائية قوية، يحفظها للمستخدم، ويُبطل التوكن.
     *
     * @param rawToken التوكن المُتحقَّق منه
     * @return كلمة المرور الجديدة بنصها الصريح (لعرضها للمستخدم مرة واحدة)
     *         أو empty إذا فشل التحقق
     */
    @Transactional
    public Optional<String> resetPassword(String rawToken) {
        Optional<User> userOpt = validateToken(rawToken);
        if (userOpt.isEmpty()) return Optional.empty();

        User user = userOpt.get();

        // توليد كلمة مرور جديدة قوية
        String newPassword = generateStrongPassword();

        // تشفير وحفظ كلمة المرور الجديدة
        user.setPasswordHash(PasswordUtil.hash(newPassword));
        user.setFailedAttempts(0);
        user.setStatus("A"); // إلغاء القفل إن وجد
        em.merge(user);

        // إبطال التوكن المستخدم
        markTokenAsUsed(rawToken);

        LOG.info("Password reset successful for userId=" + user.getId());
        return Optional.of(newPassword);
    }

    // ─── Private Helpers ──────────────────────────

    private Optional<User> findUserByEmail(String email) {
        try {
            return Optional.of(em.createNamedQuery("User.findByEmail", User.class)
                    .setParameter("email", email)
                    .getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    @Transactional
    private void invalidateOldTokens(Long userId) {
        List<PasswordResetToken> old = em.createQuery(
                "SELECT t FROM PasswordResetToken t WHERE t.userId = :uid AND t.used = false",
                PasswordResetToken.class)
                .setParameter("uid", userId)
                .getResultList();
        old.forEach(t -> { t.setUsed(true); em.merge(t); });
    }

    @Transactional
    private void markTokenAsUsed(String rawToken) {
        try {
            PasswordResetToken prt = em.createQuery(
                    "SELECT t FROM PasswordResetToken t WHERE t.token = :tok",
                    PasswordResetToken.class)
                    .setParameter("tok", rawToken)
                    .getSingleResult();
            prt.setUsed(true);
            em.merge(prt);
        } catch (NoResultException ignored) {}
    }

    /** توليد توكن URL-safe عشوائي (48 بايت = 64 حرف Base64) */
    private String generateSecureToken() {
        SecureRandom sr = new SecureRandom();
        byte[] bytes = new byte[48];
        sr.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * يولّد كلمة مرور عشوائية قوية:
     * - 4 أحرف كبيرة + 4 صغيرة + 4 أرقام + 2 رمز خاص = 14 حرف
     */
    private String generateStrongPassword() {
        String upper   = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String lower   = "abcdefghjkmnpqrstuvwxyz";
        String digits  = "23456789";
        String special = "@#$%&*!";
        String all     = upper + lower + digits + special;

        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder();

        // ضمان وجود كل نوع على الأقل
        sb.append(upper.charAt(rnd.nextInt(upper.length())));
        sb.append(upper.charAt(rnd.nextInt(upper.length())));
        sb.append(lower.charAt(rnd.nextInt(lower.length())));
        sb.append(lower.charAt(rnd.nextInt(lower.length())));
        sb.append(digits.charAt(rnd.nextInt(digits.length())));
        sb.append(digits.charAt(rnd.nextInt(digits.length())));
        sb.append(special.charAt(rnd.nextInt(special.length())));

        // باقي الأحرف عشوائية
        for (int i = 7; i < 14; i++)
            sb.append(all.charAt(rnd.nextInt(all.length())));

        // خلط الترتيب
        char[] arr = sb.toString().toCharArray();
        for (int i = arr.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            char tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp;
        }
        return new String(arr);
    }
}
