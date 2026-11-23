-- ============================================================
-- joud erp — joud-trade plugin schema
-- ============================================================
-- الجداول الموجودة مسبقاً (في erp_database_schema_fixed.sql):
--   parties, invoices, invoice_items, payments
-- التحسينات المضافة:
--   alter table parties  — إضافة حقول
--   alter table invoices — إضافة حقول
-- الجداول الجديدة:
--   invoice_sequence     — تسلسل أرقام الفواتير لكل فرع
--   price_list           — قوائم الأسعار
--   price_list_item      — تفاصيل قوائم الأسعار
--   stock_movements      — حركات المخزون (مرتبطة بالفواتير)
-- ============================================================

-- ============================================================
-- 1. تحسين جدول parties
-- ============================================================
create table parties (
  parties_i_id bigint primary key,
  -- الرقم الفريد للعميلة  
  parties_c_code varchar(100) unique not null,
  -- رمز العميل  
  parties_c_name varchar(255) not null,
  -- اسم العميل
  -- customer 'customer' | 'supplier' | 'both'
  parties_c_type varchar(20) not null default 'customer',
  -- نوع العميل     -- 'customer' | 'supplier' | 'both'
  parties_c_address varchar(500),
-- عنوان العميل     
  parties_c_phone varchar(50),
-- رقم هاتف العميل  
  parties_c_email varchar(100),
-- بريد العميل    
  parties_c_tax_number varchar(100),
-- رقم الضريبة    
  act_i_id          bigint    references account_mast(act_i_id),
  --الحساب المرتبط    
  parties_f_credit_limit numeric(23,5) default 0,
-- حد الائتمان
  parties_i_pay_terms    integer    default 0,
  -- حالة الائتمان: a=مسموح, h=موقوف, b=محجوب
  parties_c_credit_sts   char(1)      default 'a'constraint constraint p_credit_sts_check check (parties_c_credit_sts in ('a', 'h', 'b')),
  -- الفئة السعرية
  parties_c_price_cat    varchar(20),
  -- الضريبة الافتراضية
  parties_f_tax_rate     numeric(5,2)  default 0,
  -- المنطقة / المنطقة الجغرافية
  parties_c_region       varchar(100),
  -- موقع الويب
  parties_c_website      varchar(200),
  -- ملاحظات
  parties_c_notes        text,
  -- تاريخ الإنشاء
  parties_d_created      timestamp default current_timestamp
  is_active boolean   default true,
  updated_at timestamp default current_timestamp,
  agent_i_id bigint references agent_mast(agent_i_id),
  user_i_id bigint references users(user_i_id)
);

-- ============================================================
-- 2. تحسين جدول invoices
-- ============================================================
create table invoices (
  -- رقم فريد مرجعى للفاتورة
  invoice_i_id bigint primary key,
  -- رقم الفاتورة
  invoice_number varchar(100) unique not null,
  -- نوع الفاتورةsale | purchase | sale_return | purchase_return
  invoice_c_type varchar(50) not null default 'sale' constraint check (invoice_c_type in ('sale', 'purchase', 'sale_return', 'purchase_return')),
  -- حالة الفاتورةdraft | posted | cancelled
  invoice_c_status varchar(20) not null default 'draft' constraint check (invoice_c_status in ('draft', 'posted', 'cancelled')),
  -- العميل
  party_id bigint references parties(parties_i_id) not null,
  -- المستودع
  warehouse_id bigint references warehouses(ware_i_id) not null,
  -- المندوب
  agent_i_id bigint references agent_mast(agent_i_id) not null,
  -- تاريخ الفاتورة
  invoice_d_date timestamp not null,
    -- الإجمالي الفرعي
  subtotal numeric default 0,
  -- العملة
  currency_i_id bigint references currency_mast(currency_i_id), 
  -- نوع الخصم
  discount_type varchar(20) default 'amount' constraint check (discount_type in ('amount', 'percentage')), 
  -- قيمة الخصم
  discount_value numeric default 0,
  -- تفعيل الضريبة
  tax_enabled smallint default 0,
   -- مبلغ الضريبة
  total numeric default 0,
  -- طريقة الدفع
  payment_method varchar(50) not null,
  -- حالة الدفع
  status varchar(50) default 'posted' constraint check (status in ('draft', 'posted', 'cancelled')),
  -- القيد المحاسبي المولَّد
  jv_i_id            numeric(30),
  -- شروط الدفع بالأيام
  invoice_i_pay_days numeric(5)     default 0,
  -- المندوب
  sales_emp_id       numeric(30)    references employee_mast(emp_i_id),
   -- مرجع فاتورة خارجي (رقم فاتورة المورد مثلاً)
  invoice_c_ref      varchar(100),
  -- تاريخ الاستحقاق
  invoice_d_due      date,
  -- نسبة الضريبة
  invoice_f_tax_rate numeric(5,2)   default 0,
  -- مبلغ الضريبة
  invoice_f_tax_amt  numeric(23,5)  default 0,
  -- قيمة الخصم
  invoice_f_disc_amt numeric(23,5)  default 0,
  -- المبلغ المدفوع
  invoice_f_paid     numeric(23,5)  default 0,
  -- الرصيد المتبقي
  invoice_f_balance  numeric(23,5)  default 0,
  -- القيد المحاسبي المولَّد
  jv_i_id            numeric(30),
  -- شروط الدفع بالأيام
  invoice_i_pay_days numeric(5)     default 0,
  -- المندوب
  sales_emp_id       numeric(30)    references employee_mast(emp_i_id),
  -- الملاحظات
  invoice_c_notes    text
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
-- 3. invoice_sequence — تسلسل أرقام الفواتير
-- ============================================================
create table invoice_sequence
(
  seq_i_id        bigint primary key,
  agent_i_id     bigint    not null references agent_mast(agent_i_id),
  -- نوع الفاتورة: sale, purchase, sale_return, purchase_return
  seq_c_type    varchar(30)  not null,
  seq_i_year    integer     not null,
  -- الرقم الحالي
  seq_i_current numeric(10)    default 0,
  -- البادئة مثل inv-2026-
  seq_c_prefix  varchar(20)  default 'inv',
  constraint uq_seq unique (agent_i_id, seq_c_type, seq_i_year)
);

-- ============================================================
-- 4. price_list — قوائم الأسعار
-- ============================================================
create table price_list
(
  pl_i_id       bigint    primary key,
  agent_i_id    bigint    not null references agent_mast(agent_i_id),
  pl_c_code     varchar(20)  not null,
  pl_c_name     varchar(100) not null,
  currency_i_id  bigint    not null references currency_mast(currency_i_id),
  pl_d_from     timestamp          not null,
  pl_d_to       timestamp,
  pl_c_status   char(1)       default 'a',
  constraint uq_pl unique (agent_i_id, pl_c_code,pl_i_id)
);

-- ============================================================
-- 5. price_list_item — تفاصيل قوائم الأسعار
-- ============================================================
create table price_list_item
(
  pli_i_id      bigint    primary key,
  pl_i_id       bigint    not null references price_list(pl_i_id) on delete cascade,
  prod_i_id     bigint    not null references products(prod_i_id),
  pli_f_price   numeric(23,5)  not null,
  pli_f_disc    numeric(5,2)   default 0,
  constraint uq_pli unique (pl_i_id, prod_i_id)
);

-- ============================================================
-- 6. stock_movements — حركات المخزون (محسَّن)
-- ============================================================
create table stock_movements
(
  move_i_id     bigint    primary key,
  agent_i_id    bigint    not null references agent_mast(agent_i_id),
  prod_i_id     bigint    not null references products(prod_i_id),
  invoice_i_id  bigint    references invoices(invoice_i_id),
  ware_i_id     bigint    references warehouses(ware_i_id) ,
  -- النوع: in=وارد, out=صادر, adj=تسوية, trf=تحويل
  move_c_type   varchar(10)  not null default 'in' check (move_c_type in ('in', 'out', 'adj', 'trf')),
  move_i_qty    numeric(23,5)  not null,
  -- الكمية قبل وبعد الحركة
  move_f_qty_before numeric(23,5) default 0,
  move_f_qty_after  numeric(23,5) default 0,
  move_f_unit_cost  numeric(23,5) default 0,
  currency_i_id     bigint references currency_mast(currency_i_id),
  move_d_date   date          not null,
  move_c_notes  varchar(500),
  created_by    bigint    references users(user_i_id),
  created_at    timestamp     default current_timestamp
);

create index idx_smov_prod  on stock_movements(prod_i_id, move_d_date);
create index idx_smov_agent on stock_movements(agent_i_id, move_d_date);

-- ============================================================
-- 7. views مفيدة
-- ============================================================

-- تقرير المبيعات الشهري
create or replace view v_sales_monthly as
select
  i.agent_i_id,
  extract(year  from i.invoice_d_date)  as sale_year,
  extract(month from i.invoice_d_date)  as sale_month,
  i.currency_i_id,
  count(*)                               as invoice_count,
  sum(i.total)                           as total_sales,
  sum(i.invoice_f_disc_amt)              as total_disc,
  sum(i.invoice_f_tax_amt)               as total_tax,
  sum(i.invoice_f_paid)                  as total_paid,
  sum(i.invoice_f_balance)               as total_balance
from invoices i
where i.invoice_c_type = 'sale'
  and i.status <> 'cancelled'
group by i.agent_i_id,
         extract(year from i.invoice_d_date),
         extract(month from i.invoice_d_date),
         i.currency_i_id;

-- كشف حساب العميل / المورد
create or replace view v_party_statement as
select
  p.parties_i_id,
  p.parties_c_name,
  p.parties_c_type,
  i.invoice_i_id,
  i.invoice_number,
  i.invoice_c_type,
  i.invoice_d_date,
  i.total,
  i.invoice_f_paid,
  i.invoice_f_balance,
  i.status,
  i.agent_i_id,
  i.currency_i_id
from parties p
join invoices i on i.party_id = p.parties_i_id
where i.status <> 'cancelled'
order by p.parties_i_id, i.invoice_d_date;

-- أكثر الأصناف مبيعاً
create or replace view v_top_products as
select
  ii.product_id,
  ii.product_name,
  i.agent_i_id,
  i.currency_i_id,
  sum(ii.quantity)     as total_qty,
  sum(ii.total)        as total_amount,
  count(distinct i.invoice_i_id) as invoice_count
from invoice_items ii
join invoices i on ii.invoice_id = i.invoice_i_id
where i.invoice_c_type = 'sale'
  and i.status <> 'cancelled'
group by ii.product_id, ii.product_name, i.agent_i_id, i.currency_i_id
order by total_amount desc;

-- ============================================================
-- end of trade schema
-- جداول جديدة: 4
-- alter table: 2
-- views: 3
-- ============================================================
