package com.joud.notification.listener;

import com.joud.core.event.JournalPostedEvent;
import com.joud.notification.config.MailSettings;
import com.joud.notification.service.EmailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JournalPostedNotificationObserverTest {

    @Mock
    private EmailService emailService;

    @Mock
    private MailSettings settings;

    @InjectMocks
    private JournalPostedNotificationObserver observer;

    private JournalPostedEvent event(String reference) {
        return new JournalPostedEvent(10L, reference, 1L, "EG", LocalDate.of(2026, 11, 17),
                new BigDecimal("1000.50"), new BigDecimal("1000.50"), 2L, 99L);
    }

    @Test
    @DisplayName("اعتماد قيد يرسل إشعاراً للمستلمين المضبوطين، وبه رقم القيد والمبلغ")
    void sendsToConfiguredRecipients() {
        when(settings.journalRecipients()).thenReturn(List.of("a@x.com", "b@x.com"));

        observer.onJournalPosted(event("JV-10"));

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmailAsync(eq("a@x.com,b@x.com"), subject.capture(), html.capture(),
                eq("JOURNAL_ENTRY_POSTED"));
        assertTrue(subject.getValue().contains("JV-10"));
        assertTrue(html.getValue().contains("JV-10"));
        assertTrue(html.getValue().contains("1000.50"));
    }

    @Test
    @DisplayName("لا يُرسل شيء إن لم تُضبط قائمة المستلمين")
    void noRecipientsMeansNoEmail() {
        when(settings.journalRecipients()).thenReturn(List.of());

        observer.onJournalPosted(event("JV-10"));

        verify(emailService, never()).sendEmailAsync(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("نص القيد يُهرَّب في HTML فلا يمكن حقن وسوم")
    void htmlIsEscaped() {
        String html = JournalPostedNotificationObserver.buildHtml(event("JV-<b>&"));

        assertTrue(html.contains("JV-&lt;b&gt;&amp;"));
        assertFalse(html.contains("JV-<b>"));
    }

    @Test
    @DisplayName("قيم ناقصة تظهر كشرطة دون أخطاء")
    void nullFieldsRenderAsDash() {
        JournalPostedEvent sparse = new JournalPostedEvent(1L, null, null, null, null, null, null, null, null);

        assertTrue(JournalPostedNotificationObserver.buildHtml(sparse).contains("<td>-</td>"));
    }
}
