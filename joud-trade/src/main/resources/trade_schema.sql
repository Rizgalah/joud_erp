-- ============================================================
-- Joud ERP — joud-trade Plugin Schema
-- ============================================================
-- الجداول الموجودة مسبقاً (في erp_database_schema_fixed.sql):
--   parties, invoices, invoice_items, payments
-- التحسينات المضافة:
--   ALTER TABLE parties  — إضافة حقول
--   ALTER TABLE invoices — إضافة حقول
-- الجداول الجديدة:
--   INVOICE_SEQUENCE     — تسلسل أرقام الفواتير لكل فرع
--   PRICE_LIST           — قوائم الأسعار
--   PRICE_LIST_ITEM      — تفاصيل قوائم الأسعار
--   STOCK_MOVEMENTS      — حركات المخزون (مرتبطة بالفواتير)
-- ============================================================

-- ============================================================
-- 1. تحسين جدول parties
-- ============================================================
ALTER TABLE parties ADD (
  -- رصيد الحساب الافتراضي
  ACT_I_ID         NUMBER(30)    REFERENCES ACCOUNT_MAST(ACT_I_ID),
  -- حد الائتمان
  parties_f_credit_limit NUMBER(23,5) DEFAULT 0,
  -- شروط الدفع بالأيام
  parties_i_pay_terms    NUMBER(5)    DEFAULT 0,
  -- حالة الائتمان: A=مسموح, H=موقوف, B=محجوب
  parties_c_credit_sts   CHAR(1)      DEFAULT 'A',
  -- الفئة السعرية
  parties_c_price_cat    VARCHAR2(20),
  -- الضريبة الافتراضية
  parties_f_tax_rate     NUMBER(5,2)  DEFAULT 0,
  -- المنطقة / المنطقة الجغرافية
  parties_c_region       VARCHAR2(100),
  -- موقع الويب
  parties_c_website      VARCHAR2(200),
  -- ملاحظات
  parties_c_notes        CLOB,
  -- تاريخ الإنشاء
  parties_d_created      DATE DEFAULT SYSDATE
);

-- ============================================================
-- 2. تحسين جدول invoices
-- ============================================================
ALTER TABLE invoices ADD (
  -- مرجع فاتورة خارجي (رقم فاتورة المورد مثلاً)
  invoice_c_ref      VARCHAR2(100),
  -- تاريخ الاستحقاق
  invoice_d_due      DATE,
  -- نسبة الضريبة
  invoice_f_tax_rate NUMBER(5,2)   DEFAULT 0,
  -- مبلغ الضريبة
  invoice_f_tax_amt  NUMBER(23,5)  DEFAULT 0,
  -- قيمة الخصم
  invoice_f_disc_amt NUMBER(23,5)  DEFAULT 0,
  -- المبلغ المدفوع
  invoice_f_paid     NUMBER(23,5)  DEFAULT 0,
  -- الرصيد المتبقي
  invoice_f_balance  NUMBER(23,5)  DEFAULT 0,
  -- القيد المحاسبي المولَّد
  JV_I_ID            NUMBER(30),
  -- شروط الدفع بالأيام
  invoice_i_pay_days NUMBER(5)     DEFAULT 0,
  -- المندوب
  SALES_EMP_ID       NUMBER(30)    REFERENCES EMPLOYEE_MAST(EMP_i_id),
  -- الملاحظات
  invoice_c_notes    CLOB
);

-- ============================================================
-- 3. INVOICE_SEQUENCE — تسلسل أرقام الفواتير
-- ============================================================
CREATE TABLE INVOICE_SEQUENCE
(
  SEQ_I_ID      NUMBER(30)    PRIMARY KEY,
  AGENT_I_ID    NUMBER(30)    NOT NULL REFERENCES AGENT_MAST(AGENT_I_ID),
  -- نوع الفاتورة: sale, purchase, sale_return, purchase_return
  SEQ_C_TYPE    VARCHAR2(30)  NOT NULL,
  SEQ_I_YEAR    NUMBER(4)     NOT NULL,
  -- الرقم الحالي
  SEQ_I_CURRENT NUMBER(10)    DEFAULT 0,
  -- البادئة مثل INV-2026-
  SEQ_C_PREFIX  VARCHAR2(20)  DEFAULT 'INV',
  CONSTRAINT uq_seq UNIQUE (AGENT_I_ID, SEQ_C_TYPE, SEQ_I_YEAR)
);

-- ============================================================
-- 4. PRICE_LIST — قوائم الأسعار
-- ============================================================
CREATE TABLE PRICE_LIST
(
  PL_I_ID       NUMBER(30)    PRIMARY KEY,
  AGENT_I_ID    NUMBER(30)    NOT NULL REFERENCES AGENT_MAST(AGENT_I_ID),
  PL_C_CODE     VARCHAR2(20)  NOT NULL,
  PL_C_NAME     VARCHAR2(100) NOT NULL,
  CURRENCY_I_ID NUMBER(30)    NOT NULL REFERENCES CURRENCY_MAST(CURRENCY_I_ID),
  PL_D_FROM     DATE          NOT NULL,
  PL_D_TO       DATE,
  PL_C_STATUS   CHAR(1)       DEFAULT 'A',
  CONSTRAINT uq_pl UNIQUE (AGENT_I_ID, PL_C_CODE)
);

-- ============================================================
-- 5. PRICE_LIST_ITEM — تفاصيل قوائم الأسعار
-- ============================================================
CREATE TABLE PRICE_LIST_ITEM
(
  PLI_I_ID      NUMBER(30)    PRIMARY KEY,
  PL_I_ID       NUMBER(30)    NOT NULL REFERENCES PRICE_LIST(PL_I_ID) ON DELETE CASCADE,
  PROD_I_ID     NUMBER(30)    NOT NULL REFERENCES products(prod_i_id),
  PLI_F_PRICE   NUMBER(23,5)  NOT NULL,
  PLI_F_DISC    NUMBER(5,2)   DEFAULT 0,
  CONSTRAINT uq_pli UNIQUE (PL_I_ID, PROD_I_ID)
);

-- ============================================================
-- 6. STOCK_MOVEMENTS — حركات المخزون (محسَّن)
-- ============================================================
CREATE TABLE STOCK_MOVEMENTS
(
  MOVE_I_ID     NUMBER(30)    PRIMARY KEY,
  AGENT_I_ID    NUMBER(30)    NOT NULL REFERENCES AGENT_MAST(AGENT_I_ID),
  PROD_I_ID     NUMBER(30)    NOT NULL REFERENCES products(prod_i_id),
  INVOICE_I_ID  NUMBER(30)    REFERENCES invoices(invoice_i_id),
  WARE_I_ID     NUMBER(30)    REFERENCES warehouses(ware_i_id),
  -- النوع: IN=وارد, OUT=صادر, ADJ=تسوية, TRF=تحويل
  MOVE_C_TYPE   VARCHAR2(10)  NOT NULL,
  MOVE_I_QTY    NUMBER(23,5)  NOT NULL,
  -- الكمية قبل وبعد الحركة
  MOVE_F_QTY_BEFORE NUMBER(23,5) DEFAULT 0,
  MOVE_F_QTY_AFTER  NUMBER(23,5) DEFAULT 0,
  MOVE_F_UNIT_COST  NUMBER(23,5) DEFAULT 0,
  CURRENCY_I_ID     NUMBER(30) REFERENCES CURRENCY_MAST(CURRENCY_I_ID),
  MOVE_D_DATE   DATE          NOT NULL,
  MOVE_C_NOTES  VARCHAR2(500),
  CREATED_BY    NUMBER(30)    REFERENCES users(user_i_id),
  CREATED_AT    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_smov_prod  ON STOCK_MOVEMENTS(PROD_I_ID, MOVE_D_DATE);
CREATE INDEX idx_smov_agent ON STOCK_MOVEMENTS(AGENT_I_ID, MOVE_D_DATE);

-- ============================================================
-- 7. Views مفيدة
-- ============================================================

-- تقرير المبيعات الشهري
CREATE OR REPLACE VIEW V_SALES_MONTHLY AS
SELECT
  i.AGENT_I_ID,
  EXTRACT(YEAR  FROM i.invoice_d_date)  AS SALE_YEAR,
  EXTRACT(MONTH FROM i.invoice_d_date)  AS SALE_MONTH,
  i.CURRENCY_I_ID,
  COUNT(*)                               AS INVOICE_COUNT,
  SUM(i.total)                           AS TOTAL_SALES,
  SUM(i.invoice_f_disc_amt)              AS TOTAL_DISC,
  SUM(i.invoice_f_tax_amt)               AS TOTAL_TAX,
  SUM(i.invoice_f_paid)                  AS TOTAL_PAID,
  SUM(i.invoice_f_balance)               AS TOTAL_BALANCE
FROM invoices i
WHERE i.invoice_c_type = 'sale'
  AND i.status <> 'cancelled'
GROUP BY i.AGENT_I_ID,
         EXTRACT(YEAR FROM i.invoice_d_date),
         EXTRACT(MONTH FROM i.invoice_d_date),
         i.CURRENCY_I_ID;

-- كشف حساب العميل / المورد
CREATE OR REPLACE VIEW V_PARTY_STATEMENT AS
SELECT
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
  i.AGENT_I_ID,
  i.CURRENCY_I_ID
FROM parties p
JOIN invoices i ON i.party_id = p.parties_i_id
WHERE i.status <> 'cancelled'
ORDER BY p.parties_i_id, i.invoice_d_date;

-- أكثر الأصناف مبيعاً
CREATE OR REPLACE VIEW V_TOP_PRODUCTS AS
SELECT
  ii.product_id,
  ii.product_name,
  i.AGENT_I_ID,
  i.CURRENCY_I_ID,
  SUM(ii.quantity)     AS TOTAL_QTY,
  SUM(ii.total)        AS TOTAL_AMOUNT,
  COUNT(DISTINCT i.invoice_i_id) AS INVOICE_COUNT
FROM invoice_items ii
JOIN invoices i ON ii.invoice_id = i.invoice_i_id
WHERE i.invoice_c_type = 'sale'
  AND i.status <> 'cancelled'
GROUP BY ii.product_id, ii.product_name, i.AGENT_I_ID, i.CURRENCY_I_ID
ORDER BY TOTAL_AMOUNT DESC;

-- ============================================================
-- END OF TRADE SCHEMA
-- جداول جديدة: 4
-- ALTER TABLE: 2
-- Views: 3
-- ============================================================
