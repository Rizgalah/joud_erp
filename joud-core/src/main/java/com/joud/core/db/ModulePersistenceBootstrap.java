package com.joud.core.db;

import com.joud.core.module.JoudModule;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.jpa.HibernatePersistenceProvider;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ينشئ الـ EntityManagerFactory بعد دمج كيانات الوحدات المثبتة فعلاً:
 *
 *  - persistence.xml يبقى مصدر الإعدادات (DataSource وخصائص Hibernate)،
 *    وقائمة كياناته تُعامَل كمجموعة شاملة: ما غاب jar الخاص به يُتخطّى.
 *  - كل وحدة مثبتة (JoudModule عبر SPI) تُضيف كياناتها عبر getEntityClasses().
 *
 * فحذف jar وحدة أو إضافته لا يتطلب تعديل persistence.xml.
 */
public final class ModulePersistenceBootstrap {

    private static final Logger LOG = Logger.getLogger(ModulePersistenceBootstrap.class.getName());
    private static final int MAX_SPI_ENTRIES = 200;

    private ModulePersistenceBootstrap() {
    }

    public static EntityManagerFactory createFactory(String unitName, ClassLoader classLoader)
            throws IOException, NamingException {
        PersistenceUnitDefinition definition = PersistenceXmlReader.readFromClasspath(classLoader, unitName)
                .orElseThrow(() -> new IllegalStateException(
                        "Persistence unit '" + unitName + "' was not found in META-INF/persistence.xml"));

        List<Class<?>> moduleClasses = collectModuleEntities(classLoader);

        EntityClassResolver.Result result = EntityClassResolver.resolve(
                definition.classNames(), moduleClasses, name -> isLoadable(classLoader, name));

        LOG.info("Persistence unit '" + unitName + "': " + result.classNames().size() + " entities ("
                + result.addedByModules().size() + " added by installed modules, "
                + result.skippedMissing().size() + " skipped because their module is not installed).");
        if (!result.skippedMissing().isEmpty()) {
            LOG.info("Skipped (module not installed): " + result.skippedMissing());
        }
        if (!result.addedByModules().isEmpty()) {
            LOG.info("Added from modules (not listed in persistence.xml): " + result.addedByModules());
        }

        JoudPersistenceUnitInfo info = new JoudPersistenceUnitInfo(
                definition, result.classNames(), lookupDataSource(definition.nonJtaDataSource()), classLoader);

        return new HibernatePersistenceProvider().createContainerEntityManagerFactory(info, Map.of());
    }

    /** كيانات كل وحدة مثبتة (JoudModule) على الـ classpath. */
    static List<Class<?>> collectModuleEntities(ClassLoader classLoader) {
        List<Class<?>> classes = new ArrayList<>();
        Iterator<JoudModule> modules = ServiceLoader.load(JoudModule.class, classLoader).iterator();

        for (int i = 0; i < MAX_SPI_ENTRIES; i++) {
            try {
                if (!modules.hasNext()) {
                    break;
                }
                JoudModule module = modules.next();
                List<Class<?>> declared = module.getEntityClasses();
                if (declared != null) {
                    classes.addAll(declared);
                }
            } catch (ServiceConfigurationError | RuntimeException e) {
                LOG.log(Level.WARNING, "A module could not be loaded while collecting entities; skipping it.", e);
            }
        }
        return classes;
    }

    private static boolean isLoadable(ClassLoader classLoader, String className) {
        try {
            Class.forName(className, false, classLoader);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static DataSource lookupDataSource(String jndiName) throws NamingException {
        if (jndiName == null || jndiName.isBlank()) {
            return null; // اتصال JDBC مباشر عبر خصائص jakarta.persistence.jdbc.*
        }
        return (DataSource) new InitialContext().lookup(jndiName);
    }
}
