package com.joud.notification.listener;

import com.joud.core.event.EmailNotificationEvent;
import com.joud.notification.service.EmailService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

@ApplicationScoped
public class EmailEventListener {

    @Inject
    private EmailService emailService;

    /**
     * هذه الدالة تعمل كـ "مراقب" (Observer).
     * تعمل تلقائياً في خيط خلفي (Virtual Thread) فور إطلاق الحدث من أي مكان في
     * النظام.
     */
    public void onEmailRequested(@ObservesAsync EmailNotificationEvent event) {
        System.out.println("🎧 [joud-notification] تم التقاط حدث إرسال بريد إلى: " + event.getRecipient());

        // استدعاء خدمة الإرسال الفعلية
        emailService.sendEmailAsync(
                event.getRecipient(),
                event.getSubject(),
                event.getBodyHtml());
    }
}
