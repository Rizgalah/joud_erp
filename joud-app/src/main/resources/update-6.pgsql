-- 1. إصلاح جدول المستخدمين: تحويل حقل المحاولات الفاشلة إلى رقمي وتصحيح التسمية
ALTER TABLE users RENAME COLUMN failed_c_attempts TO failed_i_attempts;
ALTER TABLE users ALTER COLUMN failed_i_attempts TYPE INT USING (failed_i_attempts::integer);
ALTER TABLE users ALTER COLUMN failed_i_attempts SET DEFAULT 0;

-- 2. تأمين المفتاح الأجنبي للموظف في جدول المستخدمين (إذا لم يكن موجوداً)
-- ALTER TABLE users ADD COLUMN emp_i_id BIGINT REFERENCES employee_mast(emp_i_id);

-- 3. تنظيف جدول الموظفين وإضافة حقل الكود الوظيفي
-- أولاً تأكد من حذف حقل العلاقة العكسية لإنهاء الدائرية
ALTER TABLE EMPLOYEE_MAST DROP COLUMN IF EXISTS user_i_id;

-- إضافة كود الموظف (الرقم الوظيفي) الذي يبحث عنه الكود
ALTER TABLE EMPLOYEE_MAST ADD COLUMN emp_c_code VARCHAR(30) UNIQUE;

-- 4. تحديث البيانات الحالية للمدير كمثال صحيح
UPDATE EMPLOYEE_MAST 
SET emp_i_id = 1, emp_c_code = 'EMP-001' 
WHERE emp_c_name = 'النظام';

UPDATE users 
SET emp_i_id = 1, failed_i_attempts = 0 
WHERE user_i_id = 1;

insert into agent_mast(agent_i_id,agent_c_name) values(1,'النظام');
insert into employee_mast(emp_i_id,emp_c_code,emp_i_SEQNO, emp_c_name,emp_c_add1,emp_d_doj,emp_f_basic,agent_i_id) values(1,'EMP-001',1,'النظام','DJB','1998-01-01',0,1);



