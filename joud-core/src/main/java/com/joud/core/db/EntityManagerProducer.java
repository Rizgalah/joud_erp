package com.joud.core.db;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class EntityManagerProducer {

    private static final Logger logger = Logger.getLogger(EntityManagerProducer.class.getName());

    // ⚠️ تنبيه: تأكد من أن هذا الاسم يطابق تماماً الـ name الموجود في ملف
    // persistence.xml لديك
    private static final String PERSISTENCE_UNIT_NAME = "JoudERP";

    private final EntityManagerFactory emf;

    public EntityManagerProducer() {
        logger.info("Initializing EntityManagerFactory for " + PERSISTENCE_UNIT_NAME);
        this.emf = buildFactory();
    }

    /**
     * يبني الـ factory من persistence.xml مع دمج كيانات الوحدات المثبتة فعلاً.
     * إن فشل هذا المسار لأي سبب يُسجَّل الخطأ ويُستخدم المسار القياسي القديم.
     */
    private EntityManagerFactory buildFactory() {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader == null) {
                classLoader = EntityManagerProducer.class.getClassLoader();
            }
            return ModulePersistenceBootstrap.createFactory(PERSISTENCE_UNIT_NAME, classLoader);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Module-aware persistence bootstrap failed; "
                    + "falling back to the standard persistence.xml bootstrap.", e);
            return Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
        }
    }

    @Produces
    @RequestScoped
    public EntityManager createEntityManager() {
        logger.fine("Creating a new EntityManager for the request.");
        return emf.createEntityManager();
    }

    public void closeEntityManager(@Disposes EntityManager em) {
        if (em != null && em.isOpen()) {
            logger.fine("Closing EntityManager context.");
            em.close();
        }
    }

    /** إغلاق الـ factory عند إيقاف التطبيق (يحرّر اتصالات القاعدة عند إعادة النشر على Tomcat). */
    @PreDestroy
    void closeFactory() {
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }
}
