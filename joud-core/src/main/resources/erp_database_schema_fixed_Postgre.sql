-- ============================================================
-- joud erp system - database schema (postgresql version)
-- date: 2026-05-30 (migrated to postgresql)
-- ============================================================
-- ============================================================
-- group: user admin
-- ============================================================
create table key_generation (
  key_i_id bigint primary key,
  table_c_name varchar(100),
  id_i_gen bigint,
  id_c_remarks varchar(250)
);
create table agent_mast (
  agent_i_id bigint primary key,
  -- الرقم التعريفي الفريد للوكيل
  agent_c_name varchar(100) not null,
  -- اسم الوكيل أو الشركة
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
create table employee_mast (
  emp_i_id bigint primary key,
  emp_i_seqno int not null,
  agent_i_id bigint references agent_mast(agent_i_id),
  design_i_id varchar(10),
  emp_c_name varchar(100) not null,
  emp_c_add1 varchar(100) not null,
  emp_c_code varchar(10) emp_c_add2 varchar(100),
  emp_c_city varchar(25),
  country_c_code varchar(10),
  emp_d_doj timestamp not null,
  emp_d_confirm timestamp,
  emp_d_dob date,
  -- pure date of birth, no time needed
  emp_c_sex varchar(1),
  emp_c_marsts varchar(1),
  emp_d_lstincr timestamp,
  emp_f_basic numeric(23, 5) not null,
  emp_f_lstincramt numeric(23, 5),
  emp_c_eduquali varchar(25),
  emp_d_edudate date,
  emp_c_pfno varchar(15),
  emp_c_esino varchar(15),
  emp_d_entry timestamp,
  emp_c_status varchar(1),
  emp_c_flag varchar(1),
  pay_c_confirmsts varchar(1),
  pay_c_salsts varchar(1),
  pay_c_banktrainsts varchar(1) default 'n',
  pay_f_staffsecdep numeric(23, 5) default 0.00,
  pay_i_heirarchy int,
  emp_c_authlevel varchar(10),
  emp_i_incrno int,
  seq int,
  cust_i_oldno bigint
);
drop table users;
drop table role;
drop table user_roles;
alter table users drop column failed_c_attempts;
alter table users
add column failed_c_attempts bigint default 0;
create table users (
  user_i_id bigint primary key,
  password_c_hash varchar(100) not null,
  user_c_name varchar(100) unique not null,
  full_name varchar(200) default '' email varchar(100) unique not null,
  users_c_status varchar(1) default 'a' not null,
  failed_i_attempts bigint default 0 not null,
  locked_i_until bigint,
  last_login timestamp,
  emp_i_id bigint references employee_mast(emp_i_id),
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
-- ============================================================
-- group: application data model - accounting
-- ============================================================
create table ledger_group (
  group_i_id bigint primary key,
  group_c_desc varchar(100) not null,
  group_c_type varchar(1) not null,
  balance_c_type varchar(1) not null,
  group_c_other varchar(10),
  group_i_sequence int,
  user_i_id bigint references users(user_i_id)
);
create table mainldg_mast (
  ldg_i_id bigint primary key,
  group_i_id bigint references ledger_group(group_i_id),
  ldg_c_desc varchar(100) not null,
  user_i_id bigint references users(user_i_id)
);
create table subldg_mast (
  sub_i_id bigint primary key,
  ldg_i_id bigint references mainldg_mast(ldg_i_id),
  sub_c_ldgdesc varchar(100) not null,
  sub_c_type varchar(1) not null,
  sub_c_revalid varchar(1) not null,
  group_i_id bigint references ledger_group(group_i_id),
  act_c_type varchar(10) not null,
  profit_c_calc varchar(1),
  act_c_class varchar(10),
  act_c_nature varchar(10),
  bud_c_flag varchar(1),
  user_i_id bigint references users(user_i_id)
);
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
-- ============================================================
-- group: kyc data model
-- ============================================================
create table parties (
  parties_i_id bigint primary key,
  parties_c_code varchar(100) unique not null,
  parties_c_name varchar(255) not null,
  parties_c_type varchar(20) not null,
  -- 'customer' | 'supplier' | 'both'
  parties_c_address varchar(500),
  parties_c_phone varchar(50),
  parties_c_email varchar(100),
  parties_c_tax_number varchar(100),
  notes text,
  is_active smallint default 1,
  created_at timestamp default current_timestamp,
  updated_at timestamp default current_timestamp,
  agent_i_id bigint references agent_mast(agent_i_id),
  user_i_id bigint references users(user_i_id)
);
create table account_mast (
  act_i_id bigint primary key,
  sub_i_id bigint references subldg_mast(sub_i_id),
  agent_i_id bigint references agent_mast(agent_i_id),
  act_c_type varchar(10),
  cust_i_no bigint not null,
  currency_i_id bigint references currency_mast(currency_i_id),
  act_c_name varchar(250) not null,
  cust_c_mname varchar(250),
  act_d_opdate timestamp not null,
  act_c_actsts varchar(1) default 'a',
  act_c_odsts varchar(1) default 'y',
  act_i_introid bigint,
  act_i_trbrcode bigint,
  act_d_closdt timestamp,
  closure_c_reason varchar(250),
  act_c_payflag varchar(1),
  intro_c_rem varchar(100),
  modify_i_by bigint references users(user_i_id),
  auth_i_by bigint references users(user_i_id),
  auth_d_date timestamp,
  act_i_kyc bigint,
  parties_i_id bigint references parties(parties_i_id),
  emp_i_id bigint references employee_mast(emp_i_id),
  user_i_id bigint references users(user_i_id)
);
create table sbbal_mast (
  sbbal_i_id bigint primary key,
  act_i_id bigint references account_mast(act_i_id),
  agent_i_id bigint references agent_mast(agent_i_id),
  act_c_type varchar(10),
  cust_i_no bigint,
  currency_i_id bigint references currency_mast(currency_i_id),
  sb_f_clrbal numeric(23, 5),
  sb_f_shabal numeric(23, 5),
  sb_f_rsvbal numeric(23, 5),
  sb_f_odbal numeric(23, 5),
  sb_d_odsts timestamp,
  sb_d_trndate timestamp,
  sb_i_notran int,
  sb_f_intrate numeric(23, 5),
  sb_f_freezeamt numeric(23, 5),
  sb_f_releamt numeric(23, 5),
  cust_i_oldno bigint
);
-- ============================================================
-- group: core data model - settings
-- ============================================================
create table agent_brofile (
  brofile_i_id bigint primary key,
  effect_d_date timestamp not null,
  currency_i_id bigint references currency_mast(currency_i_id),
  act_i_id bigint references account_mast(act_i_id),
  agent_i_id bigint references agent_mast(agent_i_id),
  agent_c_monhol varchar(1),
  agent_c_tuehol varchar(1),
  agent_c_wedhol varchar(1),
  agent_c_thuhol varchar(1),
  agent_c_frihol varchar(1),
  agent_c_sathol varchar(1),
  agent_c_sunhol varchar(1),
  open_d_date timestamp,
  install_d_date timestamp,
  bud_c_avail varchar(1),
  bud_c_type varchar(1),
  br_c_incexp_glhead varchar(10),
  rate_c_type varchar(10),
  ret_i_reminders int,
  version_d_date timestamp,
  version_c_no varchar(10),
  developer_c_name varchar(255),
  developer_c_contact varchar(255),
  company_c_name varchar(255),
  company_c_logo varchar(500),
  company_c_address varchar(500),
  company_c_phone varchar(100),
  company_c_email varchar(100),
  company_tax_number varchar(100),
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
-- ============================================================
-- group: application data model - inventory
-- ============================================================
create table warehouses (
  ware_i_id bigint primary key,
  ware_c_code varchar(100) unique not null,
  ware_c_name varchar(255) not null,
  ware_c_address varchar(500),
  ware_c_manager varchar(255),
  is_active smallint default 1,
  created_at timestamp default current_timestamp,
  user_i_id bigint references users(user_i_id)
);
create table categories (
  categ_i_id bigint primary key,
  categ_c_name varchar(255) not null,
  categ_c_type varchar(50) not null,
  -- 'product' | 'expense' | 'income'
  parties_i_id bigint references parties(parties_i_id),
  created_at timestamp default current_timestamp,
  user_i_id bigint references users(user_i_id)
);
create table units (
  unit_i_id bigint primary key,
  unit_c_name varchar(200) not null,
  unit_short_name varchar(50),
  created_at timestamp default current_timestamp,
  user_i_id bigint references users(user_i_id)
);
create table products (
  prod_i_id bigint primary key,
  prod_c_code varchar(100) unique not null,
  prod_c_name varchar(255) not null,
  categ_i_id bigint references categories(categ_i_id),
  unit_i_id bigint references units(unit_i_id),
  ware_i_id bigint references warehouses(ware_i_id),
  supplier_id bigint references parties(parties_i_id),
  purchase_price numeric default 0,
  currency_i_id bigint references currency_mast(currency_i_id),
  sale_price numeric default 0,
  opening_quantity numeric default 0,
  current_quantity numeric default 0,
  min_quantity numeric default 0,
  is_active smallint default 1,
  notes text,
  created_at timestamp default current_timestamp,
  updated_at timestamp default current_timestamp,
  user_i_id bigint references users(user_i_id)
);
-- ============================================================
-- group: application data model - sales & purchases
-- ============================================================
create table invoices (
  invoice_i_id bigint primary key,
  invoice_number varchar(100) unique not null,
  invoice_c_type varchar(50) not null,
  -- 'sale' | 'purchase' | 'sale_return' | 'purchase_return'
  party_id bigint references parties(parties_i_id),
  warehouse_id bigint references warehouses(ware_i_id),
  agent_i_id bigint references agent_mast(agent_i_id),
  invoice_d_date timestamp not null,
  due_d_date timestamp,
  subtotal numeric default 0,
  currency_i_id bigint references currency_mast(currency_i_id),
  discount_type varchar(20) default 'amount',
  -- 'amount' | 'percentage'
  discount_value numeric default 0,
  discount_amount numeric default 0,
  tax_enabled smallint default 0,
  tax_rate numeric default 0,
  tax_amount numeric default 0,
  total numeric default 0,
  paid_amount numeric default 0,
  remaining_amount numeric default 0,
  payment_method varchar(50) not null,
  -- 'cash' | 'partial' | 'credit'
  status varchar(50) default 'posted',
  -- 'draft' | 'posted' | 'cancelled'
  notes text,
  created_by bigint references users(user_i_id),
  created_at timestamp default current_timestamp,
  updated_at timestamp default current_timestamp
);
create table invoice_items (
  invitem_i_id bigint primary key,
  invoice_id bigint references invoices(invoice_i_id) on delete cascade,
  product_id bigint references products(prod_i_id),
  product_name varchar(255) not null,
  product_code varchar(100),
  unit_name varchar(100),
  agent_i_id bigint references agent_mast(agent_i_id),
  quantity numeric not null,
  unit_price numeric not null,
  currency_i_id bigint references currency_mast(currency_i_id),
  discount_amount numeric default 0,
  total numeric not null,
  notes text,
  created_by bigint references users(user_i_id)
);
create table payments (
  pay_i_id bigint primary key,
  invoice_id bigint references invoices(invoice_i_id),
  party_id bigint references parties(parties_i_id),
  pay_c_type varchar(50) not null,
  -- 'receipt' | 'payment'
  pay_i_amount numeric not null,
  agent_i_id bigint references agent_mast(agent_i_id),
  currency_i_id bigint references currency_mast(currency_i_id),
  pay_d_date timestamp not null,
  pay_c_method varchar(50) default 'cash',
  pay_reference varchar(255),
  notes text,
  created_at timestamp default current_timestamp,
  created_by bigint references users(user_i_id)
);
-- ============================================================
-- group: application data model - treasury & accounting entries
-- ============================================================
create table treasury_transactions (
  trn_i_id bigint primary key,
  trn_c_type varchar(20) not null,
  -- 'in' | 'out'
  category_id bigint references categories(categ_i_id),
  act_i_id bigint references account_mast(act_i_id),
  party_id bigint,
  invoice_id bigint,
  trn_i_amount numeric not null,
  act_c_type varchar(10),
  currency_i_id bigint references currency_mast(currency_i_id),
  cust_i_no bigint,
  trn_d_date timestamp not null,
  description varchar(500),
  trn_c_sts varchar(1),
  sqe_i_code int,
  trn_c_chqno varchar(20),
  trn_d_chqdate timestamp,
  trn_f_balance numeric(23, 5),
  trn_c_balsts varchar(2),
  trn_c_to_type varchar(10),
  trn_i_to_no bigint,
  uti bigint default 0 not null,
  reference varchar(255),
  created_by bigint references users(user_i_id),
  created_at timestamp default current_timestamp,
  agent_i_id bigint references agent_mast(agent_i_id)
);
create table batch (
  bbd_i_id bigint primary key,
  agent_i_id bigint references agent_mast(agent_i_id),
  bbd_d_date timestamp not null,
  bbd_i_slno bigint not null,
  bbd_c_drcr varchar(2) not null,
  act_c_type varchar(10) not null,
  cust_i_no bigint,
  currency_i_id bigint references currency_mast(currency_i_id),
  bbd_f_amt numeric(23, 5) not null,
  bbd_c_chqno varchar(20),
  bbd_c_chqdt timestamp,
  bbd_c_sts varchar(2),
  oper_i_id bigint,
  bbd_c_usrid bigint references users(user_i_id),
  bbd_c_payee varchar(200),
  bbd_f_odamt numeric(23, 5),
  bbd_c_mode varchar(4),
  uti bigint default 0 not null,
  bbd_d_datetime timestamp default current_timestamp
);
create table day_ledg (
  trn_i_no bigint primary key,
  agent_i_id bigint references agent_mast(agent_i_id),
  trn_d_date timestamp not null,
  act_c_type varchar(10) not null,
  cust_i_no bigint,
  trn_c_narr varchar(250),
  currency_i_id bigint references currency_mast(currency_i_id),
  trn_f_trnamt numeric(23, 5) not null,
  trn_c_drcr varchar(2) not null,
  trt_c_type varchar(10),
  trn_c_sts varchar(1),
  seq_i_code double precision,
  trn_c_chqno varchar(10),
  trn_d_chqdate timestamp,
  trn_c_usrid bigint references users(user_i_id),
  trn_f_balance numeric(20, 2),
  trn_c_balsts varchar(2),
  trn_c_totype varchar(10),
  trn_i_tono double precision,
  uti bigint default 0 not null,
  screen_name varchar(40) default 'no screen name' not null
);
-- ============================================================
-- group: application data model - stock movements
-- ============================================================
create table stock_movements (
  move_i_id bigint primary key,
  product_id bigint references products(prod_i_id),
  invoice_id bigint references invoices(invoice_i_id),
  move_c_type varchar(20) not null,
  -- 'in' | 'out' | 'adjustment'
  move_quantity numeric not null,
  move_unit_price numeric,
  currency_i_id bigint references currency_mast(currency_i_id),
  warehouse_id bigint references warehouses(ware_i_id),
  move_d_date timestamp not null,
  notes text,
  agent_i_id bigint references agent_mast(agent_i_id),
  created_at timestamp default current_timestamp,
  created_by bigint references users(user_i_id)
);
-- ============================================================
-- end of schema
-- total tables: 27
-- ============================================================