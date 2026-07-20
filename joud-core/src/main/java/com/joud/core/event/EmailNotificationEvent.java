package com.joud.core.event;

import java.util.logging.Logger;

/**
 * حدث عام يُرمى عند الحاجة لإرسال بريد إلكتروني
 */
public class EmailNotificationEvent {
    @SuppressWarnings("unused")
    private static final Logger LOG = Logger.getLogger(EmailNotificationEvent.class.getName());

    private final String recipient;
    private final String subject;
    private final String bodyHtml;

    public EmailNotificationEvent(String recipient, String subject, String bodyHtml) {
        this.recipient = recipient;
        this.subject = subject;
        this.bodyHtml = bodyHtml;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getSubject() {
        return subject;
    }

    public String getBodyHtml() {
        return bodyHtml;
    }
}