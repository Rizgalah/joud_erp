package com.joud.core.db;

import com.joud.core.db.EntityClassResolver.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityClassResolverTest {

    private static final List<String> LISTED = List.of(
            "com.joud.core.User",
            "com.joud.accounting.JournalEntry",
            "com.joud.accounting.DayLedger",
            "com.joud.hr.Employee",
            "com.joud.trade.Invoice",
            "com.joud.notification.entity.SentEmailLog");

    static class ExtraEntity {
    }

    @Test
    @DisplayName("غياب كل وحدة على حدة: كياناتها تُتخطّى وبقية الكيانات تبقى")
    void eachModuleCanBeAbsent() {
        for (String module : List.of("accounting", "hr", "trade", "notification")) {
            Predicate<String> installed = name -> !name.startsWith("com.joud." + module + ".");

            Result result = EntityClassResolver.resolve(LISTED, List.of(), installed);

            assertTrue(result.classNames().stream().noneMatch(n -> n.startsWith("com.joud." + module + ".")),
                    "entities of '" + module + "' must be skipped");
            assertTrue(result.classNames().contains("com.joud.core.User"), "core entities always stay");
            assertEquals(LISTED.size(), result.classNames().size() + result.skippedMissing().size());
            assertTrue(result.addedByModules().isEmpty());
        }
    }

    @Test
    @DisplayName("غياب كل الوحدات: تبقى كيانات القلب وحدها")
    void allModulesAbsent() {
        Result result = EntityClassResolver.resolve(LISTED, List.of(), name -> name.startsWith("com.joud.core."));

        assertEquals(List.of("com.joud.core.User"), result.classNames());
        assertEquals(5, result.skippedMissing().size());
    }

    @Test
    @DisplayName("كيان تعلنه وحدة ولم يُذكر في persistence.xml يُضاف تلقائياً")
    void moduleDeclaredClassesAreAdded() {
        Result result = EntityClassResolver.resolve(LISTED, List.of(ExtraEntity.class), name -> true);

        assertEquals(LISTED.size() + 1, result.classNames().size());
        assertEquals(List.of(ExtraEntity.class.getName()), result.addedByModules());
    }

    @Test
    @DisplayName("لا تكرار إن أعلنت الوحدة كياناً مذكوراً أصلاً")
    void noDuplicates() {
        Result result = EntityClassResolver.resolve(List.of(ExtraEntity.class.getName()),
                List.of(ExtraEntity.class), name -> true);

        assertEquals(List.of(ExtraEntity.class.getName()), result.classNames());
        assertTrue(result.addedByModules().isEmpty());
    }

    @Test
    @DisplayName("الترتيب محفوظ: المذكورة أولاً ثم المضافة من الوحدات")
    void orderIsPreserved() {
        Result result = EntityClassResolver.resolve(LISTED, List.of(ExtraEntity.class), name -> true);

        assertEquals(LISTED, result.classNames().subList(0, LISTED.size()));
        assertEquals(ExtraEntity.class.getName(), result.classNames().get(LISTED.size()));
    }
}
