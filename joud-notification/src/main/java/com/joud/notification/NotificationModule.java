package com.joud.notification;

import com.joud.core.menu.MenuItem;
import com.joud.core.module.JoudModule;
import com.joud.notification.entity.SentEmailLog;

import java.util.List;
import java.util.Locale;

/**
 * NotificationModule — تسجيل وحدة الإشعارات في القلب
 *
 * يُكتشف تلقائياً عبر Java SPI عند التشغيل.
 * ملف التسجيل: META-INF/services/com.joud.core.module.JoudModule
 *
 * لا قوائم لها في الواجهة (تعمل في الخلفية عبر أحداث CDI)، لكنها تعلن كياناتها
 * كي يضمّها القلب إلى وحدة الاستمرارية تلقائياً عند وجود هذا الـ jar.
 */
public class NotificationModule implements JoudModule {

    @Override
    public String getModuleId() {
        return "notification";
    }

    @Override
    public String getModuleName(Locale locale) {
        return "ar".equals(locale.getLanguage()) ? "الإشعارات" : "Notifications";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription(Locale locale) {
        return "إرسال البريد الإلكتروني وتسجيله في سجل التدقيق";
    }

    @Override
    public List<MenuItem> getMenuItems(Locale locale) {
        return List.of();
    }

    @Override
    public int getMenuOrder() {
        return 900;
    }

    @Override
    public List<Class<?>> getEntityClasses() {
        return List.of(SentEmailLog.class);
    }
}
