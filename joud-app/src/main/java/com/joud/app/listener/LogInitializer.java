package com.joud.app.listener;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import org.slf4j.bridge.SLF4JBridgeHandler;

@WebListener
public class LogInitializer implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        // 🔥 إزالة المعالجات الافتراضية لتومكات وتوجيهها إلى Logback
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        SLF4JBridgeHandler.uninstall();
    }
}