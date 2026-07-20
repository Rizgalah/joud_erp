package com.joud.config;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

// هذا الصنف يجعل Tomcat يقرأ كل الكلاسات التي تحمل @Path
@ApplicationPath("/api") // 👈 هذا السطر هو الذي يفعّل البادئة في السيرفر!
public class RestApplication extends Application {

    // يترك فارغاً في البيئات الحديثة ليعتمد على المسح التلقائي (Auto-scanning)
}
