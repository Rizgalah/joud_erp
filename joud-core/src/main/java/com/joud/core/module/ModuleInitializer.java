package com.joud.core.module;

//import com.joud.core.db.JoudRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class ModuleInitializer {
    private static final Logger LOGGER = Logger.getLogger(ModuleInitializer.class.getName());
    // @Inject
    // private JoudRepository repository;

    @Inject
    private ModuleInstallationService installationService;

    /**
     * قائمة الموديولات المتاحة في النظام (التي يجب أن تكون موجودة في الجداول)
     * يمكنك التعديل عليها لاحقاً بسهولة
     */
    /*
     * private static final Map<String, String> AVAILABLE_MODULES = Map.of(
     * "accounting", "نظام الحسابات العامة",
     * "sales", "نظام المبيعات",
     * "purchases", "نظام المشتريات",
     * "inventory", "نظام المخزون",
     * "hr", "نظام الموارد البشرية",
     * "crm", "إدارة علاقات العملاء",
     * "project_management", "إدارة المشاريع",
     * "wms", "نظام إدارة المستودعات",
     * "manufacturing", "نظام التصنيع",
     * "ecommerce", "منصة التجارة الإلكترونية",
     * "pos", "نقاط البيع",
     * "production", "نظام الإنتاج");
     */

    public void init(@Observes @Initialized(ApplicationScoped.class) Object init) {
        LOGGER.info("⏳ جاري استكشاف وتدقيق الموديولات المثبتة في joudERP...");

        // جلب كافة الموديولات المسجلة عبر SPI تلقائياً
        ServiceLoader<JoudModule> modules = ServiceLoader.load(JoudModule.class);

        for (JoudModule module : modules) {
            try {
                // استدعاء خدمة التثبيت في Transaction منفصل لكل موديول
                installationService.installIfNew(module);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "❌ فشل فحص أو تثبيت الموديول: " + module.getModuleId(), e);
            }
        }

        LOGGER.info("✅ اكتملت عملية تهيئة وتدقيق كافة الموديولات بنجاح.");
    }

    /*
     * public void onStartup(@Observes StartupEvent ev) {
     * System.out.println("[JoudERP] Starting module initialization...");
     * initializeModules();
     * }
     * 
     * private void initializeModules() {
     * repository.executeInTransaction(em -> {
     * int created = 0;
     * int enabled = 0;
     * 
     * for (Map.Entry<String, String> entry : AVAILABLE_MODULES.entrySet()) {
     * String moduleId = entry.getKey();
     * String moduleName = entry.getValue();
     * 
     * // التحقق إذا كان الموديول موجوداً بالفعل
     * Long count = em.createQuery(
     * "SELECT COUNT(m) FROM SystemModule m WHERE m.moduleId = :moduleId",
     * Long.class)
     * .setParameter("moduleId", moduleId)
     * .getSingleResult();
     * 
     * if (count == 0) {
     * // إنشاء موديول جديد (افتراضي معطل)
     * SystemModule newModule = new SystemModule(moduleId, moduleName, false);
     * em.persist(newModule);
     * created++;
     * System.out.println("[JoudERP] Created module: " + moduleId);
     * } else {
     * // الموديول موجود، نتحقق من تفعيله
     * List<SystemModule> existingModules = em.createQuery(
     * "SELECT m FROM SystemModule m WHERE m.moduleId = :moduleId",
     * SystemModule.class)
     * .setParameter("moduleId", moduleId)
     * .setMaxResults(1)
     * .getResultList();
     * 
     * if (!existingModules.isEmpty()) {
     * SystemModule module = existingModules.get(0);
     * if (!module.getActive()) {
     * // تفعيل الموديول تلقائياً
     * module.setActive(true);
     * em.merge(module);
     * enabled++;
     * System.out.println("[JoudERP] Enabled module: " + moduleId);
     * }
     * }
     * }
     * }
     * 
     * System.out.println("[JoudERP] Initialization complete: " + created +
     * " new modules created, " + enabled
     * + " modules enabled.");
     * });
     * }
     * 
     * /**
     * الحصول على قائمة بجميع الموديولات مع حالة التفعيل
     */
    /*
     * public List<SystemModule> getAllModules() {
     * return repository
     * .execute(em ->
     * em.createQuery("SELECT m FROM SystemModule m ORDER BY m.moduleId",
     * SystemModule.class)
     * .getResultList());
     * }
     * 
     * /**
     * تفعيل أو تعطيل موديول معين
     */
    /*
     * public void toggleModule(String moduleId, boolean active) {
     * repository.executeInTransaction(em -> {
     * List<SystemModule> modules = em.createQuery(
     * "SELECT m FROM SystemModule m WHERE m.moduleId = :moduleId",
     * SystemModule.class)
     * .setParameter("moduleId", moduleId)
     * .setMaxResults(1)
     * .getResultList();
     * 
     * if (!modules.isEmpty()) {
     * SystemModule module = modules.get(0);
     * module.setActive(active);
     * em.merge(module);
     * System.out.println("[JoudERP] " + (active ? "Enabled" : "Disabled") +
     * " module: " + moduleId);
     * } else {
     * throw new RuntimeException("Module not found: " + moduleId);
     * }
     * });
     * }
     * 
     * /**
     * التحقق إذا كان موديول معين مفعّل
     */
    /*
     * public boolean isModuleActive(String moduleId) {
     * return repository.execute(em -> {
     * List<SystemModule> modules = em.createQuery(
     * "SELECT m FROM SystemModule m WHERE m.moduleId = :moduleId",
     * SystemModule.class)
     * .setParameter("moduleId", moduleId)
     * .setMaxResults(1)
     * .getResultList();
     * 
     * return !modules.isEmpty() && modules.get(0).getActive();
     * });
     * }
     * 
     * /**
     * الحصول على الموديولات المفعّلة فقط
     */
    /*
     * public List<SystemModule> getActiveModules() {
     * return repository.execute(em -> em.createQuery(
     * "SELECT m FROM SystemModule m WHERE m.active = true ORDER BY m.moduleId",
     * SystemModule.class)
     * .getResultList());
     * }
     * 
     * /**
     * الحصول على الموديولات المعطلة
     */
    /*
     * public List<SystemModule> getInactiveModules() {
     * return repository.execute(em -> em.createQuery(
     * "SELECT m FROM SystemModule m WHERE m.active = false ORDER BY m.moduleId",
     * SystemModule.class)
     * .getResultList());
     * }
     */

    /**
     * إعداد الموديولات المتاحة (للاستخدام الداخلي)
     */
    /*
     * protected Map<String, String> getAvailableModules() {
     * return AVAILABLE_MODULES;
     * }
     */

    /**
     * التحقق إذا كان الموديول موجوداً في قائمة الموديولات المتاحة
     */
    /*
     * protected boolean isModuleAvailable(String moduleId) {
     * return AVAILABLE_MODULES.containsKey(moduleId);
     * }
     */
}
