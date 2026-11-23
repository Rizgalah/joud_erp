package com.joud.core.db;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.List;
import jakarta.persistence.Query;

@ApplicationScoped
public class JoudRepository {

    @Inject
    private EntityManagerProducer producer; // 👈 استخدام البروديوسر الخاص بك لإنشاء الـ Factory والـ EM

    /**
     * تنفيذ عملية استعلام آمنة وإرجاع نتيجة (تلقائية الفتح والإغلاق)
     */
    public <R> R execute(Function<EntityManager, R> action) {
        EntityManager em = producer.createEntityManager();
        try {
            return action.apply(em);
        } finally {
            producer.closeEntityManager(em);
        }
    }

    /**
     * تنفيذ عملية حفظ أو تعديل داخل Transaction مؤمنة تلقائياً مع إرجاع قيمة
     */
    public <R> R executeInTransaction(Function<EntityManager, R> action) {
        EntityManager em = producer.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            R result = action.apply(em);
            tx.commit();
            return result;
        } catch (Exception e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        } finally {
            producer.closeEntityManager(em);
        }
    }

    /**
     * تنفيذ عملية حفظ أو تعديل داخل Transaction مؤمنة تلقائياً بدون إرجاع قيمة
     */
    public void executeInTransaction(Consumer<EntityManager> action) {
        executeInTransaction(em -> {
            action.accept(em);
            return null;
        });
    }

    // ─── Facade Methods to hide EntityManager ────────────────────

    public void persist(Object entity) {
        executeInTransaction((Consumer<EntityManager>) em -> em.persist(entity));
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> fetchAll(String jpql, Class<T> clazz, Object... params) {
        return execute(em -> {
            Query query = em.createQuery(jpql, clazz);
            for (int i = 0; i < params.length; i++) {
                query.setParameter(i + 1, params[i]);
            }
            return query.getResultList();
        });
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> fetchTop(String jpql, Class<T> clazz, int limit, Object... params) {
        return execute(em -> {
            Query query = em.createQuery(jpql, clazz).setMaxResults(limit);
            for (int i = 0; i < params.length; i++) {
                query.setParameter(i + 1, params[i]);
            }
            return query.getResultList();
        });
    }

    public <T> T fetchSingle(String jpql, Class<T> clazz, Object... params) {
        return execute(em -> {
            Query query = em.createQuery(jpql, clazz).setMaxResults(1);
            for (int i = 0; i < params.length; i++) {
                query.setParameter(i + 1, params[i]);
            }
            @SuppressWarnings("unchecked")
            List<T> results = query.getResultList();
            return results.isEmpty() ? null : results.get(0);
        });
    }

    public int executeUpdate(String nativeSql, Object... params) {
        return executeInTransaction(em -> {
            Query query = em.createNativeQuery(nativeSql);
            for (int i = 0; i < params.length; i++) {
                query.setParameter(i + 1, params[i]);
            }
            return query.executeUpdate();
        });
    }
}