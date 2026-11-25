package com.joud.notification;

import com.joud.core.module.JoudModule;
import com.joud.notification.entity.SentEmailLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationModuleTest {

    private final NotificationModule module = new NotificationModule();

    @Test
    @DisplayName("الوحدة تعلن كيانها وبلا قوائم في الواجهة")
    void declaresEntityAndNoMenu() {
        assertEquals("notification", module.getModuleId());
        assertEquals(List.of(SentEmailLog.class), module.getEntityClasses());
        assertTrue(module.getMenuItems(Locale.ENGLISH).isEmpty());
    }

    @Test
    @DisplayName("تُكتشف عبر SPI (ملف META-INF/services موجود وصحيح)")
    void isDiscoverableViaServiceLoader() {
        boolean found = StreamSupport.stream(ServiceLoader.load(JoudModule.class).spliterator(), false)
                .anyMatch(m -> m instanceof NotificationModule);

        assertTrue(found, "NotificationModule must be registered in META-INF/services");
    }
}
