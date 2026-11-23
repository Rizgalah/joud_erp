package com.joud.accounting.service;

import java.util.Locale;
import java.util.Map;

/**
 * يحوّل قيمة العمود AGENT_MAST.COUNTRY (نص حر: اسم بلد أو رمز) إلى رمز البلد
 * المستخدم في JOURNAL_ENTRY و DAY_LEDG و GENLDG_MAST (COUNTRY_C_CODE).
 * فروع الشركة: مصر، السعودية، الإمارات، جيبوتي. أي قيمة غير معروفة تُعطي "XX".
 */
public final class CountryCodes {

    public static final String UNKNOWN = "XX";

    private static final Map<String, String> BY_NAME = Map.ofEntries(
            Map.entry("egypt", "EG"), Map.entry("egy", "EG"), Map.entry("eg", "EG"),
            Map.entry("مصر", "EG"),
            Map.entry("saudi arabia", "SA"), Map.entry("saudi", "SA"), Map.entry("ksa", "SA"),
            Map.entry("sau", "SA"), Map.entry("sa", "SA"),
            Map.entry("السعودية", "SA"), Map.entry("المملكة العربية السعودية", "SA"),
            Map.entry("united arab emirates", "AE"), Map.entry("uae", "AE"), Map.entry("are", "AE"),
            Map.entry("ae", "AE"),
            Map.entry("الإمارات", "AE"), Map.entry("الامارات", "AE"),
            Map.entry("الإمارات العربية المتحدة", "AE"), Map.entry("الامارات العربية المتحدة", "AE"),
            Map.entry("djibouti", "DJ"), Map.entry("dji", "DJ"), Map.entry("dj", "DJ"),
            Map.entry("جيبوتي", "DJ"));

    private CountryCodes() {
    }

    public static String fromName(String country) {
        if (country == null || country.isBlank()) {
            return UNKNOWN;
        }
        return BY_NAME.getOrDefault(country.trim().toLowerCase(Locale.ROOT), UNKNOWN);
    }
}
