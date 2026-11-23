package com.joud.core.web.bean;

import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.Locale;

/**
 * LocaleBean — مدير الجلسة والترجمة واتجاه العرض
 *
 * @SessionScoped يضمن احتفاظ كل مستخدم بلغته طوال جلسته.
 * يُقرأ في template.xhtml لضبط lang/dir على <html> وفي
 * <f:view locale="#{localeBean.locale}"> لتحميل ResourceBundle الصحيح.
 *
 * مسار الملف النهائي:
 *   joud-core/src/main/java/com/joud/core/web/bean/LocaleBean.java
 */
@Named("localeBean")
@SessionScoped
public class LocaleBean implements Serializable {
    private static final long serialVersionUID = 1L;

    // ─── الإعدادات الافتراضية (عربي RTL) ─────────────────
    private Locale locale = Locale.of("ar");
    private String dir    = "rtl";

    // ─── Getters ──────────────────────────────────────────

    /** كائن Locale المُستخدَم في <f:view locale="#{localeBean.locale}"> */
    public Locale getLocale()   { return locale; }

    /** "rtl" أو "ltr" — يُستخدَم في <html dir="#{localeBean.dir}"> */
    public String getDir()      { return dir; }

    /** "ar" أو "en" — يُستخدَم في <html lang="#{localeBean.language}"> */
    public String getLanguage() { return locale.getLanguage(); }

    /** هل اللغة الحالية عربية؟ — لتبسيط الشروط في XHTML */
    public boolean isArabic()   { return "ar".equals(locale.getLanguage()); }

    // ─── تبديل اللغة ──────────────────────────────────────

    /**
     * يُستدعى من زر مبدِّل اللغة في template.xhtml
     *
     * @param lang "ar" أو "en"
     * @return null (بقاء في نفس الصفحة بعد التحديث)
     */
    public String changeLanguage(String lang) {
        if ("en".equalsIgnoreCase(lang)) {
            this.locale = Locale.of("en");
            this.dir    = "ltr";
        } else {
            this.locale = Locale.of("ar");
            this.dir    = "rtl";
        }
        return null; // يبقى في نفس الصفحة — JSF يعيد تصيير الـ view
    }
}
