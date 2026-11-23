package com.joud.notification.listener;

import com.joud.core.event.JournalPostedEvent;
import com.joud.notification.config.MailSettings;
import com.joud.notification.service.EmailService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

import java.util.List;
import java.util.logging.Logger;

/**
 * يرسل إشعار بريد عند اعتماد قيد محاسبي (بعد نجاح حفظه في قاعدة البيانات).
 * المستلمون من الإعداد joud.notify.journal / JOUD_NOTIFY_JOURNAL.
 * وكل رسالة تُسجَّل في audit.sent_emails_log (نوع JOURNAL_ENTRY_POSTED).
 */
@ApplicationScoped
public class JournalPostedNotificationObserver {

    private static final Logger LOG = Logger.getLogger(JournalPostedNotificationObserver.class.getName());
    static final String EMAIL_TYPE = "JOURNAL_ENTRY_POSTED";

    @Inject
    private EmailService emailService;

    @Inject
    private MailSettings settings;

    public void onJournalPosted(@ObservesAsync JournalPostedEvent event) {
        List<String> recipients = settings.journalRecipients();
        if (recipients.isEmpty()) {
            LOG.fine("No journal notification recipients configured; skipping.");
            return;
        }

        emailService.sendEmailAsync(
                String.join(",", recipients),
                "تم اعتماد القيد " + safe(event.reference()),
                buildHtml(event),
                EMAIL_TYPE);
    }

    static String buildHtml(JournalPostedEvent e) {
        return """
                <div dir="rtl" style="font-family:Tahoma,Arial,sans-serif;">
                  <h3>تم اعتماد قيد محاسبي</h3>
                  <table cellpadding="6" style="border-collapse:collapse;">
                    <tr><td>رقم القيد</td><td>%s</td></tr>
                    <tr><td>التاريخ</td><td>%s</td></tr>
                    <tr><td>الفرع</td><td>%s</td></tr>
                    <tr><td>البلد</td><td>%s</td></tr>
                    <tr><td>إجمالي المدين</td><td>%s</td></tr>
                    <tr><td>إجمالي الدائن</td><td>%s</td></tr>
                    <tr><td>العملة</td><td>%s</td></tr>
                    <tr><td>اعتمده المستخدم</td><td>%s</td></tr>
                  </table>
                </div>
                """.formatted(
                safe(e.reference()),
                safe(e.date()),
                safe(e.agentId()),
                safe(e.countryCode()),
                e.totalDebit() == null ? "-" : e.totalDebit().toPlainString(),
                e.totalCredit() == null ? "-" : e.totalCredit().toPlainString(),
                safe(e.currencyId()),
                safe(e.approvedBy()));
    }

    /** نص آمن للـ HTML (القيم قادمة من بيانات المستخدم). */
    private static String safe(Object value) {
        if (value == null) {
            return "-";
        }
        return value.toString()
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
