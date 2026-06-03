# Joud Core Shell — القلب الثابت

## متطلبات التشغيل
- JDK 21 ✅ (موجود على جهازك)
- VS Code ✅ (موجود على جهازك)
- Maven — سيثبته VS Code تلقائياً

---

## خطوات الفتح في VS Code

1. افتح VS Code
2. File → Open Folder → اختر مجلد `joud-core`
3. عند ظهور رسالة "Install recommended extensions" → اضغط **Install**
4. انتظر دقيقة حتى يكتمل تحميل Maven والمكتبات
5. ستجد المشروع جاهزاً في Explorer

---

## هيكل المشروع

```
joud-core/
├── pom.xml                          ← إعدادات Maven والمكتبات
├── src/
│   └── main/
│       ├── java/com/joud/core/
│       │   ├── module/
│       │   │   ├── JoudModule.java       ← واجهة القابس الرئيسية
│       │   │   └── PluginRegistry.java   ← ماكنة اكتشاف الوحدات
│       │   ├── menu/
│       │   │   └── MenuItem.java         ← عنصر القائمة
│       │   └── report/
│       │       ├── ReportDefinition.java ← وصف التقرير
│       │       └── ReportParameter.java  ← معامل فلترة التقرير
│       └── resources/
│           └── META-INF/
└── .vscode/
    ├── settings.json
    └── extensions.json
```

---

## الوحدات القادمة (Plugins)

كل وحدة ستكون مشروع Maven منفصل يعتمد على joud-core:

| الوحدة | artifactId | تعتمد على |
|--------|-----------|-----------|
| الأمان والمستخدمين | joud-security | joud-core |
| المحاسبة | joud-accounting | joud-core |
| الموظفين | joud-hr | joud-core |
| المبيعات والمشتريات | joud-trade | joud-core, joud-accounting |
| المخزون | joud-inventory | joud-core, joud-trade |

---

## أمر البناء

```bash
mvn clean install
```
# joud-core
# joud-core
