إليك هذه الوثيقة الهندسية المتكاملة والمصممة خصيصاً كمسودة خطة معمارية رسمية لنظام **Joud ERP**. يمكنك نسخها بالكامل وحفظها في ملف نصي أو ملف ماركداون (مثل i18n-architecture-plan.md) داخل مجلد التوثيق الخاص بمشروعك للرجوع إليها مستقبلاً عند بدء التنفيذ الفعلي للترجمة القياسية وتدويل النظام.

# **📑 وثيقة الخطة المعمارية لدعم اللغات المتعددة وتدويل النظام (Internationalization \- i18n)**

## **مشروع: Joud ERP**

**البيئة المستهدفة:** Java 21 / Jakarta EE 10 / JSF (Facelets) / Apache Tomcat

### **🎯 1\. الأهداف الاستراتيجية والمعايير الهندسية**

1. **المركزية (Centralization):** إدارة كافة النصوص والترجمات من مكان موحد داخل موديول الكور (joud-core) لخدمة بقية الموديولات (الحسابات، المستودعات، شؤون الموظفين... إلخ).  
2. **الأداء العالي (Performance):** الاعتماد على حزم الموارد القياسية لجافا (Resource Bundles) المعتمدة على ملفات الكفاءة الخفيفة .properties بدلاً من الملفات الضخمة مثل XML.  
3. **التوافق الأصيل مع الترميز (Native UTF-8):** الاستفادة من ميزة Java 21 التي تقرأ ملفات .properties بترميز UTF-8 تلقائياً دون الحاجة لأدوات تحويل خارجية.  
4. **الديناميكية البصرية (Dynamic RTL/LTR):** قلب اتجاه النظام بالكامل (الواجهات، القوائم، الجداول) برمجياً وبصرياً بناءً على لغة الجلسة الحالية للمستخدم.

### **📂 2\. هيكلية الملفات المقترحة ومساراتها (File Structure)**

توضع ملفات الترجمة والتحكم في موديول الكور لتصبح متاحة كقابس مشترك لكافة أجزاء الـ ERP:

Plaintext  
joud-core/  
└── src/  
    └── main/  
        ├── java/  
        │   └── com/joud/core/web/bean/  
        │       └── LocaleBean.java         \<-- مدير الجلسة واللغة والاتجاه  
        └── resources/  
            ├── messages\_ar.properties      \<-- قاموس النصوص العربية  
            ├── messages\_en.properties      \<-- قاموس النصوص الإنجليزية  
            └── META-INF/  
                └── faces-config.xml        \<-- تسجيل حزم اللغات في الـ JSF

### **📝 3\. قواميس المصطلحات المبدئية (.properties)**

#### **🇸🇦 ملف اللغة العربية الافتراضي: src/main/resources/messages\_ar.properties**

Properties  
\# ═══════════════════════════════════════════════════════════  
\# Joud ERP \- Arabic Language Bundle (Default)  
\# ═══════════════════════════════════════════════════════════  
app.title\=نظام جود لإدارة الموارد (Joud ERP)  
app.version\=الإصدار 1.0.0

\# واجهة تسجيل الدخول  
login.title\=تسجيل الدخول للنظام  
login.username\=اسم المستخدم  
login.password\=كلمة المرور  
login.submit\=دخول آمن  
login.forgot\_password\=نسيت كلمة المرور؟

\# القوائم الرئيسية العامة  
menu.dashboard\=لوحة التحكم  
menu.accounting\=النظام المالي والحسابات  
menu.inventory\=إدارة المستودعات والمخازن  
menu.hr\=شؤون الموظفين والموارد البشرية

\# أزرار عامة  
btn.save\=حفظ  
btn.cancel\=إلغاء  
btn.delete\=حذف  
btn.edit\=تعديل

#### **🇬🇧 ملف اللغة الإنجليزية: src/main/resources/messages\_en.properties**

Properties  
\# ═══════════════════════════════════════════════════════════  
\# Joud ERP \- English Language Bundle  
\# ═══════════════════════════════════════════════════════════  
app.title\=Joud Enterprise Resource Planning (Joud ERP)  
app.version\=Version 1.0.0

\# Login Interface  
login.title\=System Secure Login  
login.username\=Username  
login.password\=Password  
login.submit\=Login Securely  
login.forgot\_password\=Forgot Password?

\# Main Menu  
menu.dashboard\=Dashboard  
menu.accounting\=Financial & Accounting Suite  
menu.inventory\=Inventory & Warehouse Management  
menu.hr\=Human Resources & Payroll

\# General Buttons  
btn.save\=Save  
btn.cancel\=Cancel  
btn.delete\=Delete  
btn.edit\=Edit

### **⚙️ 4\. ضبط الإعدادات المركزية (faces-config.xml)**

يتم تسجيل الملفات الأساسية وتحديد اللغة الافتراضية للنظام (العربية) داخل ملف الإعدادات المتواجد في الكور:

XML  
\<application\>  
    \<resource-bundle\>  
        \<base-name\>messages\</base-name\>  
        \<var\>msg\</var\>  
    \</resource-bundle\>  
      
    \<locale-config\>  
        \<default-locale\>ar\</default-locale\>  
        \<supported-locale\>ar\</supported-locale\>  
        \<supported-locale\>en\</supported-locale\>  
    \</locale-config\>  
\</application\>

### **☕ 5\. الطبقة البرمجية المتحكمة (LocaleBean.java)**

مكون جافا مدار على مستوى جلسة المستخدم المتصل (Session Scope) للحفاظ على خياراته طوال فترة تصفحه للنظام:

Java  
package com.joud.core.web.bean;

import jakarta.enterprise.context.SessionScoped;  
import jakarta.inject.Named;  
import java.io.Serializable;  
import java.util.Locale;

@Named("localeBean")  
@SessionScoped  
public class LocaleBean implements Serializable {  
    private static final long serialVersionUID \= 1L;

    // الإعدادات الافتراضية للنظام عند أول دخول للمستخدم  
    private Locale locale \= new Locale("ar");   
    private String dir \= "rtl";               

    public Locale getLocale() { return locale; }  
    public String getDir() { return dir; }  
    public String getLanguage() { return locale.getLanguage(); }

    /\*\*  
     \* دالة تبديل لغة النظام ديناميكياً وعكس اتجاه الشاشات بصرة فورية  
     \* @param lang كود اللغة المستهدفة (ar أو en)  
     \*/  
    public void changeLanguage(String lang) {  
        if ("en".equalsIgnoreCase(lang)) {  
            this.locale \= new Locale("en");  
            this.dir \= "ltr"; // من اليسار إلى اليمين للغة الإنجليزية  
        } else {  
            this.locale \= new Locale("ar");  
            this.dir \= "rtl"; // من اليمين إلى اليسار للغة العربية  
        }  
    }  
}

### **🖼️ 6\. معمارية ربط الواجهات والقوالب (template.xhtml)**

يتم دمج المتغيرات الديناميكية في قالب النظام الرئيسي لتعمل المتصفحات تلقائياً على إعادة هيكلة العرض بمرونة:

XML  
\<?xml version="1.0" encoding="UTF-8"?\>  
\<\!DOCTYPE **html**\>  
\<html xmlns\="http://www.w3.org/1999/xhtml"  
      xmlns:h\="http://jakarta.faces.html"  
      xmlns:f\="http://jakarta.faces.core"  
      xmlns:ui\="http://jakarta.faces.facelets"  
      lang\="\#{localeBean.language}"  
      dir\="\#{localeBean.dir}"\> \<h:head\>  
    \<meta charset\="UTF-8" /\>  
    \<title\>\#{msg\['app.title'\]}\</title\>  
    \<h:outputStylesheet library\="css" name\="joud-theme.css" /\>  
\</h:head\>

\<h:body\>  
    \<f:view locale\="\#{localeBean.locale}"\> \<h:form id\="langForm"\>  
            \<div class\="lang-switcher"\>  
                \<h:commandLink value\="English" action\="\#{localeBean.changeLanguage('en')}" rendered\="\#{localeBean.language eq 'ar'}" /\>  
                \<h:commandLink value\="العربية" action\="\#{localeBean.changeLanguage('ar')}" rendered\="\#{localeBean.language eq 'en'}" /\>  
            \</div\>  
        \</h:form\>

        \<div class\="main-container"\>  
            \<ui:insert name\="content"\>  
                \</ui:insert\>  
        \</div\>

    \</f:view\>  
\</h:body\>  
\</html\>

### **💡 7\. توصيات معمارية مستقبلية للتصميم البصري (Modern CSS)**

* **CSS Logical Properties:** عند كتابة أكواد الـ CSS في الموديولات، يُنصح بشدة بالتوقف عن استخدام الخصائص الثابتة مثل margin-left أو float: right؛ واستبدالها بالخصائص المنطقية الحديثة مثل:  
  * margin-inline-start بدلاً من margin-left.  
  * text-align: start بدلاً من text-align: right.  
* **النتيجة البصرية:** المتصفح سيقوم بنقل الحواشي والأزرار تلقائياً بناءً على قيمة dir="rtl" أو dir="ltr" في وسم الـ html العلوي، مما يوفر عليك مستقبلاً عناء بناء ملفين CSS منفصلين لكل لغة ويحافظ على خفة موديول الكور.

هذه النسخة جاهزة للحفظ والأرشفة، وستكون دليلك ومرجعك الأساسي متى ما حان وقت الترجمة والتدويل القياسي لنظام **Joud ERP**. بالتوفيق دائماً يا صديقي في خطواتك المنظمة\!