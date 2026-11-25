package com.joud.core.security.bean;

import com.joud.core.event.PasswordResetRequestedEvent;
import com.joud.core.security.service.PasswordResetService;
import jakarta.faces.view.ViewScoped;
import jakarta.enterprise.event.Event;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.util.Optional;

/**
 * ForgotPasswordBean — backing bean لشاشة "نسيت كلمة المرور"
 *
 * يستقبل البريد الإلكتروني → يولّد توكناً مؤقتاً →
 * يُرسل رابط الإعادة بشكل غير متزامن عبر PasswordResetEmailService.
 *
 * ملاحظة أمنية: الرسالة نفسها تظهر سواء وُجد البريد أم لا
 * (لمنع تسريب بيانات الحسابات — Timing-safe response).
 */
@Named("forgotBean")
@ViewScoped
public class ForgotPasswordBean implements Serializable {

    @Inject private PasswordResetService      resetService;
    @Inject private Event<PasswordResetRequestedEvent> resetRequestedEvent;

    private String  email;
    private boolean sent = false;

    // ─── Action ──────────────────────────────────────

    public void sendResetLink() {
        if (email == null || email.isBlank()) {
            addError("البريد الإلكتروني مطلوب");
            return;
        }

        try {
            Optional<String> tokenOpt = resetService.generateResetToken(email.trim().toLowerCase());

         /*   if (tokenOpt.isPresent()) {
                // إرسال البريد بشكل غير متزامن (Virtual Threads)
                emailService.sendPasswordResetAsync(email.trim(), tokenOpt.get());
            }*/
            if (tokenOpt.isPresent()) {
            	// إرسال البريد بشكل غير متزامن (Virtual Threads)
                resetRequestedEvent.fire(new PasswordResetRequestedEvent(email.trim(), tokenOpt.get()));
            }
            // نُظهر نفس الرسالة دائماً (Timing-safe — لا نكشف هل البريد موجود أم لا)
            sent = true;

        } catch (Exception e) {
            // حتى في حالة الخطأ: لا نكشف التفاصيل للمستخدم
            sent = true;
        }
    }

    // ─── Helpers ─────────────────────────────────────

    private void addError(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(FacesMessage.SEVERITY_ERROR, msg, null));
    }

    // ─── Getters & Setters ────────────────────────────
    public String  getEmail()           { return email; }
    public void    setEmail(String v)   { this.email = v; }
    public boolean isSent()             { return sent; }
}
