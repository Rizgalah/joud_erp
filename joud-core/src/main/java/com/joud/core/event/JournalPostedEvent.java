package com.joud.core.event;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * حدث "تم اعتماد قيد محاسبي". يُطلق من وحدة المحاسبة بعد نجاح حفظ الترحيل
 * (بعد الـ commit)، وتلتقطه وحدة الإشعارات. يحمل لقطة من بيانات القيد فقط.
 */
public record JournalPostedEvent(
        Long journalEntryId,
        String reference,
        Long agentId,
        String countryCode,
        LocalDate date,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        Long currencyId,
        Long approvedBy) {
}
