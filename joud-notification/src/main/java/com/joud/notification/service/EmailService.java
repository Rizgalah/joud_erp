
package com.joud.notification.service;

import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;

@ApplicationScoped // لجعل الكلاس متاحاً للحقن (CDI Injection) في أي مكان بالمشروع
public class EmailService {

    // نطلب من تومكات تزويدنا بالمُنفذ الخلفي لإدارة الخيوط البرمجية (Threads)
    @Resource
    private ManagedExecutorService executor;

    /**
     * إرسال بريد إلكتروني بشكل غير متزامن (Asynchronous) في الخلفية
     */
    public void sendEmailAsync(String to, String subject, String htmlContent) {
        // تسليم المهمة للخلفية فوراً لكي لا تنتظر واجهة المستخدم ثانية واحدة
        executor.submit(() -> {
            executeActualEmailSend(to, subject, htmlContent);
        });
    }

    /**
     * العملية الفردية للإرسال الفعلي (تُنفذ خلف الكواليس)
     */
    private void executeActualEmailSend(String to, String subject, String htmlContent) {
        // إعدادات الـ SMTP لسيرفر البريد الخاص بك
        Properties prop = new Properties();
        prop.put("mail.smtp.auth", "true");
        prop.put("mail.smtp.starttls.enable", "true");
        prop.put("mail.smtp.host", "://gmail.com"); // استبدله بسيرفر شركتك لاحقاً
        prop.put("mail.smtp.port", "587");

        Session session = Session.getInstance(prop, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                // بيانات الاعتماد الخاصة بالسيرفر
                return new PasswordAuthentication("your-system-email@company.com", "your-app-password");
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress("no-reply@joud-erp.com"));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            
            // دعم المحتوى المنسق بالـ HTML واللغة العربية بشكل صحيح
            message.setContent(htmlContent, "text/html; charset=utf-8");

            Transport.send(message);
            System.out.println("📬 [Joud ERP] تم إرسال البريد بنجاح إلى: " + to);

        } catch (MessagingException e) {
            System.err.println("❌ [Joud ERP] فشل إرسال البريد إلى: " + to + " بسبب: " + e.getMessage());
            // هنا يمكنك مستقبلاً تسجيل الفشل في قاعدة البيانات لإعادة المحاولة
        }
    }
}
