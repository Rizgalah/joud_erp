# دليل نشر Joud ERP على Apache Tomcat

## المتطلبات

| البرنامج | الإصدار |
|----------|---------|
| Java JDK | 21 |
| Apache Tomcat | 10.1.x |
| PostgreSQL | 14+ |
| Maven | 3.9+ |

---

## الخطوة 1 — إعداد قاعدة البيانات

```sql
-- 1. أنشئ قاعدة البيانات
CREATE DATABASE joud_erp
  ENCODING 'UTF8'
  LC_COLLATE 'en_US.UTF-8'
  LC_CTYPE 'en_US.UTF-8';

-- 2. أنشئ مستخدماً خاصاً
CREATE USER joud_user WITH PASSWORD 'your_secure_password';
GRANT ALL PRIVILEGES ON DATABASE joud_erp TO joud_user;

-- 3. اتصل بالقاعدة ونفّذ ملفات SQL بالترتيب:
\c joud_erp

\i erp_database_schema_fixed.sql   -- 27 جدول
\i accounting_schema.sql           -- 6 جداول + 5 Views
\i hr_schema.sql                   -- 9 جداول + 2 Views
\i trade_schema.sql                -- 4 جداول + 3 Views
\i inventory_schema.sql            -- 5 جداول + 3 Views
```

---

## الخطوة 2 — تثبيت مكتبات Tomcat

انسخ ملفات JAR التالية إلى `Tomcat/lib/`:

```
postgresql-42.7.3.jar
weld-servlet-shaded-5.1.2.Final.jar
```

> **ملاحظة:** باقي المكتبات مضمَّنة في WAR نفسه.

---

## الخطوة 3 — إعداد الاتصال بقاعدة البيانات

### الطريقة 1: JNDI DataSource (موصى بها)

1. انسخ `context.xml` إلى:
   ```
   Tomcat/conf/Catalina/localhost/joud-erp.xml
   ```

2. عدّل البيانات:
   ```xml
   url="jdbc:postgresql://localhost:5432/joud_erp"
   username="joud_user"
   password="your_secure_password"
   ```

3. في `persistence.xml`، عطّل JDBC المباشر وفعّل JNDI:
   ```xml
   <jta-data-source>java:comp/env/jdbc/JoudDS</jta-data-source>
   <!-- احذف أو اعلّق عليها خصائص jdbc.* -->
   ```

### الطريقة 2: JDBC مباشر (للتطوير فقط)

عدّل `joud-app/src/main/resources/META-INF/persistence.xml`:
```xml
<property name="jakarta.persistence.jdbc.url"
          value="jdbc:postgresql://localhost:5432/joud_erp"/>
<property name="jakarta.persistence.jdbc.user"     value="joud_user"/>
<property name="jakarta.persistence.jdbc.password" value="your_password"/>
```

---

## الخطوة 4 — البناء

### أ. البناء الكامل (من الصفر)

ضع جميع مجلدات الوحدات في مجلد واحد:
```
joud-erp/
├── pom.xml          ← pom.xml الرئيسي (joud-erp-parent-pom.xml)
├── joud-core/
├── joud-accounting/
├── joud-hr/
├── joud-trade/
├── joud-inventory/
├── joud-dashboard/
└── joud-app/
```

ثم:
```bash
cd joud-erp
mvn clean package
# الناتج: joud-app/target/joud-erp.war
```

### ب. بناء التطبيق فقط (بعد تثبيت الوحدات في Maven local repo)

```bash
# أولاً: ثبّت الوحدات الستة
mvn install -pl joud-core,joud-accounting,joud-hr,joud-trade,joud-inventory,joud-dashboard

# ثم ابنِ WAR
mvn package -pl joud-app
```

---

## الخطوة 5 — النشر على Tomcat

```bash
# انسخ WAR إلى Tomcat
cp joud-app/target/joud-erp.war /path/to/tomcat/webapps/

# شغّل Tomcat
cd /path/to/tomcat
bin/startup.sh        # Linux/Mac
bin\startup.bat       # Windows

# افتح المتصفح
# http://localhost:8080/joud-erp/
```

---

## بيانات الدخول الأولى

بعد تثبيت قاعدة البيانات أضف مستخدماً أولياً:

```sql
-- BCrypt hash لكلمة المرور "admin123" (cost=12)
INSERT INTO users (user_i_id, username, password_hash, full_name, is_active)
VALUES (1, 'admin',
  '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LwTqa6hG5B8DmHqe',
  'مدير النظام', 1);

-- أنشئ دور المدير بكل الصلاحيات
INSERT INTO role (role_i_id, role_c_name, role_c_desc)
VALUES (1, 'ADMIN', 'مدير النظام — كامل الصلاحيات');

-- ربط المستخدم بالدور
INSERT INTO user_roles (user_i_id, role_i_id) VALUES (1, 1);
```

ثم أضف صلاحيات الدور (واحدة لكل صلاحية من قائمة getPermissions() في كل وحدة).

---

## التحقق من النشر

```bash
# تحقق من سجلات Tomcat
tail -f Tomcat/logs/catalina.out

# ابحث عن:
# INFO  PluginRegistry - Module loaded: accounting v1.0.0
# INFO  PluginRegistry - Module loaded: hr v1.0.0
# INFO  PluginRegistry - Module loaded: trade v1.0.0
# INFO  PluginRegistry - Module loaded: inventory v1.0.0
# INFO  PluginRegistry - Module loaded: dashboard v1.0.0
```

---

## استكشاف الأخطاء الشائعة

| الخطأ | السبب | الحل |
|-------|-------|------|
| `ClassNotFoundException: org.postgresql.Driver` | postgresql.jar غير موجود في lib/ | انسخه إلى Tomcat/lib/ |
| `WELD-001408: Unsatisfied dependencies` | beans.xml مفقود | تأكد من وجوده في WEB-INF/ |
| `org.hibernate.HibernateException: Missing table` | لم تُنفَّذ ملفات SQL | نفّذ الملفات بالترتيب |
| `javax.faces.FacesException` | Faces Servlet لم يُحمَّل | تحقق من web.xml |
| صفحة بيضاء بعد تسجيل الدخول | AuthFilter لا يجد الجلسة | تأكد من cookie JOUD_SESSION |
