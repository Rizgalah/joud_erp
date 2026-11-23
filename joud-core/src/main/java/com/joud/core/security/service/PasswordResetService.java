package com.joud.core.security.service;

import com.joud.core.db.JoudRepository;
import com.joud.core.security.model.PasswordResetToken;
import com.joud.core.security.model.User;
import com.joud.core.security.util.PasswordUtil;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;

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
 *
 * الحفظ عبر JoudRepository (معاملات يدوية) لأن Tomcat لا يوفر JTA.
 */
@ApplicationScoped
public class PasswordResetService {

    private static final Logger LOG = Logger.getLogger(PasswordResetService.class.getName());
    private static final int TOKEN_EXPIRY_MINUTES = 15;

    @Inject
    private JoudRepository db;

    // ─── 1. توليد وحفظ التوكن ─────────────────────

    /**
     * يبحث عن المستخدم بالبريد الإلكتروني ويولّد توكناً مؤقتاً.
     * الحسابات النشطة (A) والمقفلة (L) يمكنها طلب إعادة التعيين،
     * لأن إعادة التعيين هي طريقة فك القفل.
     *
     * @param email البريد الإلكتروني للمستخدم
     * @return التوكن المُولَّد، أو empty إذا لم يُوجد المستخدم
     */
    public Optional<String> generateResetToken(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }

        final String normalizedEmail = email.trim().toLowerCase();
        final String rawToken = generateSecureToken();

        // كل الخطوات في معاملة واحدة: البحث، إبطال القديم، حفظ الجديد
        Optional<Long> userId = db.executeInTransaction(em -> {
            List<Long> ids = em.createQuery(
                    "SELECT u.id FROM User u WHERE u.email = :email AND u.status IN ('A', 'L')",
                    Long.class)
                    .setParameter("email", normalizedEmail)
                    .setMaxResults(1)
                    .getResultList();
            if (ids.isEmpty()) {
                return Optional.<Long>empty();
            }
            Long uid = ids.get(0);

            em.createQuery("UPDATE PasswordResetToken t SET t.used = true WHERE t.userId = :uid AND t.used = false")
                    .setParameter("uid", uid)
                    .executeUpdate();

            PasswordResetToken prt = new PasswordResetToken();
            prt.setUserId(uid);
            prt.setToken(rawToken);
            prt.setExpiresAt(LocalDateTime.now().plusMinutes(TOKEN_EXPIRY_MINUTES));
            em.persist(prt);

            return Optional.of(uid);
        });

        if (userId.isEmpty()) {
            LOG.warning("Password reset requested for an unknown email.");
            return Optional.empty();
        }

        LOG.info("Password reset token generated for userId=" + userId.get());
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
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        final String token = rawToken.trim();

        return db.execute(em -> {
            List<PasswordResetToken> found = em.createQuery(
                    "SELECT t FROM PasswordResetToken t WHERE t.token = :tok",
                    PasswordResetToken.class)
                    .setParameter("tok", token)
                    .setMaxResults(1)
                    .getResultList();

            if (found.isEmpty()) {
                LOG.warning("Reset token not found.");
                return Optional.<User>empty();
            }
            PasswordResetToken prt = found.get(0);
            if (!prt.isValid()) {
                LOG.warning("Invalid or expired reset token used.");
                return Optional.<User>empty();
            }
            return Optional.ofNullable(em.find(User.class, prt.getUserId()));
        });
    }

    // ─── 3. توليد كلمة مرور جديدة وتطبيقها ───────

    /**
     * يولّد كلمة مرور عشوائية قوية، يحفظها للمستخدم، ويُبطل التوكن.
     * يُقفل صف التوكن أثناء المعاملة فلا يمكن استخدامه مرتين بالتوازي.
     *
     * @param rawToken التوكن المُتحقَّق منه
     * @return كلمة المرور الجديدة بنصها الصريح (لعرضها للمستخدم مرة واحدة)
     *         أو empty إذا فشل التحقق
     */
    public Optional<String> resetPassword(String rawToken) {
        // فحص سريع أولاً كي لا نُجري تشفيراً مكلفاً لتوكن غير صالح
        if (validateToken(rawToken).isEmpty()) {
            return Optional.empty();
        }
        final String token = rawToken.trim();

        // التشفير (BCrypt) بطيء، فنُجريه خارج المعاملة كي لا يطول قفل الصف
        final String newPassword = generateStrongPassword();
        final String newHash = PasswordUtil.hash(newPassword);

        Optional<Long> userId = db.executeInTransaction(em -> {
            List<PasswordResetToken> found = em.createQuery(
                    "SELECT t FROM PasswordResetToken t WHERE t.token = :tok",
                    PasswordResetToken.class)
                    .setParameter("tok", token)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .setMaxResults(1)
                    .getResultList();

            // إعادة التحقق بعد القفل (قد يكون استُخدم في هذه الأثناء)
            if (found.isEmpty() || !found.get(0).isValid()) {
                return Optional.<Long>empty();
            }
            PasswordResetToken prt = found.get(0);

            User user = em.find(User.class, prt.getUserId());
            if (user == null) {
                return Optional.<Long>empty();
            }
            // لا نُعيد تفعيل حساب عُطّل (I) بعد إصدار التوكن
            if (!"A".equals(user.getStatus()) && !"L".equals(user.getStatus())) {
                return Optional.<Long>empty();
            }

            user.setPasswordHash(newHash);
            user.setFailedAttempts(0);
            user.setStatus("A"); // إلغاء القفل إن وجد
            prt.setUsed(true);

            return Optional.of(user.getId());
        });

        if (userId.isEmpty()) {
            return Optional.empty();
        }
        LOG.info("Password reset successful for userId=" + userId.get());
        return Optional.of(newPassword);
    }

    // ─── Private Helpers ──────────────────────────

    /** توليد توكن URL-safe عشوائي (48 بايت = 64 حرف Base64) */
    private String generateSecureToken() {
        SecureRandom sr = new SecureRandom();
        byte[] bytes = new byte[48];
        sr.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * يولّد كلمة مرور عشوائية قوية:
     * - 2 أحرف كبيرة + 2 صغيرة + 2 أرقام + 1 رمز خاص + 7 عشوائية = 14 حرف
     */
    private String generateStrongPassword() {
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String lower = "abcdefghjkmnpqrstuvwxyz";
        String digits = "23456789";
        String special = "@#$%&*!";
        String all = upper + lower + digits + special;

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
        for (int i = 7; i < 14; i++) {
            sb.append(all.charAt(rnd.nextInt(all.length())));
        }

        // خلط الترتيب
        char[] arr = sb.toString().toCharArray();
        for (int i = arr.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            char tmp = arr[i];
            arr[i] = arr[j];
            arr[j] = tmp;
        }
        return new String(arr);
    }
}
