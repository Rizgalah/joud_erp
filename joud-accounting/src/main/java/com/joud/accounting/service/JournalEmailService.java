package com.joud.accounting.service;

import com.joud.core.event.EmailNotificationEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

@ApplicationScoped
public class JournalEmailService {

    // حقن أداة إطلاق الأحداث من جاكرتا
    @Inject
    private Event<EmailNotificationEvent> emailEventPublisher;

    public void saveJournalEntry(String recipientEmail, double amount) {
        // 1. منطق حفظ القيد في قاعدة البيانات (السريع جداً)
        System.out.println("💾 تم حفظ القيد المحاسبي بقيمة: " + amount);

        // 2. إطلاق الحدث بشكل غير متزامن في الخلفية دون الانتظار (Async)
        String subject = "تأكيد تسجيل قيد مالي جديد";
        String htmlContent = "<h3>مرحباً،</h3><p>تم تسجيل قيد مالي جديد بقيمة: <b>" + amount + " $</b></p>";

        emailEventPublisher.fireAsync(new EmailNotificationEvent(recipientEmail, subject, htmlContent));

        System.out.println("⚡ تم إطلاق حدث البريد والرجوع للمستخدم فوراً!");
    }
}
