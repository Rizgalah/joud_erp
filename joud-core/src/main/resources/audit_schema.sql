-- 1. إعداد نوع الحالة (Enum)
CREATE TYPE email_status AS ENUM ('PENDING', 'SUCCESS', 'FAILED');
-- 2. جدول سجل البريد الإلكتروني
CREATE TABLE audit.sent_emails_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender VARCHAR(255) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    email_type VARCHAR(50) NOT NULL,
    -- e.g., 'PASSWORD_RESET', 'INVOICE_ALERT'
    subject VARCHAR(500) NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status email_status NOT NULL DEFAULT 'PENDING',
    error_message TEXT
);
-- 3. الفهارس (Indexes) لتحسين أداء الاستعلام للتدقيق والمراقبة
CREATE INDEX idx_sent_emails_recipient ON audit.sent_emails_log(recipient);
CREATE INDEX idx_sent_emails_sent_at ON audit.sent_emails_log(sent_at DESC);
CREATE INDEX idx_sent_emails_status ON audit.sent_emails_log(status);
-- إنشاء جدول توكنات إعادة تعيين كلمة المرور
CREATE TABLE password_reset_tokens (
    token_i_id BIGSERIAL PRIMARY KEY,
    user_i_id BIGINT NOT NULL,
    token_c_value VARCHAR(100) NOT NULL UNIQUE,
    token_d_expires TIMESTAMP NOT NULL,
    token_b_used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- فهرس لتسريع البحث عن طريق التوكن (مهم جداً لأن الـ Bean يبحث بالتوكن)
CREATE INDEX idx_reset_token_value ON password_reset_tokens (token_c_value);
-- فهرس لتسريع البحث عن التوكنات الخاصة بمستخدم معين (لإبطال التوكنات القديمة)
CREATE INDEX idx_reset_token_user ON password_reset_tokens (user_i_id);
--الخطوة 1: تحويل أسماء الأعمدة (Columns) إلى أحرف صغيرة
DO $$
DECLARE r RECORD;
BEGIN FOR r IN
SELECT table_name,
    column_name
FROM information_schema.columns
WHERE table_schema = 'public'
    AND column_name <> lower(column_name) -- تجنب جداول النظام والـ Views
    AND table_name NOT IN (
        SELECT table_name
        FROM information_schema.views
        WHERE table_schema = 'public'
    ) LOOP EXECUTE 'ALTER TABLE public."' || r.table_name || '" RENAME COLUMN "' || r.column_name || '" TO "' || lower(r.column_name) || '"';
END LOOP;
END $$;
--الخطوة 2: تحويل أسماء الجداول (Tables) إلى أحرف صغيرة
DO $$
DECLARE r RECORD;
BEGIN FOR r IN
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
    AND table_type = 'BASE TABLE'
    AND table_name <> lower(table_name) LOOP EXECUTE 'ALTER TABLE public."' || r.table_name || '" RENAME TO "' || lower(r.table_name) || '"';
END LOOP;
END $$;
--1. الاستعلام الأول: فحص أسماء الجداول (Tables)
--هذا الاستعلام يبحث عن أي جدول في الـ Schema العامة (public) لا يزال يحتوي على حروف كبيرة:
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
    AND table_type = 'BASE TABLE'
    AND table_name <> lower(table_name);
--2. الاستعلام الثاني: فحص أسماء الأعمدة (Columns)
--هذا الاستعلام يبحث في جميع جداولك عن أي عمود (Column) لا يزال يحتفظ بحروف كبيرة:
SELECT table_name,
    column_name
FROM information_schema.columns
WHERE table_schema = 'public'
    AND column_name <> lower(column_name) -- تجنب جداول النظام والـ Views
    AND table_name NOT IN (
        SELECT table_name
        FROM information_schema.views
        WHERE table_schema = 'public'
    );
DO $$
DECLARE r RECORD;
next_id BIGINT;
BEGIN -- 1. معرفة آخر ID تم استخدامه لتبدأ الزيادة منه بأمان
SELECT COALESCE(MAX(key_i_id), 0) INTO next_id
FROM key_generation;
-- 2. الدوران على جميع الجداول الفعلية وتجاهل الـ Views والجدول نفسه
FOR r IN
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
    AND table_type = 'BASE TABLE'
    AND table_name <> 'key_generation' -- تجنب الجداول التي تم تسجيلها مسبقاً في جدول الترقيم
    AND table_name NOT IN (
        SELECT table_c_name
        FROM key_generation
    ) LOOP -- زيادة العداد بمقدار 1
    next_id := next_id + 1;
-- 3. إدخال السجل الجديد
INSERT INTO key_generation (key_i_id, table_c_name, id_i_gen, id_c_remarks)
VALUES (
        next_id,
        r.table_name,
        1,
        -- القيمة الابتدائية للترقيم التلقائي
        'Sequence for ' || r.table_name || ' table'
    );
END LOOP;
END $$;
CREATE TABLE JOUD_SEQUENCE_REGISTRY (
    SEQ_NAME VARCHAR(100) PRIMARY KEY,
    -- سيخزن اسم الجدول (مثل GL_SUB_LEDGER)
    NEXT_VAL NUMERIC(19, 0) NOT NULL -- القيمة الحالية للمعير
);
DO $$
DECLARE r RECORD;
BEGIN FOR r IN
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
    AND table_type = 'BASE TABLE' -- تجنب الجداول التي تم تسجيلها مسبقاً في جدول الترقيم
    AND table_name NOT IN (
        SELECT seq_name
        FROM JOUD_SEQUENCE_REGISTRY
    ) LOOP -- إدراج الجدول مع القيمة الأولية 1
INSERT INTO JOUD_SEQUENCE_REGISTRY (SEQ_NAME, NEXT_VAL)
VALUES (r.table_name, 1);
END LOOP;
END $$;
--1. أنشئ الجدول المؤقت إذا لم يكن موجوداً
CREATE TABLE IF NOT EXISTS temp_module_config (
    MODULE_ID VARCHAR(50) PRIMARY KEY,
    ACTIVE BOOLEAN DEFAULT TRUE
);
--2. انسخ البيانات من جدولك إلى الجدول المؤقت
-- سيتم نسخ كل الجداول المسجلة، مع الاحتفاظ بالحالة السابقة
INSERT INTO temp_module_config (MODULE_ID, ACTIVE)
SELECT MODULE_ID,
    ACTIVE
FROM JOUD_SYSTEM_MODULES ON CONFLICT (MODULE_ID) DO NOTHING;
--3. حذف الجدول الأصلي
DROP TABLE JOUD_SYSTEM_MODULES;
--4. إعادة إنشاء الجدول بالهيكل الجديد (مع حذف عمود STATUS الإجباري)
CREATE TABLE JOUD_SYSTEM_MODULES (
    MODULE_ID VARCHAR(50) PRIMARY KEY,
    -- سيخزن اسم الموديول (مثل accounting)
    ACTIVE BOOLEAN DEFAULT TRUE -- تم التبديل من VARCHAR إلى Boolean
);
--5. إرجاع البيانات من الجدول المؤقت إلى الجدول الجديد
INSERT INTO JOUD_SYSTEM_MODULES (MODULE_ID, ACTIVE)
SELECT MODULE_ID,
    ACTIVE
FROM temp_module_config;
--6. حذف الجدول المؤقت
DROP TABLE temp_module_config;
CREATE TABLE JOUD_SYSTEM_MODULES (
    MODULE_ID VARCHAR(50) PRIMARY KEY,
    -- مثل "accounting"
    MODULE_NAME VARCHAR(100) NOT NULL,
    VERSION VARCHAR(20) NOT NULL,
    INSTALLED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    STATUS VARCHAR(15) DEFAULT 'ACTIVE'
);