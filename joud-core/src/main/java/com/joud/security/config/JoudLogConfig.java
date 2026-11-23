package com.joud.security.config;

import java.io.File;
import java.io.IOException;
import java.util.logging.*;

public class JoudLogConfig {

    public static void initializeLogger() {
        try {
            Logger rootLogger = Logger.getLogger(""); // جلب اللوجر الرئيسي للنظام

            // 1. تحديد مسار المجلد والملف (مثلاً في مجلد للمشروع أو مجلد المستخدم الحالي)
            String logDirPath = System.getProperty("user.home") + File.separator + "joud_logs";
            File logDir = new File(logDirPath);
            if (!logDir.exists()) {
                logDir.mkdirs(); // إنشاء المجلد إن لم يكن موجوداً
            }

            String logFilePath = logDirPath + File.separator + "joud.log";

            // 2. إعداد الـ FileHandler:
            // البارامترات: المسار، الحد الأقصى للملف (10 ميجابايت)، عدد ملفات التدوير (5)،
            // تفعيل الإضافة (Append)
            FileHandler fileHandler = new FileHandler(logFilePath, 10 * 1024 * 1024, 5, true);

            // 3. تخصيص تنسيق السطر البرمجي ليكون مقروءاً وأنيقاً
            // %1=الوقت، %4=مستوى الخطأ (INFO/SEVERE)، %5=الرسالة، %6=الـ Stack Trace إن
            // وُجد
            fileHandler.setFormatter(new SimpleFormatter() {
                // التعديل هنا: جعلنا المؤشرات متسلسلة (1، 2، 3، 4) لتطابق المتغيرات الممررة
                // بالأسفل
                private static final String FORMAT = "[%1$tF %1$tT] [%2$-7s] %3$s %4$s%n";

                @Override
                public synchronized String format(LogRecord lr) {
                    return String.format(FORMAT,
                            java.util.Date.from(lr.getInstant()), // %1$ -> الوقت
                            lr.getLevel().getLocalizedName(), // %2$ -> مستوى الخطأ
                            lr.getMessage(), // %3$ -> الرسالة
                            lr.getThrown() != null ? "\n" + parseException(lr.getThrown()) : "" // %4$ -> الـ Stack
                                                                                                // Trace
                    );
                }
            });

            // 4. ضبط مستوى الحفظ الأدنى ليقوم بالتقاط كل شيء من INFO فما فوق
            fileHandler.setLevel(Level.INFO);
            // بناء وتطبيق فلتر ذكي مخصص للتحكم بما يدخل الملف
            fileHandler.setFilter(new Filter() {
                @Override
                public boolean isLoggable(LogRecord record) {
                    String loggerName = record.getLoggerName();

                    // حماية ضد القيم الفارغة
                    if (loggerName == null) {
                        return false;
                    }

                    // 1. السماح الكامل لجميع سجلات نظام جود (INFO, WARNING, SEVERE...)
                    if (loggerName.startsWith("com.joud")) {
                        return true;
                    }

                    // 2. الحزم الخارجية (مثل هيبورنيت وجاكرتا): لا تسمح لها بالكتابة
                    // إلا إذا كان مستوى الخطأ خطيراً جداً SEVERE لحل مشاكل السيرفر
                    if (loggerName.startsWith("org.hibernate") || loggerName.startsWith("jakarta")) {
                        return record.getLevel().intValue() >= Level.SEVERE.intValue();
                    }

                    // 3. منع بقية الحزم الخارجية تماماً من دخول ملف joud.log
                    return false;
                }
            });
            // حذف كل المعالجات الافتراضية (التي تطبع على الشاشة) لمنع التكرار
            rootLogger.setUseParentHandlers(false);

            // 5. ربط الحارس بـ Root Logger ليطبق على كل الموديولات
            rootLogger.addHandler(fileHandler);

        } catch (IOException e) {
            System.err.println("فشلت عملية تهيئة نظام تسجيل joud.log المخصص: " + e.getMessage());
        }
    }

    private static String parseException(Throwable t) {
        // دالة مساعدة لتحويل الـ Stack Trace إلى نص منسق داخل الملف
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        t.printStackTrace(pw);
        return sw.toString();
    }
}