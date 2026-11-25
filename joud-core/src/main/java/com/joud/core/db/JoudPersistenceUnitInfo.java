package com.joud.core.db;

import jakarta.persistence.SharedCacheMode;
import jakarta.persistence.ValidationMode;
import jakarta.persistence.spi.ClassTransformer;
import jakarta.persistence.spi.PersistenceUnitInfo;
import jakarta.persistence.spi.PersistenceUnitTransactionType;

import javax.sql.DataSource;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Properties;

/**
 * وصف وحدة الاستمرارية الذي يُسلَّم لـ Hibernate: نفس إعدادات persistence.xml،
 * لكن بقائمة الكيانات النهائية (الموجودة فعلاً + المعلنة من الوحدات).
 */
public final class JoudPersistenceUnitInfo implements PersistenceUnitInfo {

    private static final String DEFAULT_PROVIDER = "org.hibernate.jpa.HibernatePersistenceProvider";

    private final PersistenceUnitDefinition definition;
    private final List<String> managedClassNames;
    private final DataSource nonJtaDataSource;
    private final ClassLoader classLoader;

    public JoudPersistenceUnitInfo(PersistenceUnitDefinition definition, List<String> managedClassNames,
            DataSource nonJtaDataSource, ClassLoader classLoader) {
        this.definition = definition;
        this.managedClassNames = List.copyOf(managedClassNames);
        this.nonJtaDataSource = nonJtaDataSource;
        this.classLoader = classLoader;
    }

    @Override
    public String getPersistenceUnitName() {
        return definition.name();
    }

    @Override
    public String getPersistenceProviderClassName() {
        return definition.providerClass() != null ? definition.providerClass() : DEFAULT_PROVIDER;
    }

    @Override
    public PersistenceUnitTransactionType getTransactionType() {
        return "JTA".equalsIgnoreCase(definition.transactionType())
                ? PersistenceUnitTransactionType.JTA
                : PersistenceUnitTransactionType.RESOURCE_LOCAL;
    }

    @Override
    public DataSource getJtaDataSource() {
        return null;
    }

    @Override
    public DataSource getNonJtaDataSource() {
        return nonJtaDataSource;
    }

    @Override
    public List<String> getMappingFileNames() {
        return definition.mappingFiles();
    }

    @Override
    public List<URL> getJarFileUrls() {
        return List.of();
    }

    @Override
    public URL getPersistenceUnitRootUrl() {
        return definition.rootUrl();
    }

    @Override
    public List<String> getManagedClassNames() {
        return managedClassNames;
    }

    @Override
    public boolean excludeUnlistedClasses() {
        return definition.excludeUnlistedClasses();
    }

    @Override
    public SharedCacheMode getSharedCacheMode() {
        return SharedCacheMode.UNSPECIFIED;
    }

    @Override
    public ValidationMode getValidationMode() {
        return ValidationMode.AUTO;
    }

    @Override
    public Properties getProperties() {
        Properties properties = new Properties();
        properties.putAll(definition.properties());
        return properties;
    }

    @Override
    public String getPersistenceXMLSchemaVersion() {
        return "3.1";
    }

    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    @Override
    public void addTransformer(ClassTransformer transformer) {
        // لا نستخدم bytecode enhancement
    }

    @Override
    public ClassLoader getNewTempClassLoader() {
        return new URLClassLoader(new URL[0], classLoader);
    }
}
