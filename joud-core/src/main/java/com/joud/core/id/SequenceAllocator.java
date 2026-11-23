package com.joud.core.id;

import jakarta.persistence.EntityManager;

import java.util.List;

/**
 * يولّد القيمة التالية من JOUD_SEQUENCE_REGISTRY داخل المعاملة الحالية.
 *
 * - اسم التسلسل (seqName) بنفس قاعدة JoudIdGenerator: اسم الجدول بحروف كبيرة.
 * - يأخذ الأكبر بين قيمة السجل وأكبر قيمة موجودة فعلاً (floor)، كي لا
 *   يتصادم مع صفوف أُدخلت يدوياً أو بطريقة أخرى.
 * - صف التسلسل يُقفل (FOR UPDATE) حتى نهاية المعاملة، فلا يحصل مستخدمان
 *   على القيمة نفسها.
 *
 * يجب استدعاؤه داخل معاملة (db.executeInTransaction). أسماء الجدول والعمود
 * يجب أن تكون ثوابت داخلية وليست مدخلات مستخدم.
 */
public final class SequenceAllocator {

    private SequenceAllocator() {
    }

    /** المعرّف التالي لجدول: الأكبر بين السجل وMAX(idColumn) + 1. */
    public static long nextId(EntityManager em, String seqName, String table, String idColumn) {
        long maxId = ((Number) em.createNativeQuery(
                "SELECT COALESCE(MAX(" + idColumn + "), 0) FROM " + table).getSingleResult()).longValue();
        return nextValue(em, seqName, maxId);
    }

    /**
     * القيمة التالية لتسلسل مسمّى (مثل ترقيم المراجع). floor أكبر قيمة مستخدمة
     * حالياً خارج السجل؛ تُعاد قيمة أكبر منها دائماً.
     */
    public static long nextValue(EntityManager em, String seqName, long floor) {
        List<?> rows = em.createNativeQuery(
                "SELECT NEXT_VAL FROM JOUD_SEQUENCE_REGISTRY WHERE SEQ_NAME = ?1 FOR UPDATE")
                .setParameter(1, seqName)
                .getResultList();

        if (rows.isEmpty()) {
            long next = floor + 1;
            em.createNativeQuery("INSERT INTO JOUD_SEQUENCE_REGISTRY (SEQ_NAME, NEXT_VAL) VALUES (?1, ?2)")
                    .setParameter(1, seqName)
                    .setParameter(2, next)
                    .executeUpdate();
            return next;
        }

        long next = Math.max(((Number) rows.get(0)).longValue(), floor) + 1;
        em.createNativeQuery("UPDATE JOUD_SEQUENCE_REGISTRY SET NEXT_VAL = ?1 WHERE SEQ_NAME = ?2")
                .setParameter(1, next)
                .setParameter(2, seqName)
                .executeUpdate();
        return next;
    }
}
