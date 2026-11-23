

drop table users;
drop table role;
drop table user_roles;

create table users (
  user_i_id bigint primary key,
  password_c_hash varchar(100) not null,
  user_c_name varchar(100) unique not null,
  full_name varchar(200) default '' ,
  email varchar(100) unique not null,
  users_c_status varchar(1) default 'a' not null,
  failed_i_attempts bigint default 0 not null,
  locked_i_until bigint,
  last_login timestamp,
  emp_i_id bigint default 0,
  agent_i_id bigint references agent_mast(agent_i_id),
  created_at timestamp default current_timestamp,
  language_c_code varchar(10) default 'ar',
  password_c_hash_change_date timestamp(6) default now(),
  is_lockout_enabled boolean default true,
  is_two_factor_enabled boolean default false
);
-- bcrypt hash لكلمة المرور "admin123" (cost=12)
--insert into users (user_i_id, user_c_name, password_c_hash, full_name,users_c_status,email)
--values (1, 'sa',
--  '$2a$12$lqv3c1yqbwvhxkd0lhakcoyz6ttxmqjqhn8/lwtqa6hg5b8dmhqe',
-- 'مدير النظام', 'a','a.rizgalah@gmail.com');
create table role (
  role_i_id bigint primary key,
  role_c_name varchar(30) unique,
  role_c_desc varchar(100)
);
insert into role (role_i_id, role_c_name, role_c_desc)
values (1, 'admin', 'مدير النظام — كامل الصلاحيات');
create table user_roles (
  user_i_id bigint references users(user_i_id),
  role_i_id bigint references role(role_i_id),
  assigned_d_at timestamp
);
insert into user_roles (user_i_id, role_i_id)
values (1, 1);
create table role_permissions (
  role_i_id bigint not null,
  permission_code varchar(100) not null,
  primary key (role_i_id, permission_code),
  foreign key (role_i_id) references role(role_i_id) on delete cascade
);
insert into role_permissions (role_i_id, permission_code)
values (1, 'all');
-- ============================================================
-- group: security
-- ============================================================
create table sessions (
  sess_i_id bigint primary key,
  user_i_id bigint references users(user_i_id),
  token_hash varchar(250),
  ip_address varchar(250),
  expires_at timestamp,
  revoked timestamp
);
create table login_attempts (
  log_i_id bigint primary key,
  ip_address varchar(250),
  email varchar(100) references users(email),
  attempted_at timestamp,
  success varchar(1) default 'y'
);
create table reset_tokens (
  token_hash varchar(250),
  user_i_id bigint references users(user_i_id),
  expires_at timestamp,
  used timestamp
);
-- ============================================================
-- group: audit
-- ============================================================
create table audit_log (
  audit_i_id bigint primary key,
  table_c_name varchar(100),
  record_id bigint,
  action varchar(10),
  old_values varchar(250),
  new_values varchar(250),
  actor_id bigint
);
-- ============================================================
-- group: maintenance
-- ============================================================
create table backup_history (
  backup_i_id bigint primary key,
  file_c_name varchar(100),
  file_i_size bigint,
  check_c_sum varchar(1) default 'n',
  backup_c_status varchar(1) default 'n',
  triggered_by varchar(100),
  created_at timestamp
);
create table security_events (
  event_i_id bigint primary key,
  event_c_type varchar(10),
  event_c_ip varchar(250),
  user_i_id bigint references users(user_i_id),
  event_c_detail varchar(250),
  event_c_severity varchar(250),
  created_at timestamp
);
create table joud_sequence_registry (
    seq_name varchar(100) primary key,
    -- سيخزن اسم الجدول (مثل gl_sub_ledger)
    next_val numeric(19, 0) not null -- القيمة الحالية للمعير
);
create table joud_system_modules (
    module_id varchar(50) primary key,
    -- مثل "accounting"
    module_name varchar(100) not null,
    version varchar(20) not null,
    installed_at timestamp default current_timestamp,
    status varchar(15) default 'active'
);
-- 1. إعداد نوع الحالة (enum)
create type email_status as enum ('pending', 'success', 'failed');
-- 2. جدول سجل البريد الإلكتروني
create table sent_emails_log (
    id uuid primary key default gen_random_uuid(),
    sender varchar(255) not null,
    recipient varchar(255) not null,
    email_type varchar(50) not null,
    -- e.g., 'password_reset', 'invoice_alert'
    subject varchar(500) not null,
    sent_at timestamptz not null default current_timestamp,
    status email_status not null default 'pending',
    error_message text
);
-- 3. الفهارس (indexes) لتحسين أداء الاستعلام للتدقيق والمراقبة
create index idx_sent_emails_recipient on audit.sent_emails_log(recipient);
create index idx_sent_emails_sent_at on audit.sent_emails_log(sent_at desc);
create index idx_sent_emails_status on audit.sent_emails_log(status);
-- إنشاء جدول توكنات إعادة تعيين كلمة المرور
create table password_reset_tokens (
    token_i_id bigserial primary key,
    user_i_id bigint not null,
    token_c_value varchar(100) not null unique,
    token_d_expires timestamp not null,
    token_b_used boolean not null default false,
    created_at timestamp not null default current_timestamp
);
-- فهرس لتسريع البحث عن طريق التوكن (مهم جداً لأن الـ bean يبحث بالتوكن)
create index idx_reset_token_value on password_reset_tokens (token_c_value);
-- فهرس لتسريع البحث عن التوكنات الخاصة بمستخدم معين (لإبطال التوكنات القديمة)
create index idx_reset_token_user on password_reset_tokens (user_i_id);
-- ============================================================
-- group: core data model - settings
-- ============================================================
create table currency_mast (
  currency_i_id bigint primary key,
  currency_c_desc varchar(100) not null,
  currency_c_symbol varchar(10),
  currency_c_lowcode varchar(10),
  currency_c_lowdesc varchar(50),
  currency_f_reten numeric(23, 5),
  swift_c_desc varchar(100),
  user_i_id bigint references users(user_i_id)
);
create table country_mast
(
  country_i_id  bigint primary key,
  country_c_desc  varchar(100) not null,
  currency_i_id bigint references currency_mast(currency_i_id),
  group_c_code    varchar(10),
  country_f_limit numeric(20,2),
  user_i_id    bigint references users(user_i_id)
);
create table agent_mast (
  agent_i_id bigint primary key,
  -- الرقم التعريفي الفريد للوكيل
  agent_c_name varchar(100) not null,
  -- اسم الوكيل أو الشركة
  currency_i_id bigint references currency_mast(currency_i_id),
  -- العملة
  agent_c_sunhol varchar(1),
   -- اجازه يوم الاحد
  agent_c_monhol varchar(1),
  -- اجازه يوم الاثنين
  agent_c_tuehol varchar(1),
  -- اجازه يوم الثلاثاء
  agent_c_wedhol varchar(1),
  -- اجازه يوم الاربعاء
  agent_c_thuhol varchar(1),
  -- اجازه يوم الخميس
  agent_c_frihol varchar(1),
  -- اجازه يوم الجمعة
  agent_c_sathol varchar(1),
  -- اجازه يوم السبت
  open_d_date timestamp,
  -- تاريخ فتح الحساب
  install_d_date timestamp,
  -- تاريخ التثبيت
  bud_c_avail varchar(1),
  -- ميزانية متاحة
  bud_c_type varchar(1),
  -- نوع الميزانية
  company_reg_no varchar(50),
  -- رقم السجل التجاري
  tax_number varchar(50),
  -- الرقم الضريبي
  -- بيانات الاتصال والموقع
  contact_person varchar(100),
  -- اسم الشخص المسؤول للتواصل
  phone_number varchar(20),
  -- رقم الهاتف
  email_address varchar(100),
  -- البريد الإلكتروني
  country varchar(50),
  -- الدولة
  city varchar(50),
  -- المدينة
  full_address text,
  -- العنوان التفصيلي لمقر الوكيل
  -- معلومات التوزيع والمالية
  region_covered varchar(100),
  -- المنطقة الجغرافية المسموح له بالتوزيع فيها
  credit_limit decimal(15, 2) default 0.00,
  -- الحد الائتماني (أقصى قيمة بضائع بالآجل)
  commission_rate decimal(5, 2) default 0.00,
  -- نسبة عمولة الوكيل (إن وجدت)
  -- الحالة والمراقبة
  is_active boolean default true,
  -- حالة الوكيل (نشط / موقف مؤقتاً)
  created_at timestamp default current_timestamp -- تاريخ ووقت تسجيل الوكيل في النظام
);
create table company_brofile (
  brofile_i_id bigint primary key,
  effect_d_date timestamp not null,
  company_c_name varchar(255),
  company_c_logo varchar(500),
  company_c_address varchar(500),
  company_c_phone varchar(100),
  company_c_email varchar(100),
  company_tax_number varchar(100),
  currency_i_id bigint references currency_mast(currency_i_id),
  br_c_incexp_glhead varchar(10),
  rate_c_type varchar(10),
  ret_i_reminders int,
  version_d_date timestamp,
  version_c_no varchar(10),
  developer_c_name varchar(255),
  developer_c_contact varchar(255),
  invoice_prefix_sales varchar(20) default 'inv',
  invoice_prefix_purchases varchar(20) default 'pur',
  invoice_prefix_sales_return varchar(20) default 'sr',
  invoice_prefix_purchase_return varchar(20) default 'pr',
  tax_enabled smallint default 0,
  tax_rate numeric default 14.0,
  dark_mode smallint default 0,
  cash_c_acc varchar(10),
  session_time bigint default 1800,
  user_i_id bigint references users(user_i_id)
);

