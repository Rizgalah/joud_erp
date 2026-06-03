package com.joud.core.module;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * PluginRegistry — ماكنة اكتشاف وإدارة الوحدات
 *
 * يعمل تلقائياً عند تشغيل التطبيق:
 *   1. يكتشف جميع الوحدات عبر Java ServiceLoader (SPI)
 *   2. يتحقق من الاعتماديات بين الوحدات
 *   3. يرتبها ويشغلها بالتسلسل الصحيح
 *   4. يوفر للـ Header/Navbar قائمة العناصر المدمجة
 */
@ApplicationScoped
@Named("pluginRegistry")
public class PluginRegistry {

    private static final Logger LOG = Logger.getLogger(PluginRegistry.class.getName());

    @PersistenceContext
    private EntityManager em;

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

        // ترتيب حسب getMenuOrder()
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
     * يتحقق أن جميع الاعتماديات المطلوبة مثبتة
     */
    private boolean canActivate(JoudModule module) {
        if (!module.isAvailable()) return false;
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
    public List<com.joud.core.menu.MenuItem> getAllMenuItems(Locale locale) {
        return modules.values().stream()
                .flatMap(m -> m.getMenuItems(locale).stream())
                .collect(Collectors.toList());
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
