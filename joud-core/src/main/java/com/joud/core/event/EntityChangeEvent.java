package com.joud.core.event;

import java.time.LocalDateTime;
import java.util.logging.Logger;

/**
 * EntityChangeEvent - كائن الحدث العام لتغييرات قاعدة البيانات.
 * يُستخدم لنقل تفاصيل العملية (إضافة/تعديل/حذف) والكائن المتأثر إلى الموديولات
 * الأخرى بشكل غير متزامن.
 */
public class EntityChangeEvent {
    @SuppressWarnings("unused")
    private static final Logger LOG = Logger.getLogger(EntityChangeEvent.class.getName());

    /**
     * أنواع العمليات التي جرت على قاعدة البيانات
     */
    public enum ActionType {
        CREATED,
        UPDATED,
        DELETED
    }

    private final Object entity;
    private final ActionType actionType;
    private final LocalDateTime timestamp;

    public EntityChangeEvent(Object entity, ActionType actionType) {
        this.entity = entity;
        this.actionType = actionType;
        this.timestamp = LocalDateTime.now();
    }

    // --- Getters ---

    public Object getEntity() {
        return entity;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    /**
     * دالة مساعدة لمعرفة اسم الكائن المتأثر بسهولة
     */
    public String getEntityName() {
        return entity != null ? entity.getClass().getSimpleName() : "Unknown";
    }

    @Override
    public String toString() {
        return "EntityChangeEvent{" +
                "entity=" + getEntityName() +
                ", actionType=" + actionType +
                ", timestamp=" + timestamp +
                '}';
    }
}
