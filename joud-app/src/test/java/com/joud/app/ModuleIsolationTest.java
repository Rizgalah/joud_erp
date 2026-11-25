package com.joud.app;

import com.joud.core.db.EntityClassResolver;
import com.joud.core.db.EntityClassResolver.Result;
import com.joud.core.db.PersistenceUnitDefinition;
import com.joud.core.db.PersistenceXmlReader;
import com.joud.core.module.JoudModule;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * اختبار العزل بين الوحدات (plug-in): يحاكي غياب كل وحدة على حدة (وغياب كلها)
 * ويتحقق أن كيانات بقية الوحدات تبقى سليمة التعيين مع Hibernate، دون قاعدة بيانات.
 *
 * إن فشل اختبار "غياب وحدة" فهذا يعني ارتباطاً غير معلن بين وحدتين
 * (كيان يشير إلى كيان في وحدة أخرى).
 */
class ModuleIsolationTest {

    private static final String UNIT = "JoudERP";

    /**
     * الاعتماديات المعلنة في pom.xml: الوحدة → الوحدات التي لا تعمل بدونها.
     * المفتاح آخر جزء من اسم حزمة الوحدة (com.joud.hr → hr). حدّثها مع أي تغيير في الـ poms.
     */
    private static final Map<String, Set<String>> DECLARED_DEPENDENCIES = Map.of(
            "hr", Set.of("accounting"),
            "inventory", Set.of("trade"));

    private static ClassLoader classLoader;
    private static PersistenceUnitDefinition unit;
    private static List<JoudModule> modules;

    @BeforeAll
    static void load() throws Exception {
        classLoader = Thread.currentThread().getContextClassLoader();
        unit = PersistenceXmlReader.readFromClasspath(classLoader, UNIT)
                .orElseThrow(() -> new AssertionError("persistence unit '" + UNIT + "' not found"));
        modules = new ArrayList<>();
        ServiceLoader.load(JoudModule.class, classLoader).forEach(modules::add);
    }

    // ─── اختبارات الاتساق ───────────────────────────

    @Test
    @DisplayName("الوحدات تُكتشف عبر SPI")
    void modulesAreDiscovered() {
        assertFalse(modules.isEmpty(), "no JoudModule found via META-INF/services");
    }

    @Test
    @DisplayName("كل كيان مذكور في persistence.xml موجود فعلاً على الـ classpath")
    void everyListedEntityExists() {
        List<String> missing = unit.classNames().stream().filter(n -> !isLoadable(n)).toList();

        assertTrue(missing.isEmpty(), "Listed in persistence.xml but not found: " + missing);
    }

    @Test
    @DisplayName("كل وحدة تعلن في getEntityClasses() كل كياناتها المذكورة في persistence.xml")
    void everyModuleDeclaresAllItsEntities() {
        List<String> problems = new ArrayList<>();
        for (JoudModule module : modules) {
            String prefix = packageOf(module) + ".";
            Set<String> declared = entityNamesOf(module);
            List<String> missing = unit.classNames().stream()
                    .filter(n -> n.startsWith(prefix))
                    .filter(n -> !declared.contains(n))
                    .toList();
            if (!missing.isEmpty()) {
                problems.add(module.getModuleId() + " must add to getEntityClasses(): " + missing);
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    // ─── محاكاة الحضور والغياب ──────────────────────

    @Test
    @DisplayName("كل الوحدات مثبتة: تعيينات Hibernate لكل الكيانات سليمة")
    void fullSetBuilds() {
        Result result = resolve(modules, name -> true);

        assertEquals(result.classNames().size(), buildMetadata(result.classNames()),
                "mapping errors in the full entity set");
    }

    @Test
    @DisplayName("غياب كل وحدة على حدة (مع الوحدات المعلن اعتمادها عليها): التطبيق سليم")
    void applicationWorksWithoutEachModule() {
        for (JoudModule removedModule : modules) {
            Set<String> removedKeys = withDependents(keyOf(removedModule));
            Scenario scenario = scenario(removedKeys);

            Result result = resolve(scenario.remaining(), scenario.installed());

            assertTrue(result.classNames().stream().noneMatch(scenario::isHidden),
                    "entities of removed modules " + removedKeys + " must not remain");
            assertEquals(result.classNames().size(), buildMetadata(result.classNames()),
                    "Removing " + removedKeys + " breaks other modules' mappings "
                            + "(undeclared coupling between modules)");
        }
    }

    @Test
    @DisplayName("غياب كل الوحدات: يبقى القلب سليماً")
    void applicationWorksWithCoreOnly() {
        Scenario scenario = scenario(modules.stream().map(ModuleIsolationTest::keyOf).collect(Collectors.toSet()));

        Result result = resolve(scenario.remaining(), scenario.installed());

        assertTrue(result.classNames().stream().allMatch(n -> n.startsWith("com.joud.core.")),
                "only core entities should remain: " + result.classNames());
        assertEquals(result.classNames().size(), buildMetadata(result.classNames()));
    }

    @Test
    @DisplayName("نسيان ذكر كيانات وحدة في persistence.xml لا يضرّ: تضيفها الوحدة بنفسها")
    void moduleEntitiesAreAddedEvenIfNotListed() {
        for (JoudModule module : modules) {
            Set<String> own = entityNamesOf(module);
            if (own.isEmpty()) {
                continue;
            }
            List<String> listedWithoutModule = unit.classNames().stream().filter(n -> !own.contains(n)).toList();

            Result result = EntityClassResolver.resolve(listedWithoutModule, allModuleClasses(modules), n -> true);

            assertTrue(result.classNames().containsAll(own),
                    "entities of '" + module.getModuleId() + "' must be added by the module itself");
            assertEquals(result.classNames().size(), buildMetadata(result.classNames()));
        }
    }

    // ─── أدوات ──────────────────────────────────────

    private record Scenario(List<JoudModule> remaining, Set<String> hiddenPrefixes) {
        Predicate<String> installed() {
            return name -> !isHidden(name);
        }

        boolean isHidden(String name) {
            return hiddenPrefixes.stream().anyMatch(name::startsWith);
        }
    }

    private static Scenario scenario(Set<String> removedKeys) {
        List<JoudModule> remaining = new ArrayList<>();
        Set<String> hidden = new HashSet<>();
        for (JoudModule module : modules) {
            if (removedKeys.contains(keyOf(module))) {
                hidden.add(packageOf(module) + ".");
            } else {
                remaining.add(module);
            }
        }
        return new Scenario(remaining, hidden);
    }

    /** الوحدة المحذوفة + كل وحدة تعتمد عليها (معلنةً في pom.xml أو في getRequiredModules). */
    private static Set<String> withDependents(String removedKey) {
        Set<String> removed = new LinkedHashSet<>(Set.of(removedKey));
        boolean changed = true;
        while (changed) {
            changed = false;
            for (JoudModule module : modules) {
                if (removed.contains(keyOf(module))) {
                    continue;
                }
                Set<String> needs = new HashSet<>(DECLARED_DEPENDENCIES.getOrDefault(keyOf(module), Set.of()));
                needs.addAll(module.getRequiredModules());
                boolean dependsOnRemoved = needs.stream().anyMatch(n -> removed.contains(n)
                        || modules.stream().anyMatch(m -> removed.contains(keyOf(m)) && m.getModuleId().equals(n)));
                if (dependsOnRemoved) {
                    removed.add(keyOf(module));
                    changed = true;
                }
            }
        }
        return removed;
    }

    private static Result resolve(List<JoudModule> installedModules, Predicate<String> installed) {
        return EntityClassResolver.resolve(unit.classNames(), allModuleClasses(installedModules), installed);
    }

    private static List<Class<?>> allModuleClasses(List<JoudModule> installedModules) {
        List<Class<?>> classes = new ArrayList<>();
        installedModules.forEach(m -> classes.addAll(m.getEntityClasses()));
        return classes;
    }

    private static Set<String> entityNamesOf(JoudModule module) {
        return module.getEntityClasses().stream().map(Class::getName).collect(Collectors.toSet());
    }

    private static String packageOf(JoudModule module) {
        return module.getClass().getPackageName();
    }

    private static String keyOf(JoudModule module) {
        String pkg = packageOf(module);
        return pkg.substring(pkg.lastIndexOf('.') + 1);
    }

    private static boolean isLoadable(String className) {
        try {
            Class.forName(className, false, classLoader);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** يبني تعيينات Hibernate (دون قاعدة بيانات) ويُعيد عدد الكيانات المعرَّفة. */
    private static int buildMetadata(List<String> classNames) {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .build();
        try {
            MetadataSources sources = new MetadataSources(registry);
            for (String name : classNames) {
                try {
                    sources.addAnnotatedClass(Class.forName(name, false, classLoader));
                } catch (ClassNotFoundException e) {
                    throw new AssertionError("Entity class not found: " + name, e);
                }
            }
           
            return sources.buildMetadata().getEntityBindings().size();
            
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
        
        
    }
}
