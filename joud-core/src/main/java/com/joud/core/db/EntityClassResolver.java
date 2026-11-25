package com.joud.core.db;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * يحدد قائمة الكيانات النهائية للتطبيق:
 *   1. الكيانات المذكورة في persistence.xml، لكن الموجودة فعلاً على الـ classpath فقط
 *      (فحذف jar وحدة لا يمنع إقلاع التطبيق ولا يتطلب تعديل الملف)،
 *   2. مضافاً إليها الكيانات التي تعلنها الوحدات المثبتة عبر getEntityClasses()
 *      (فإضافة وحدة جديدة لا تتطلب تعديل الملف).
 */
public final class EntityClassResolver {

    private EntityClassResolver() {
    }

    /**
     * @param classNames     القائمة النهائية بالترتيب
     * @param skippedMissing مذكورة في persistence.xml لكنها غير موجودة (وحدة محذوفة)
     * @param addedByModules أضافتها الوحدات ولم تكن مذكورة في persistence.xml
     */
    public record Result(List<String> classNames, List<String> skippedMissing, List<String> addedByModules) {
    }

    public static Result resolve(Collection<String> listedInXml, Collection<Class<?>> moduleClasses,
            Predicate<String> isAvailable) {
        Set<String> resolved = new LinkedHashSet<>();
        List<String> skipped = new ArrayList<>();
        for (String name : listedInXml) {
            if (isAvailable.test(name)) {
                resolved.add(name);
            } else {
                skipped.add(name);
            }
        }

        List<String> added = new ArrayList<>();
        for (Class<?> moduleClass : moduleClasses) {
            if (resolved.add(moduleClass.getName())) {
                added.add(moduleClass.getName());
            }
        }

        return new Result(List.copyOf(resolved), List.copyOf(skipped), List.copyOf(added));
    }
}
