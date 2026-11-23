package com.joud.accounting.entity;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * يتحقق أن Hibernate يستطيع بناء mappings كل كيانات المحاسبة (العلاقات،
 * mappedBy، الأعمدة...) دون الاتصال بقاعدة بيانات. أي خطأ في التعيين كان
 * سيظهر وقت إقلاع التطبيق على Tomcat؛ هنا يظهر عند البناء.
 */
class EntityMappingTest {

    private static final Class<?>[] ENTITIES = {
            AgentMast.class,
            AccountingPeriod.class,
            DayLedger.class,
            ExchangeRateHistory.class,
            GeneralLedgerMaster.class,
            JournalEntry.class,
            SubldgMast.class
    };

    @Test
    @DisplayName("mappings كيانات المحاسبة سليمة (بما فيها JournalEntry.lines)")
    void allEntityMappingsAreValid() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .build();
        try {
            MetadataSources sources = new MetadataSources(registry);
            for (Class<?> entity : ENTITIES) {
                sources.addAnnotatedClass(entity);
            }

            Metadata metadata = sources.buildMetadata();

            for (Class<?> entity : ENTITIES) {
                assertNotNull(metadata.getEntityBinding(entity.getName()),
                        "Entity mapping missing: " + entity.getSimpleName());
            }
            assertNotNull(metadata.getCollectionBinding(JournalEntry.class.getName() + ".lines"),
                    "JournalEntry.lines collection mapping missing");
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
