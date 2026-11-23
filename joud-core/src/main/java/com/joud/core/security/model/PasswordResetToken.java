package com.joud.core.security.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * PasswordResetToken — رمز إعادة تعيين كلمة المرور
 *
 * يُخزَّن في قاعدة البيانات مع:
 * - userId: مرتبط بالمستخدم
 * - token: UUID عشوائي مُرسَل في رابط البريد
 * - expiresAt: وقت انتهاء الصلاحية (15 دقيقة)
 * - used: هل استُخدم مسبقاً؟
 */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_i_id")
    private Long id;

    @Column(name = "user_i_id", nullable = false)
    private Long userId;

    @Column(name = "token_c_value", nullable = false, unique = true, length = 100)
    private String token;

    @Column(name = "token_d_expires", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "token_b_used", nullable = false)
    private boolean used = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // ─── Helpers ──────────────────────────────────

    /** هل التوكن صالح للاستخدام؟ (لم يُستخدم ولم تنتهِ صلاحيته) */
    public boolean isValid() {
        return !used && LocalDateTime.now().isBefore(expiresAt);
    }

    // ─── Getters & Setters ────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public boolean isUsed() { return used; }
    public void setUsed(boolean used) { this.used = used; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
