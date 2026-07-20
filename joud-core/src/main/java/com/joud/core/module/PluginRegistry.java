package com.joud.core.module;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.joud.core.db.JoudRepository;

/**
 * PluginRegistry — ماكنة اكتشاف وإدارة الوحدات
 *
 * يعمل تلقائياً عند تشغيل التطبيق:
 * 1. يكتشف جميع الوحدات عبر Java ServiceLoader (SPI)
 * 2. يتحقق من الاعتماديات بين الوحدات
 * 3. يرتبها ويشغلها بالتسلسل الصحيح
 * 4. يوفر للـ Header/Navbar قائمة العناصر المدمجة
 */
@ApplicationScoped
@Named("pluginRegistry")
public class PluginRegistry {

    private static final Logger LOG = Logger.getLogger(PluginRegistry.class.getName());

    @Inject
    private JoudRepository db; // 👈 حقن المستودع الموحد والتخلص تماماً من الـ EntityManager!

    // الوحدات المثبتة مرتبة حسب getMenuOrder()
    private final Map<String, JoudModule> modules = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        LOG.info("=== Joud Plugin Registry: scanning modules ===");

        // اكتشاف جميع الوحدات عبر Java SPI
        ServiceLoader<JoudModule> loader = ServiceLoader.load(JoudModule.class);
        List<JoudModule> discovered = new ArrayList<>();
        loader.forEach(discovered::add);

        LOG.info("Discovered " + discovered.size() + " module(s)");

        // رغبة المطور ترتيب حسب
        discovered.sort(Comparator.comparingInt(JoudModule::getMenuOrder));

        // تحقق من الاعتماديات وتفعيل
        for (JoudModule module : discovered) {
            if (canActivate(module)) {
                modules.put(module.getModuleId(), module);
                module.onStartup();
                LOG.info("  [OK] Module loaded: " + module.getModuleId()
                        + " v" + module.getVersion());
            } else {
                LOG.warning("  [SKIP] Module skipped (missing dependencies): "
                        + module.getModuleId());
            }
        }

        LOG.info("=== " + modules.size() + " module(s) active ===");
    }

    /**
     * فحص قاعدة البيانات وتثبيت الموديول إذا كان جديداً بالكامل
     */
    @SuppressWarnings("unused")
    private void autoInstallIfRequired(JoudModule module) {
        Long count = db.fetchSingle(
                "SELECT COUNT(m) FROM JoudSystemModule m WHERE m.moduleId = ?1", Long.class, module.getModuleId());

        if (count == null || count == 0) {
            LOG.info("🔧 First-time install detected for module: " + module.getModuleId());

            // تشغيل كود التأسيس المعتمد على JPA النقي داخل الموديول
            module.onInstall(db);

            // تسجيل الموديول في قاعدة البيانات لمنع إعادة التثبيت مستقبلاً
            db.executeUpdate(
                    "INSERT INTO JOUD_SYSTEM_MODULES (MODULE_ID, MODULE_NAME, VERSION, STATUS) VALUES (?, ?, ?, 'ACTIVE')",
                    module.getModuleId(),
                    module.getModuleId().toUpperCase(),
                    module.getVersion());

            LOG.info("🎯 Module " + module.getModuleId() + " has been registered successfully.");
        }
    }

    /**
     * يتحقق أن جميع الاعتماديات المطلوبة مثبتة
     */
    private boolean canActivate(JoudModule module) {
        if (!module.isAvailable())
            return false;
        for (String required : module.getRequiredModules()) {
            if (!modules.containsKey(required)) {
                LOG.warning("Module '" + module.getModuleId()
                        + "' requires '" + required + "' which is not installed.");
                return false;
            }
        }
        return true;
    }

    // ─── API للـ Header/Navbar ────────────────────

    /**
     * يعيد قائمة عناصر القائمة الكاملة من جميع الوحدات
     * مرتبة وجاهزة للعرض في الـ Navbar
     */
    /**
     * يعيد قائمة عناصر القائمة الكاملة من جميع الوحدات
     * مرتبة وجاهزة للعرض في الـ Navbar
     *
     * يُضاف هنا أيضاً عنصر ثابت "مركز التقارير" — وهو ليس وحدة
     * مسجَّلة عبر SPI بل ميزة أساسية في القلب نفسه (joud-core)،
     * تجمع وتعرض كل ReportDefinition من جميع الوحدات المثبتة فعلياً.
     */
    public List<com.joud.core.menu.MenuItem> getAllMenuItems(Locale locale) {
        List<com.joud.core.menu.MenuItem> items = modules.values().stream()
                .flatMap(m -> m.getMenuItems(locale).stream())
                .collect(Collectors.toList());

        boolean ar = "ar".equals(locale.getLanguage());
        items.add(com.joud.core.menu.MenuItem
                .builder("core.reports", ar ? "مركز التقارير" : "Report Center")
                .icon("pi pi-chart-bar")
                .outcome("/views/reports")
                .order(90) // يظهر بعد لوحة التحكم وقبل أقسام الوحدات الفنية
                .build());

        return items;
    }

    // ─── API عامة ────────────────────────────────

    /** هل الوحدة مثبتة وفعالة؟ */
    public boolean isInstalled(String moduleId) {
        return modules.containsKey(moduleId);
    }

    /** الوحدات المثبتة كلها */
    public Collection<JoudModule> getInstalledModules() {
        return Collections.unmodifiableCollection(modules.values());
    }

    /** وحدة بعينها */
    public Optional<JoudModule> getModule(String moduleId) {
        return Optional.ofNullable(modules.get(moduleId));
    }

    /** جميع التقارير من جميع الوحدات (لماكنة التقارير) */
    public List<com.joud.core.report.ReportDefinition> getAllReports() {
        return modules.values().stream()
                .flatMap(m -> m.getReports().stream())
                .collect(Collectors.toList());
    }

    /** جميع الصلاحيات من جميع الوحدات */
    public List<String> getAllPermissions() {
        return modules.values().stream()
                .flatMap(m -> m.getPermissions().stream())
                .distinct()
                .collect(Collectors.toList());
    }
}
