package com.joud.core.db;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseIntegrationTest {

    private static EntityManagerFactory emf;
    private static EntityManager em;

    @BeforeAll
    static void init() {
        try {
            // يتم استدعاء وحدة التسمية المحددة في persistence.xml (مثال: JoudERP)
            emf = Persistence.createEntityManagerFactory("JoudERP");
            em = emf.createEntityManager();
        } catch (Exception e) {
            fail("فشل إنشاء EntityManagerFactory! تحقق من إعدادات الاتصال في persistence.xml: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("اختبار فحص الاتصال الفعلي وتنفيذ استعلام بسيط لمعرفة أين يقف تسلسل العمل")
    void testDatabaseConnectionAndPing() {
        assertNotNull(em, "الـ EntityManager قيمته Null، هناك مشكلة في الـ Producer أو الإعدادات");

        try {
            em.getTransaction().begin();

            // تنفيذ استعلام بسيط للتأكد من استجابة قاعدة البيانات (Ping)
            // إذا كنت تستخدم PostgreSQL أو Oracle يمكنك تعديل الاستعلام ليناسبها
            Object result = em.createNativeQuery("SELECT 1").getSingleResult();

            em.getTransaction().commit();

            assertNotNull(result, "لم يتم إرجاع أي نتيجة من الاستعلام البيسط لقاعدة البيانات");
            System.out.println(">>> تم الاتصال وقراءة البيانات بنجاح، تسلسل العمل البرمجي سليم هنا.");

        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            e.printStackTrace(); // هذا سيطبع لك الـ Stack Trace كاملًا لتعرف من أي سطر ينتج الخطأ
            fail("فشل تسلسل العمل أثناء تنفيذ العمليات على قاعدة البيانات: " + e.getMessage());
        }
    }

    @AfterAll
    static void tearDown() {
        if (em != null && em.isOpen())
            em.close();
        if (emf != null && emf.isOpen())
            emf.close();
    }
}