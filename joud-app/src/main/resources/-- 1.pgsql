-- 1. تنظيف السجل القديم أولاً لتجنب تعارض المفتاح الأساسي
DELETE FROM users WHERE user_i_id = 1;

-- 2. إدخال السجل بالطريقة اللائقة بـ بيئة الجافا و PostgreSQL
INSERT INTO users (
    user_i_id, 
    user_c_name, 
    password_c_hash, 
    full_name, 
    users_c_status, 
    email, 
    failed_c_attempts -- تأكد من مطابقة اسم العمود تماماً في جدولك
)
VALUES (
    1, 
    'sa', -- 👈 أحرف صغيرة تماماً لتتطابق مع دالة (toLowerCase) بالجافا
    '$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/OBB65Hg3FRNi9BRKm7jSqcLYmC', -- 👈 هاش BCrypt معتمد (60 حرفاً) لكلمة المرور admin123
    'مدير النظام', 
    'A', 
    'a.rizgalah@gmail.com', 
    0 -- 👈 حماية استباقية من الـ Null Pointer
);

update users set 
user_c_name = lower('sa'),
password_c_hash = '$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/OBB65Hg3FRNi9BRKm7jSqcLYmC'
where user_i_id = 1;

update users set 
user_c_name = 'sa',
password_c_hash = '$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/OBB65Hg3FRNi9BRKm7jSqcLYmC'
where user_i_id = 1;