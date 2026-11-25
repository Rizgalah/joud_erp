package com.joud.core.db;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * محتوى وحدة استمرارية (persistence-unit) مقروءة من persistence.xml:
 * الإعدادات والكيانات المذكورة صراحةً.
 *
 * @param transactionType RESOURCE_LOCAL أو JTA
 * @param rootUrl         جذر المكان الذي وُجد فيه الملف (WEB-INF/classes مثلاً)، وقد يكون null
 */
public record PersistenceUnitDefinition(
        String name,
        String transactionType,
        String providerClass,
        String jtaDataSource,
        String nonJtaDataSource,
        List<String> classNames,
        List<String> mappingFiles,
        boolean excludeUnlistedClasses,
        Map<String, String> properties,
        URL rootUrl) {

    public PersistenceUnitDefinition {
        classNames = List.copyOf(classNames);
        mappingFiles = List.copyOf(mappingFiles);
        properties = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    public PersistenceUnitDefinition withRootUrl(URL url) {
        return new PersistenceUnitDefinition(name, transactionType, providerClass, jtaDataSource,
                nonJtaDataSource, classNames, mappingFiles, excludeUnlistedClasses, properties, url);
    }
}
