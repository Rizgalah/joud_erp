package com.joud.core.service;

import com.joud.core.db.JoudRepository;
import jakarta.inject.Inject;
import java.util.List;

public abstract class BaseCrudService<T, ID> {

    @Inject
    protected JoudRepository db; // 👈 حقن المستودع المركزي الجديد

    private final Class<T> entityClass;

    protected BaseCrudService(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    public List<T> findAll() {
        return db.execute(em -> em.createQuery("SELECT e FROM " + entityClass.getSimpleName() + " e", entityClass)
                .getResultList());
    }

    public T findById(ID id) {
        return db.execute(em -> em.find(entityClass, id));
    }

    public T save(T entity) {
        return db.executeInTransaction(em -> {
            em.persist(entity);
            return entity; // سيرجع الكائن ومعه الـ ID الجديد الذي ولّده JoudIdGenerator تلقائياً!
        });
    }

    public T update(T entity) {
        return db.executeInTransaction(em -> {
            return em.merge(entity);
        });
    }

    public void delete(ID id) {
        db.executeInTransaction(em -> {
            T entity = em.find(entityClass, id);
            if (entity != null) {
                em.remove(entity);
            }
        });
    }
}