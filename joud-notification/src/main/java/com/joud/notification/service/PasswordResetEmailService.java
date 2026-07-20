package com.joud.notification.service;

import com.joud.notification.entity.SentEmailLog;
import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.Properties;

@ApplicationScoped
public class PasswordResetEmailService {

    @Resource
    private ManagedExecutorService executor;

    @PersistenceContext
    private EntityManager em;

    /**
     * إرسال رابط إعادة تعيين كلمة المرور بشكل غير متزامن بالكامل في الخلفية
     */
    public void sendPasswordResetAsync(String userEmail, String rawToken) {
        // تسليم العملية لـ Virtual Threads في الخلفية
        executor.submit(() -> {
            processAndSendEmail(userEmail, rawToken);
        });
    }

    private void processAndSendEmail(String userEmail, String rawToken) {
        String resetUrl = "https://joud-erp.com" + rawToken;
        String senderEmail = "no-reply@joud-erp.com";
        String subject = "طلب إعادة تعيين كلمة المرور - Joud ERP";
        String htmlContent = """
                <!DOCTYPE html>
                <html lang="ar" dir="rtl">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>إعادة تعيين كلمة المرور</title>
                </head>
                <body style="margin:0; padding:0; background-color:#f4f6f9; font-family: 'Segoe UI', Arial, sans-serif; direction:rtl;">
                    <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f4f6f9; padding: 40px 0;">
                        <tr>
                            <td align="center">
                                <table width="600" cellpadding="0" cellspacing="0" style="background-color:#ffffff; border-radius:12px; overflow:hidden; box-shadow:0 4px 20px rgba(0,0,0,0.08);">

                                    <!-- Header -->
                                    <tr>
                                        <td style="background: linear-gradient(135deg, #1a3c5e, #2d6a9f); padding: 36px 40px; text-align:center;">
                                            <h1 style="margin:0; color:#ffffff; font-size:26px; font-weight:700; letter-spacing:1px;">
                                                🔐 Joud ERP
                                            </h1>
                                            <p style="margin:8px 0 0; color:#a8d4f5; font-size:14px;">نظام إدارة الموارد المؤسسية</p>
                                        </td>
                                    </tr>

                                    <!-- Body -->
                                    <tr>
                                        <td style="padding: 40px 48px;">
                                            <h2 style="margin:0 0 16px; color:#1a3c5e; font-size:22px;">طلب إعادة تعيين كلمة المرور</h2>
                                            <p style="margin:0 0 12px; color:#555; font-size:15px; line-height:1.7;">
                                                تلقّينا طلباً لإعادة تعيين كلمة المرور الخاصة بحسابك.
                                                إذا لم تكن أنت من أرسل هذا الطلب، يمكنك تجاهل هذه الرسالة بأمان.
                                            </p>
                                            <p style="margin:0 0 28px; color:#555; font-size:15px; line-height:1.7;">
                                                انقر على الزر أدناه لإعادة تعيين كلمة المرور:
                                            </p>

                                            <!-- CTA Button -->
                                            <table cellpadding="0" cellspacing="0" style="margin: 0 auto 28px;">
                                                <tr>
                                                    <td style="background: linear-gradient(135deg, #2d6a9f, #1a3c5e); border-radius:8px;">
                                                        <a href="%s"
                                                           style="display:inline-block; padding:14px 36px; color:#ffffff; font-size:16px; font-weight:700; text-decoration:none; letter-spacing:0.5px;">
                                                            إعادة تعيين كلمة المرور ←
                                                        </a>
                                                    </td>
                                                </tr>
                                            </table>

                                            <!-- Expiry Warning -->
                                            <table cellpadding="0" cellspacing="0" width="100%%">
                                                <tr>
                                                    <td style="background-color:#fff8e1; border-right:4px solid #f5a623; border-radius:6px; padding:14px 18px;">
                                                        <p style="margin:0; color:#7a5500; font-size:14px;">
                                                            ⚠️ <strong>تنبيه:</strong> هذا الرابط صالح لمدة <strong>15 دقيقة</strong> فقط من وقت استلام هذا البريد.
                                                        </p>
                                                    </td>
                                                </tr>
                                            </table>

                                            <!-- Fallback link -->
                                            <p style="margin:24px 0 0; color:#999; font-size:12px; line-height:1.6;">
                                                إذا لم يعمل الزر، انسخ الرابط التالي وألصقه في متصفحك:<br>
                                                <a href="%s" style="color:#2d6a9f; word-break:break-all;">%s</a>
                                            </p>
                                        </td>
                                    </tr>

                                    <!-- Footer -->
                                    <tr>
                                        <td style="background-color:#f8f9fb; padding:24px 48px; border-top:1px solid #e8ecf0; text-align:center;">
                                            <p style="margin:0; color:#aaa; font-size:12px; line-height:1.8;">
                                                هذه رسالة آلية من نظام Joud ERP — لا تردّ عليها.<br>
                                                © %d Joud ERP. جميع الحقوق محفوظة.
                                            </p>
                                        </td>
                                    </tr>

                                </table>
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
                """
                .formatted(resetUrl, resetUrl, resetUrl, java.time.LocalDate.now().getYear());

        // 1. إنشاء سجل بدائي بحالة PENDING
        SentEmailLog emailLog = new SentEmailLog();
        emailLog.setSender(senderEmail);
        emailLog.setRecipient(userEmail);
        emailLog.setEmailType("PASSWORD_RESET");
        emailLog.setSubject(subject);
        emailLog.setStatus("PENDING");

        saveEmailLog(emailLog); // حفظ مبدئي

        // 2. إعدادات Jakarta Mail
        Properties prop = new Properties();
        prop.put("mail.smtp.auth", "true");
        prop.put("mail.smtp.starttls.enable", "true");
        prop.put("mail.smtp.host", "smtp.gmail.com");
        prop.put("mail.smtp.port", "587");

        Session session = Session.getInstance(prop, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication("your-email@company.com", "your-app-password");
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(senderEmail));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(userEmail));
            message.setSubject(subject);
            message.setContent(htmlContent, "text/html; charset=utf-8");

            // 3. المحاولة الفعالية للإرسال
            Transport.send(message);

            // 4. في حالة النجاح
            updateEmailStatus(emailLog.getId(), "SUCCESS", null);

        } catch (Exception e) {
            // 5. في حالة الفشل: التقاط الاستثناء وتحديث السجل بدلاً من إسقاط النظام
            updateEmailStatus(emailLog.getId(), "FAILED", e.getMessage());
        }
    }

    @Transactional
    protected void saveEmailLog(SentEmailLog log) {
        try {
            em.persist(log);
        } catch (Exception e) {
            System.err.println("فشل حفظ سجل البريد المبدئي: " + e.getMessage());
        }
    }

    @Transactional
    protected void updateEmailStatus(Object logId, String status, String errorMessage) {
        try {
            SentEmailLog log = em.find(SentEmailLog.class, logId);
            if (log != null) {
                log.setStatus(status);
                log.setErrorMessage(errorMessage);
                log.setSentAt(OffsetDateTime.now());
                em.merge(log);
            }
        } catch (Exception e) {
            System.err.println("فشل تحديث حالة سجل البريد: " + e.getMessage());
        }
    }
}
