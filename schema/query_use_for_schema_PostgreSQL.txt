
--الخطوة 1: تحويل أسماء الأعمدة (columns) إلى أحرف صغيرة
do $$
declare r record;
begin for r in
select table_name,
    column_name
from information_schema.columns
where table_schema = 'public'
    and column_name <> lower(column_name) -- تجنب جداول النظام والـ views
    and table_name not in (
        select table_name
        from information_schema.views
        where table_schema = 'public'
    ) loop execute 'alter table public."' || r.table_name || '" rename column "' || r.column_name || '" to "' || lower(r.column_name) || '"';
end loop;
end $$;
--الخطوة 2: تحويل أسماء الجداول (tables) إلى أحرف صغيرة
do $$
declare r record;
begin for r in
select table_name
from information_schema.tables
where table_schema = 'public'
    and table_type = 'base table'
    and table_name <> lower(table_name) loop execute 'alter table public."' || r.table_name || '" rename to "' || lower(r.table_name) || '"';
end loop;
end $$;
--1. الاستعلام الأول: فحص أسماء الجداول (tables)
--هذا الاستعلام يبحث عن أي جدول في الـ schema العامة (public) لا يزال يحتوي على حروف كبيرة:
select table_name
from information_schema.tables
where table_schema = 'public'
    and table_type = 'base table'
    and table_name <> lower(table_name);
--2. الاستعلام الثاني: فحص أسماء الأعمدة (columns)
--هذا الاستعلام يبحث في جميع جداولك عن أي عمود (column) لا يزال يحتفظ بحروف كبيرة:
select table_name,
    column_name
from information_schema.columns
where table_schema = 'public'
    and column_name <> lower(column_name) -- تجنب جداول النظام والـ views
    and table_name not in (
        select table_name
        from information_schema.views
        where table_schema = 'public'
    );


do $$
declare r record;
begin for r in
select table_name
from information_schema.tables
where table_schema = 'public'
    and table_type = 'base table' -- تجنب الجداول التي تم تسجيلها مسبقاً في جدول الترقيم
    and table_name not in (
        select seq_name
        from joud_sequence_registry
    ) loop -- إدراج الجدول مع القيمة الأولية 1
insert into joud_sequence_registry (seq_name, next_val)
values (r.table_name, 1);
end loop;
end $$;

--1. أنشئ الجدول المؤقت إذا لم يكن موجوداً
create table if not exists temp_module_config (
    module_id varchar(50) primary key,
    active boolean default true
);
--2. انسخ البيانات من جدولك إلى الجدول المؤقت
-- سيتم نسخ كل الجداول المسجلة، مع الاحتفاظ بالحالة السابقة
insert into temp_module_config (module_id, active)
select module_id,
    active
from joud_system_modules on conflict (module_id) do nothing;
--3. حذف الجدول الأصلي
drop table joud_system_modules;
--4. إعادة إنشاء الجدول بالهيكل الجديد (مع حذف عمود status الإجباري)
create table joud_system_modules (
    module_id varchar(50) primary key,
    -- سيخزن اسم الموديول (مثل accounting)
    active boolean default true -- تم التبديل من varchar إلى boolean
);
--5. إرجاع البيانات من الجدول المؤقت إلى الجدول الجديد
insert into joud_system_modules (module_id, active)
select module_id,
    active
from temp_module_config;
--6. حذف الجدول المؤقت
drop table temp_module_config;