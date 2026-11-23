package com.joud.security.config;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.util.logging.Logger;

@WebListener // وسم لجافا سيرفلت ليعرف أن هذا كلاس تهيئة
public class JoudAppAppInitializer implements ServletContextListener {

    private static final Logger logger = Logger.getLogger(JoudAppAppInitializer.class.getName());

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        // هذا الكود يتم تشغيله تلقائياً عند بدء تشغيل السيرفر (مثل Tomcat)
        logger.info("🚀 Joud ERP System Starting Up... ");

        // --- هنا يتم تفعيل نظام تسجيل الأخطاء المخصص ---
        JoudLogConfig.initializeLogger();// تشغيل التهيئة فوراً عند إقلاع النظام
        // ----------------------------------------------------

        /*
         * logger.info("🚀 Joud ERP System Starting Up... ");
         * 
         * // 1. إعداد ملفات الأمان (Auth) - السيرفر هو من يقرر من يدخل
         * JoudLogConfig.setupAuthLogger();
         * 
         * // 2. إعداد ملفات العمليات (Server) - سجل لكل @Path
         * JoudLogConfig.setupServerLogger();
         * 
         * // 3. إعداد ملفات الجلسات (Session) - مراقبة الـ Tokens
         * JoudLogConfig.setupSessionLogger();
         */

        logger.info("✅ Logging system initialized and ready.");
        logger.info("✅ System is ready to accept secure connections.");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        logger.info("🛑 Joud ERP System Shutting Down...");
    }
}
