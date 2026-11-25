package com.joud.core.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceXmlReaderTest {

    private static final String SAMPLE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <persistence xmlns="https://jakarta.ee/xml/ns/persistence" version="3.1">
              <persistence-unit name="JoudERP" transaction-type="RESOURCE_LOCAL">
                <provider>org.hibernate.jpa.HibernatePersistenceProvider</provider>
                <non-jta-data-source>java:comp/env/jdbc/JoudDS</non-jta-data-source>
                <class>com.joud.core.A</class>
                <class>com.joud.accounting.B</class>
                <class>com.joud.core.A</class>
                <exclude-unlisted-classes>true</exclude-unlisted-classes>
                <properties>
                  <!-- <property name="jakarta.persistence.jdbc.url" value="commented"/> -->
                  <property name="hibernate.dialect" value="org.hibernate.dialect.PostgreSQLDialect"/>
                  <property name="hibernate.hbm2ddl.auto" value="none"/>
                </properties>
              </persistence-unit>
              <persistence-unit name="Other"><class>x.Y</class></persistence-unit>
            </persistence>
            """;

    private static InputStream xml(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("قراءة الإعدادات: المزوّد، DataSource، الكيانات بلا تكرار، الخصائص (المعلّقة تُتجاهل)")
    void readsUnitSettings() {
        PersistenceUnitDefinition unit = PersistenceXmlReader.read(xml(SAMPLE), "JoudERP").orElseThrow();

        assertEquals("RESOURCE_LOCAL", unit.transactionType());
        assertEquals("org.hibernate.jpa.HibernatePersistenceProvider", unit.providerClass());
        assertEquals("java:comp/env/jdbc/JoudDS", unit.nonJtaDataSource());
        assertEquals(List.of("com.joud.core.A", "com.joud.accounting.B"), unit.classNames());
        assertTrue(unit.excludeUnlistedClasses());
        assertEquals(2, unit.properties().size());
        assertEquals("none", unit.properties().get("hibernate.hbm2ddl.auto"));
    }

    @Test
    @DisplayName("اختيار الوحدة بالاسم، والاسم المجهول يعطي empty")
    void selectsUnitByName() {
        assertTrue(PersistenceXmlReader.read(xml(SAMPLE), "Nope").isEmpty());
        assertEquals(List.of("x.Y"), PersistenceXmlReader.read(xml(SAMPLE), "Other").orElseThrow().classNames());
    }

    @Test
    @DisplayName("ملف بلا namespace، والقيم الافتراضية")
    void defaultsAndNoNamespace() {
        PersistenceUnitDefinition empty = PersistenceXmlReader
                .read(xml("<persistence><persistence-unit name=\"U\"/></persistence>"), "U").orElseThrow();
        assertEquals("RESOURCE_LOCAL", empty.transactionType());
        assertNull(empty.nonJtaDataSource());
        assertFalse(empty.excludeUnlistedClasses());

        PersistenceUnitDefinition excluded = PersistenceXmlReader.read(xml(
                "<persistence><persistence-unit name=\"U\"><exclude-unlisted-classes/></persistence-unit></persistence>"),
                "U").orElseThrow();
        assertTrue(excluded.excludeUnlistedClasses());
    }

    @Test
    @DisplayName("XML تالف أو يحوي DOCTYPE (XXE) يُرفض")
    void rejectsInvalidOrDangerousXml() {
        assertThrows(IllegalArgumentException.class,
                () -> PersistenceXmlReader.read(xml("<persistence><oops>"), "U"));
        assertThrows(IllegalArgumentException.class, () -> PersistenceXmlReader.read(xml(
                "<!DOCTYPE persistence [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
                        + "<persistence><persistence-unit name=\"U\">&x;</persistence-unit></persistence>"),
                "U"));
    }

    @Test
    @DisplayName("القراءة من الـ classpath تجد وحدة الاختبار JoudERP وتحدد جذرها")
    void readsFromClasspath() throws Exception {
        Optional<PersistenceUnitDefinition> unit = PersistenceXmlReader
                .readFromClasspath(Thread.currentThread().getContextClassLoader(), "JoudERP");

        assertTrue(unit.isPresent());
        assertEquals("JoudERP", unit.get().name());
        assertTrue(unit.get().rootUrl() != null);
    }
}
