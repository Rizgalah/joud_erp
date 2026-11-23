package com.joud.core.db;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
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
        this.emf = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
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
}