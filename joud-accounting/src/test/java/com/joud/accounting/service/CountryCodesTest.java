package com.joud.accounting.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CountryCodesTest {

    @Test
    @DisplayName("أسماء بلدان الشركة (عربي وإنجليزي) تتحول إلى رموزها")
    void knownCountries() {
        assertEquals("EG", CountryCodes.fromName("Egypt"));
        assertEquals("EG", CountryCodes.fromName("مصر"));
        assertEquals("SA", CountryCodes.fromName(" Saudi Arabia "));
        assertEquals("SA", CountryCodes.fromName("المملكة العربية السعودية"));
        assertEquals("AE", CountryCodes.fromName("UAE"));
        assertEquals("AE", CountryCodes.fromName("الإمارات العربية المتحدة"));
        assertEquals("DJ", CountryCodes.fromName("Djibouti"));
        assertEquals("DJ", CountryCodes.fromName("جيبوتي"));
    }

    @Test
    @DisplayName("الرمز المكتوب أصلاً يُقبل بأي حالة أحرف")
    void alreadyACode() {
        assertEquals("EG", CountryCodes.fromName("eg"));
        assertEquals("SA", CountryCodes.fromName("SA"));
    }

    @Test
    @DisplayName("قيمة فارغة أو غير معروفة تعطي XX")
    void unknownOrBlank() {
        assertEquals("XX", CountryCodes.fromName(null));
        assertEquals("XX", CountryCodes.fromName("  "));
        assertEquals("XX", CountryCodes.fromName("Atlantis"));
    }
}
