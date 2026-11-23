package com.joud.core.id;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * JoudIdGenerator — مولد المعرفات المركزي لـ joudERP.
 * يكتشف اسم الجدول ديناميكياً ويقوم بتوليد وزيادة المعرف في جدول موحد
 * لضمان التوافقية الكاملة مع أوراكل، بوستجرس، أو أي قاعدة بيانات أخرى.
 */
public class JoudIdGenerator implements IdentifierGenerator {

    @Override
    public Serializable generate(SharedSessionContractImplementor session, Object object) {
        // 1. استخراج اسم الجدول ديناميكياً من الكائن
        String tableName = getTableName(object);

        // 2. تنفيذ استعلام آمن ومغلق لمنع التضارب (Row-Level Locking)
        return session.doReturningWork(connection -> {
            long nextId = 1;

            // استعلامات SQL قياسية (Standard ANSI SQL) تعمل على أوراكل وبوستجرس دون أي
            // تعديل
            String selectSql = "SELECT NEXT_VAL FROM JOUD_SEQUENCE_REGISTRY WHERE SEQ_NAME = ? FOR UPDATE";
            String updateSql = "UPDATE JOUD_SEQUENCE_REGISTRY SET NEXT_VAL = ? WHERE SEQ_NAME = ?";
            String insertSql = "INSERT INTO JOUD_SEQUENCE_REGISTRY (SEQ_NAME, NEXT_VAL) VALUES (?, ?)";

            try (PreparedStatement selectStmt = connection.prepareStatement(selectSql)) {
                selectStmt.setString(1, tableName);

                try (ResultSet rs = selectStmt.executeQuery()) {
                    if (rs.next()) {
                        // إذا كان الجدول مسجلاً مسبقاً، نأخذ القيمة ونزيدها
                        nextId = rs.getLong("NEXT_VAL") + 1;

                        try (PreparedStatement updateStmt = connection.prepareStatement(updateSql)) {
                            updateStmt.setLong(1, nextId);
                            updateStmt.setString(2, tableName);
                            updateStmt.executeUpdate();
                        }
                    } else {
                        // أول عملية حفظ لهذا الجدول في النظام
                        nextId = 1;
                        try (PreparedStatement insertStmt = connection.prepareStatement(insertSql)) {
                            insertStmt.setString(1, tableName);
                            insertStmt.setLong(2, nextId);
                            insertStmt.executeUpdate();
                        }
                    }
                }
            }
            return nextId;
        });
    }

    /**
     * استخراج اسم الجدول من وسم @Table، وفي حال عدم وجوده نعتمد اسم الكلاس.
     */
    private String getTableName(Object object) {
        Table tableAnnotation = object.getClass().getAnnotation(Table.class);
        if (tableAnnotation != null && !tableAnnotation.name().isBlank()) {
            return tableAnnotation.name().toUpperCase();
        }
        return object.getClass().getSimpleName().toUpperCase();
    }
}