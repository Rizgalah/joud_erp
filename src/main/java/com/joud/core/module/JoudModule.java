package com.joud.core.module;

import com.joud.core.menu.MenuItem;
import com.joud.core.report.ReportDefinition;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Locale;

/**
 * JoudModule — واجهة القابس الرئيسية
 *
 * كل وحدة (HR, Trade, Accounting...) يجب أن تطبق هذه الواجهة.
 * القلب يكتشفها تلقائياً عبر Java SPI عند التشغيل.
 *
 * طريقة التسجيل:
 *   في ملف: META-INF/services/com.joud.core.module.JoudModule
 *   اكتب: com.joud.hr.HrModule
 */
public interface JoudModule {

    // ─────────────────────────────────────────
    // 1. هوية الوحدة
    // ─────────────────────────────────────────

    /** المعرف الفريد للوحدة — لا يتغير أبداً (e.g. "hr", "trade", "accounting") */
    String getModuleId();

    /** الاسم الظاهر للمستخدم حسب اللغة */
    String getModuleName(Locale locale);

    /** رقم إصدار الوحدة (e.g. "1.0.0") */
    String getVersion();

    /** وصف مختصر للوحدة */
    default String getDescription(Locale locale) { return ""; }

    // ─────────────────────────────────────────
    // 2. القائمة الجانبية (Navbar)
    // ─────────────────────────────────────────

    /**
     * قائمة العناصر التي ستظهر في الـ Navbar.
     * يبني Header/Navbar القائمة الكاملة من جميع الوحدات المثبتة.
     * الترتيب يُحدد بـ getMenuOrder().
     */
    List<MenuItem> getMenuItems(Locale locale);

    /** ترتيب الوحدة في القائمة (رقم أصغر = أعلى) */
    default int getMenuOrder() { return 100; }

    // ─────────────────────────────────────────
    // 3. جداول قاعدة البيانات
    // ─────────────────────────────────────────

    /**
     * قائمة JPA Entity classes التابعة لهذه الوحدة.
     * القلب يضيفها للـ persistence.xml تلقائياً عند التثبيت.
     * مثال: List.of(Employee.class, Payroll.class)
     */
    List<Class<?>> getEntityClasses();

    // ─────────────────────────────────────────
    // 4. دورة حياة الوحدة (Lifecycle)
    // ─────────────────────────────────────────

    /**
     * يُستدعى مرة واحدة عند تثبيت الوحدة لأول مرة.
     * استخدمه لإنشاء البيانات الأساسية (seed data).
     * مثال: إنشاء الأدوار الافتراضية، أو بيانات إعداد أولية.
     */
    default void onInstall(EntityManager em) {}

    /**
     * يُستدعى عند تحديث الوحدة من إصدار سابق.
     * استخدمه للـ migration إذا تغير الـ schema.
     */
    default void onUpgrade(EntityManager em, String fromVersion) {}

    /**
     * يُستدعى عند تشغيل التطبيق لكل وحدة مثبتة.
     * استخدمه لتهيئة الـ caches أو الـ schedulers.
     */
    default void onStartup() {}

    /**
     * يُستدعى عند إيقاف التطبيق.
     * استخدمه لتحرير الموارد.
     */
    default void onShutdown() {}

    // ─────────────────────────────────────────
    // 5. التقارير (Report Engine)
    // ─────────────────────────────────────────

    /**
     * قائمة التقارير التي تقدمها هذه الوحدة لماكنة التقارير.
     * Report Engine يجمعها من جميع الوحدات ويعرضها في شاشة التقارير.
     * مثال: تقرير الرواتب، تقرير المبيعات...
     */
    default List<ReportDefinition> getReports() { return List.of(); }

    // ─────────────────────────────────────────
    // 6. الصلاحيات (Permissions)
    // ─────────────────────────────────────────

    /**
     * قائمة أكواد الصلاحيات التي تعرفها هذه الوحدة.
     * القلب يسجلها في جدول role_permissions تلقائياً.
     * مثال: "hr.employee.view", "hr.employee.edit"
     */
    default List<String> getPermissions() { return List.of(); }

    // ─────────────────────────────────────────
    // 7. الاعتماديات بين الوحدات
    // ─────────────────────────────────────────

    /**
     * قائمة معرفات الوحدات التي تعتمد عليها هذه الوحدة.
     * Plugin Registry يتحقق منها قبل تفعيل الوحدة.
     * مثال: وحدة Payroll تعتمد على "hr"
     */
    default List<String> getRequiredModules() { return List.of(); }

    /**
     * هل الوحدة متاحة للتثبيت في البيئة الحالية؟
     * يمكن استخدامها للتحقق من الترخيص أو متطلبات البيئة.
     */
    default boolean isAvailable() { return true; }
}
