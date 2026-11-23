package com.joud.core.module;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import com.joud.core.db.JoudRepository;
import java.util.Locale;
import java.util.logging.Logger;

@ApplicationScoped
public class ModuleInstallationService {

    private static final Logger LOGGER = Logger.getLogger(ModuleInstallationService.class.getName());

    @Inject
    private JoudRepository db;

    /**
     * يتحقق من قاعدة البيانات؛ إذا كان الموديول جديداً، ينفذ ميثود onInstall
     * ويسجله.
     */
    public void installIfNew(JoudModule module) {
        String moduleId = module.getModuleId();

        // 1. التحقق من وجود الموديول في قاعدة البيانات
        Long count = db.fetchSingle(
                "SELECT COUNT(m) FROM JoudSystemModule m WHERE m.moduleId = ?1", Long.class, moduleId);

        if (count == 0) {
            LOGGER.info("🚀 تم اكتشاف موديول جديد لم يتم تثبيته بعد: [" + moduleId + "]");

            // 2. استدعاء الميثود التأسيسية
            module.onInstall(db);

            // 3. تسجيل الموديول في قاعدة البيانات لضمان عدم تشغيل الـ install مجدداً
            registerModuleInDb(module);

            LOGGER.info("🎉 تم تثبيت وتهيئة موديول [" + moduleId + "] بنجاح.");
        } else {
            LOGGER.fine("ℹ️ موديول [" + moduleId + "] مثبت مسبقاً ونشط.");
        }
    }

    private void registerModuleInDb(JoudModule module) {
        db.executeUpdate(
                "INSERT INTO JOUD_SYSTEM_MODULES (MODULE_ID, MODULE_NAME, VERSION, STATUS) VALUES (?, ?, ?, 'ACTIVE')",
                module.getModuleId(),
                module.getModuleName(Locale.getDefault()),
                module.getVersion()
        );
    }
}