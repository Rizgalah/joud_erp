package com.joud.core.audit;

import com.joud.core.event.EntityChangeEvent;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import java.util.logging.Logger;

/**
 * AuditListener - المراقب العام الشامل لجميع تغييرات قاعدة البيانات
 */
public class AuditListener {
    private static final Logger LOG = Logger.getLogger(AuditListener.class.getName());

    @PostPersist
    public void onPostPersist(Object entity) {
        LOG.info("Entity created: " + entity.getClass().getSimpleName());

        // 🚀 الفكرة الذكية: تحويل الحفظ التلقائي في JPA إلى حدث عام في CDI!
        publishEvent(entity, EntityChangeEvent.ActionType.CREATED);
    }

    @PostUpdate
    public void onPostUpdate(Object entity) {
        LOG.info("Entity updated: " + entity.getClass().getSimpleName());
        publishEvent(entity, EntityChangeEvent.ActionType.UPDATED);
    }

    @PostRemove
    public void onPostRemove(Object entity) {
        LOG.info("Entity deleted: " + entity.getClass().getSimpleName());
        publishEvent(entity, EntityChangeEvent.ActionType.DELETED);
    }

    /**
     * إرسال الحدث إلى CDI Event Bus ليلتقطه أي موديول في الخلفية (مثل البريد أو
     * الاستثمار)
     */
    private void publishEvent(Object entity, EntityChangeEvent.ActionType actionType) {
        try {
            // الوصول إلى شبكة CDI والأحداث غير المتزامنة
            CDI.current().getBeanManager().getEvent()
                    .select(EntityChangeEvent.class)
                    .fireAsync(new EntityChangeEvent(entity, actionType));
        } catch (Exception e) {
            LOG.warning("Could not fire CDI event for entity: " + e.getMessage());
        }
    }
}
