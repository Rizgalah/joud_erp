package com.joud.security;

import jakarta.ws.rs.NameBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@NameBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface Secured {
    // نتيح إمكانية تمرير مصفوفة من الصلاحيات، وتركها فارغة يعني "أي مستخدم مسجل"
    String[] value() default {};
}