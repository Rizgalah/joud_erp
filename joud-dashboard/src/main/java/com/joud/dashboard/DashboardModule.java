package com.joud.dashboard;

import com.joud.core.menu.MenuItem;
import com.joud.core.module.JoudModule;
import com.joud.core.report.ReportDefinition;

import java.util.List;
import java.util.Locale;

/**
 * DashboardModule — تسجيل لوحة التحكم
 *
 * ملاحظة معمارية مهمة:
 * لوحة التحكم لا تُضيف getRequiredModules() لأي وحدة —
 * فهي تعمل حتى لو لم تكن بعض الوحدات (محاسبة/HR/تجارة/مخزون) مثبتة.
 * كل استعلام في DashboardBean محاط بـ try/catch مستقل، فإذا كان
 * جدول معين غير موجود (الوحدة غير مثبتة)، يُتجاهل ذلك المؤشر فقط
 * بدلاً من تعطل الشاشة كاملة.
 *
 * تُسجَّل كعنصر قائمة عادي ("لوحة التحكم") بالإضافة لكونها
 * الصفحة الافتراضية index.xhtml في القلب — المستخدم يصل إليها
 * تلقائياً بعد تسجيل الدخول.
 */
public class DashboardModule implements JoudModule {

    @Override public String getModuleId() { return "dashboard"; }
    @Override public String getVersion()  { return "1.0.0"; }

    @Override
    public String getModuleName(Locale l) {
        return "ar".equals(l.getLanguage()) ? "لوحة التحكم" : "Dashboard";
    }

    @Override
    public String getDescription(Locale l) {
        return "مؤشرات الأداء الرئيسية لجميع الوحدات: مبيعات، أرباح، مخزون، رواتب";
    }

    // أول عنصر في القائمة دائماً
    @Override public int getMenuOrder() { return 1; }

    @Override
    public List<MenuItem> getMenuItems(Locale locale) {
        boolean ar = "ar".equals(locale.getLanguage());
        return List.of(
            MenuItem.builder("dashboard.home", ar ? "لوحة التحكم" : "Dashboard")
                .icon("pi pi-home")
                .outcome("/views/index")
                .order(1)
                // بدون permission — متاحة لجميع المستخدمين المسجلين
                .build()
        );
    }

    @Override
    public List<Class<?>> getEntityClasses() {
        return List.of(); // لا كيانات خاصة — يقرأ فقط من جداول الوحدات الأخرى
    }

    @Override
    public List<String> getPermissions() {
        return List.of(); // لا صلاحيات خاصة — كل مستخدم مسجل يرى لوحته
    }

    @Override
    public List<String> getRequiredModules() {
        // مهم: قائمة فارغة — Dashboard لا يتطلب أي وحدة أخرى
        // (soft-dependency فقط عبر try/catch في DashboardBean)
        return List.of();
    }

    @Override
    public List<ReportDefinition> getReports() {
        return List.of(); // لا تقارير قابلة للتصدير من لوحة التحكم نفسها
    }
}
