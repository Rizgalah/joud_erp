-- ============================================================
-- Joud ERP System - Database Schema (Corrected Version)
-- Date: 2026-05-30
-- Corrections applied:
--   1. stock_movements: products(id) → products(prod_i_id)
--   2. parties: missing comma before user_i_id
--   3. SBBAL_MAST: missing comma after ACT_I_ID
--   4. AGENT_BROFILE: missing comma before SESSION_TIME
--   5. invoices: extra closing parenthesis in party_id reference
--   6. Removed stray markdown code fences (```) from schema
-- ============================================================

-- ============================================================
-- GROUP: User Admin
-- ============================================================

CREATE TABLE KEY_GENERATION
(
  KEY_I_ID     NUMBER(30)    PRIMARY KEY,
  TABLE_C_NAME VARCHAR2(100),
  ID_I_GEN     NUMBER(30),
  ID_C_REMARKS VARCHAR2(250)
);

CREATE TABLE AGENT_MAST
(
  AGENT_I_ID   NUMBER(30)    PRIMARY KEY,
  AGENT_C_NAME VARCHAR2(100) NOT NULL,
  user_i_id    NUMBER(30)    REFERENCES users(user_i_id)
);

CREATE TABLE EMPLOYEE_MAST
(
  EMP_i_id           NUMBER(30)    PRIMARY KEY,
  EMP_I_SEQNO        NUMBER(10)    NOT NULL,
  AGENT_I_ID         NUMBER(30)    REFERENCES AGENT_MAST(AGENT_I_ID),
  DESIGN_i_id        VARCHAR2(10),
  EMP_C_NAME         VARCHAR2(100) NOT NULL,
  EMP_C_ADD1         VARCHAR2(100) NOT NULL,
  EMP_C_ADD2         VARCHAR2(100),
  EMP_C_CITY         VARCHAR2(25),
  COUNTRY_C_CODE     VARCHAR2(10),
  EMP_D_DOJ          DATE          NOT NULL,
  EMP_D_CONFIRM      DATE,
  EMP_D_DOB          DATE,
  EMP_C_SEX          CHAR(1),
  EMP_C_MARSTS       CHAR(1),
  EMP_D_LSTINCR      DATE,
  EMP_F_BASIC        NUMBER(23,5)  NOT NULL,
  EMP_F_LSTINCRAMT   NUMBER(23,5),
  EMP_C_EDUQUALI     VARCHAR2(25),
  EMP_D_EDUDATE      DATE,
  EMP_C_PFNO         VARCHAR2(15),
  EMP_C_ESINO        VARCHAR2(15),
  EMP_D_ENTRY        DATE,
  EMP_C_STATUS       CHAR(1),
  EMP_C_FLAG         CHAR(1),
  PAY_C_CONFIRMSTS   CHAR(1),
  PAY_C_SALSTS       CHAR(1),
  PAY_C_BANKTRAINSTS CHAR(1)       DEFAULT 'N',
  PAY_F_STAFFSECDEP  NUMBER(23,5)  DEFAULT 0.00,
  PAY_I_HEIRARCHY    NUMBER(15),
  EMP_C_AUTHLEVEL    VARCHAR2(10),
  EMP_I_INCRNO       NUMBER(3),
  SEQ                NUMBER(10),
  CUST_I_OLDNO       NUMBER(15)
);

CREATE TABLE users
(
  user_i_id        NUMBER(30)    PRIMARY KEY,
  password_c_hash  VARCHAR2(100) NOT NULL,
  user_c_name      VARCHAR2(100) UNIQUE NOT NULL,
  email            VARCHAR2(100) UNIQUE NOT NULL,
  users_c_status   CHAR(1)       DEFAULT 'A' NOT NULL,
  failed_c_attempts VARCHAR2(10),
  locked_i_until   NUMBER(10),
  last_login       DATE,
  EMP_i_id         NUMBER(30)    REFERENCES EMPLOYEE_MAST(EMP_i_id),
  AGENT_I_ID       NUMBER(30)    REFERENCES AGENT_MAST(AGENT_I_ID),
  created_at       DATE          DEFAULT SYSDATE
);

CREATE TABLE role
(
  role_i_id   NUMBER(30)   PRIMARY KEY,
  role_c_name VARCHAR2(30) UNIQUE,
  role_c_desc VARCHAR2(100)
);

CREATE TABLE user_roles
(
  user_i_id   NUMBER(30) REFERENCES users(user_i_id),
  role_i_id   NUMBER(30) REFERENCES role(role_i_id),
  assigned_d_at DATE
);

-- ============================================================
-- GROUP: Security
-- ============================================================

CREATE TABLE sessions
(
  Sess_i_id  NUMBER(30)    PRIMARY KEY,
  user_i_id  NUMBER(30)    REFERENCES users(user_i_id),
  token_hash VARCHAR2(250),
  ip_address VARCHAR2(250),
  expires_at DATE,
  revoked    DATE
);

CREATE TABLE login_attempts
(
  log_i_id     NUMBER(30)    PRIMARY KEY,
  ip_address   VARCHAR2(250),
  email        VARCHAR2(100) REFERENCES users(email),
  attempted_at DATE,
  success      CHAR(1)       DEFAULT 'Y'
);

CREATE TABLE reset_tokens
(
  token_hash VARCHAR2(250),
  user_i_id  NUMBER(30) REFERENCES users(user_i_id),
  expires_at DATE,
  used       DATE
);

-- ============================================================
-- GROUP: Audit
-- ============================================================

CREATE TABLE audit_log
(
  audit_i_id   NUMBER(30)    PRIMARY KEY,
  table_c_name VARCHAR2(100),
  record_id    NUMBER(30),
  action       VARCHAR2(10),
  old_values   VARCHAR2(250),
  new_values   VARCHAR2(250),
  actor_id     NUMBER(30)
);

-- ============================================================
-- GROUP: Maintenance
-- ============================================================

CREATE TABLE backup_history
(
  backup_i_id    NUMBER(30)    PRIMARY KEY,
  file_c_name    VARCHAR2(100),
  file_i_size    NUMBER(30),
  check_c_sum    CHAR(1)       DEFAULT 'N',
  backup_c_status CHAR(1)      DEFAULT 'N',
  triggered_by   VARCHAR2(100),
  created_at     DATE
);

CREATE TABLE security_events
(
  event_i_id       NUMBER(30)    PRIMARY KEY,
  event_c_type     VARCHAR2(10),
  event_c_ip       VARCHAR2(250),
  user_i_id        NUMBER(30)    REFERENCES users(user_i_id),
  event_c_detail   VARCHAR2(250),
  event_c_severity VARCHAR2(250),
  created_at       DATE
);

-- ============================================================
-- GROUP: Application Data Model - Accounting
-- ============================================================

CREATE TABLE LEDGER_GROUP
(
  GROUP_I_ID       NUMBER(30)    PRIMARY KEY,
  GROUP_C_DESC     VARCHAR2(100) NOT NULL,
  GROUP_C_TYPE     CHAR(1)       NOT NULL,
  BALANCE_C_TYPE   CHAR(1)       NOT NULL,
  GROUP_C_OTHER    VARCHAR2(10),
  GROUP_I_SEQUENCE NUMBER(4),
  user_i_id        NUMBER(30)    REFERENCES users(user_i_id)
);

CREATE TABLE MAINLDG_MAST
(
  LDG_I_ID   NUMBER(30)    PRIMARY KEY,
  GROUP_I_ID NUMBER(30)    REFERENCES LEDGER_GROUP(GROUP_I_ID),
  LDG_C_DESC VARCHAR2(100) NOT NULL,
  user_i_id  NUMBER(30)    REFERENCES users(user_i_id)
);

CREATE TABLE SUBLDG_MAST
(
  SUB_I_ID      NUMBER(30)   PRIMARY KEY,
  LDG_I_ID      NUMBER(30)   REFERENCES MAINLDG_MAST(LDG_I_ID),
  SUB_C_LDGDESC VARCHAR2(100) NOT NULL,
  SUB_C_TYPE    CHAR(1)      NOT NULL,
  SUB_C_REVALID CHAR(1)      NOT NULL,
  GROUP_I_ID    NUMBER(30)   REFERENCES LEDGER_GROUP(GROUP_I_ID),
  ACT_C_TYPE    VARCHAR2(10) NOT NULL,
  PROFIT_C_CALC CHAR(1),
  ACT_C_CLASS   VARCHAR2(10),
  ACT_C_NATURE  VARCHAR2(10),
  BUD_C_FLAG    CHAR(1),
  user_i_id     NUMBER(30)   REFERENCES users(user_i_id)
);

CREATE TABLE CURRENCY_MAST
(
  CURRENCY_I_ID      NUMBER(30)    PRIMARY KEY,
  CURRENCY_C_DESC    VARCHAR2(100) NOT NULL,
  CURRENCY_C_SYMBOL  VARCHAR2(10),
  CURRENCY_C_LOWCODE VARCHAR2(10),
  CURRENCY_C_LOWDESC VARCHAR2(50),
  CURRENCY_F_RETEN   NUMBER(23,5),
  SWIFT_C_DESC       VARCHAR2(100),
  user_i_id          NUMBER(30)    REFERENCES users(user_i_id)
);

-- ============================================================
-- GROUP: KYC Data Model
-- ============================================================

CREATE TABLE parties
(
  parties_i_id         NUMBER(30)    PRIMARY KEY,
  parties_c_code       VARCHAR2(100) UNIQUE NOT NULL,
  parties_c_name       VARCHAR2(255) NOT NULL,
  parties_c_type       VARCHAR2(20)  NOT NULL,   -- 'customer' | 'supplier' | 'both'
  parties_c_address    VARCHAR2(500),
  parties_c_phone      VARCHAR2(50),
  parties_c_email      VARCHAR2(100),
  parties_c_tax_number VARCHAR2(100),
  notes                CLOB,
  is_active            NUMBER(1)     DEFAULT 1,
  created_at           TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  updated_at           TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  AGENT_I_ID           NUMBER(30)    REFERENCES AGENT_MAST(AGENT_I_ID),
  -- FIX 1: Added missing comma before user_i_id
  user_i_id            NUMBER(30)    REFERENCES users(user_i_id)
);

CREATE TABLE ACCOUNT_MAST
(
  ACT_I_ID         NUMBER(30)   PRIMARY KEY,
  SUB_I_ID         NUMBER(30)   REFERENCES SUBLDG_MAST(SUB_I_ID),
  AGENT_I_ID       NUMBER(30)   REFERENCES AGENT_MAST(AGENT_I_ID),
  ACT_C_TYPE       VARCHAR2(10) REFERENCES SUBLDG_MAST(ACT_C_TYPE),
  CUST_I_NO        NUMBER(20)   NOT NULL,
  CURRENCY_I_ID    NUMBER(30)   REFERENCES CURRENCY_MAST(CURRENCY_I_ID),
  ACT_C_NAME       VARCHAR2(250) NOT NULL,
  CUST_C_MNAME     VARCHAR2(250),
  ACT_D_OPDATE     DATE         NOT NULL,
  ACT_C_ACTSTS     CHAR(1)      DEFAULT 'A',
  ACT_C_ODSTS      CHAR(1)      DEFAULT 'Y',
  ACT_I_INTROID    NUMBER(20),
  ACT_I_TRBRCODE   NUMBER(20),
  ACT_D_CLOSDT     DATE,
  CLOSURE_C_REASON VARCHAR2(250),
  ACT_C_PAYFLAG    CHAR(1),
  INTRO_C_REM      VARCHAR2(100),
  MODIFY_I_BY      NUMBER(30)   REFERENCES users(user_i_id),
  AUTH_I_BY        NUMBER(30)   REFERENCES users(user_i_id),
  AUTH_D_DATE      DATE,
  ACT_I_KYC        NUMBER(30),
  parties_i_id     NUMBER(30)   REFERENCES parties(parties_i_id),
  EMP_i_id         NUMBER(30)   REFERENCES EMPLOYEE_MAST(EMP_i_id),
  user_i_id        NUMBER(30)   REFERENCES users(user_i_id)
);

CREATE TABLE SBBAL_MAST
(
  SBBAL_I_ID    NUMBER(30)   PRIMARY KEY,
  -- FIX 2: Added missing comma after ACT_I_ID
  ACT_I_ID      NUMBER(30)   REFERENCES ACCOUNT_MAST(ACT_I_ID),
  AGENT_I_ID    NUMBER(30)   REFERENCES AGENT_MAST(AGENT_I_ID),
  ACT_C_TYPE    VARCHAR2(10) REFERENCES ACCOUNT_MAST(ACT_C_TYPE),
  CUST_I_NO     NUMBER(30)   REFERENCES ACCOUNT_MAST(CUST_I_NO),
  CURRENCY_I_ID NUMBER(30)   REFERENCES CURRENCY_MAST(CURRENCY_I_ID),
  SB_F_CLRBAL   NUMBER(23,5),
  SB_F_SHABAL   NUMBER(23,5),
  SB_F_RSVBAL   NUMBER(23,5),
  SB_F_ODBAL    NUMBER(23,5),
  SB_D_ODSTS    DATE,
  SB_D_TRNDATE  DATE,
  SB_I_NOTRAN   NUMBER(10),
  SB_F_INTRATE  NUMBER(23,5),
  SB_F_FREEZEAMT NUMBER(23,5),
  SB_F_RELEAMT  NUMBER(23,5),
  CUST_I_OLDNO  NUMBER(15)
);

-- ============================================================
-- GROUP: Core Data Model - Settings
-- ============================================================

CREATE TABLE AGENT_BROFILE
(
  BROFILE_I_ID                   NUMBER(30)   PRIMARY KEY,
  EFFECT_D_DATE                  DATE         NOT NULL,
  CURRENCY_I_ID                  NUMBER(30)   REFERENCES CURRENCY_MAST(CURRENCY_I_ID),
  ACT_I_ID                       NUMBER(30)   REFERENCES ACCOUNT_MAST(ACT_I_ID),
  AGENT_I_ID                     NUMBER(30)   REFERENCES AGENT_MAST(AGENT_I_ID),
  AGENT_C_MONHOL                 CHAR(1),
  AGENT_C_TUEHOL                 CHAR(1),
  AGENT_C_WEDHOL                 CHAR(1),
  AGENT_C_THUHOL                 CHAR(1),
  AGENT_C_FRIHOL                 CHAR(1),
  AGENT_C_SATHOL                 CHAR(1),
  AGENT_C_SUNHOL                 CHAR(1),
  OPEN_D_DATE                    DATE,
  INSTALL_D_DATE                 DATE,
  BUD_C_AVAIL                    CHAR(1),
  BUD_C_TYPE                     CHAR(1),
  BR_C_INCEXP_GLHEAD             VARCHAR2(10),
  RATE_C_TYPE                    VARCHAR2(10),
  RET_I_REMINDERS                NUMBER(2),
  VERSION_D_DATE                 DATE,
  VERSION_C_NO                   VARCHAR2(10),
  developer_c_name               VARCHAR2(255),
  developer_c_contact            VARCHAR2(255),
  company_c_name                 VARCHAR2(255),
  company_c_logo                 VARCHAR2(500),
  company_c_address              VARCHAR2(500),
  company_c_phone                VARCHAR2(100),
  company_c_email                VARCHAR2(100),
  company_tax_number             VARCHAR2(100),
  invoice_prefix_sales           VARCHAR2(20)  DEFAULT 'INV',
  invoice_prefix_purchases       VARCHAR2(20)  DEFAULT 'PUR',
  invoice_prefix_sales_return    VARCHAR2(20)  DEFAULT 'SR',
  invoice_prefix_purchase_return VARCHAR2(20)  DEFAULT 'PR',
  tax_enabled                    NUMBER(1)     DEFAULT 0,
  tax_rate                       NUMBER        DEFAULT 14.0,
  dark_mode                      NUMBER(1)     DEFAULT 0,
  CASH_C_ACC                     VARCHAR2(10)  REFERENCES ACCOUNT_MAST(ACT_C_TYPE),
  -- FIX 3: Added missing comma before SESSION_TIME
  SESSION_TIME                   NUMBER(20)    DEFAULT 1800,
  user_i_id                      NUMBER(30)    REFERENCES users(user_i_id)
);

-- ============================================================
-- GROUP: Application Data Model - Inventory
-- ============================================================

CREATE TABLE warehouses
(
  ware_i_id     NUMBER(30)    PRIMARY KEY,
  ware_c_code   VARCHAR2(100) UNIQUE NOT NULL,
  ware_c_name   VARCHAR2(255) NOT NULL,
  ware_c_address VARCHAR2(500),
  ware_c_manager VARCHAR2(255),
  is_active     NUMBER(1)     DEFAULT 1,
  created_at    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  user_i_id     NUMBER(30)    REFERENCES users(user_i_id)
);

CREATE TABLE categories
(
  categ_i_id   NUMBER(30)    PRIMARY KEY,
  categ_c_name VARCHAR2(255) NOT NULL,
  categ_c_type VARCHAR2(50)  NOT NULL,   -- 'product' | 'expense' | 'income'
  parties_i_id NUMBER(30)    REFERENCES parties(parties_i_id),
  created_at   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  user_i_id    NUMBER(30)    REFERENCES users(user_i_id)
);

CREATE TABLE units
(
  unit_i_id       NUMBER(30)    PRIMARY KEY,
  unit_c_name     VARCHAR2(200) NOT NULL,
  unit_short_name VARCHAR2(50),
  created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  user_i_id       NUMBER(30)    REFERENCES users(user_i_id)
);

CREATE TABLE products
(
  prod_i_id        NUMBER(30)    PRIMARY KEY,
  prod_c_code      VARCHAR2(100) UNIQUE NOT NULL,
  prod_c_name      VARCHAR2(255) NOT NULL,
  categ_i_id       NUMBER(30)    REFERENCES categories(categ_i_id),
  unit_i_id        NUMBER(30)    REFERENCES units(unit_i_id),
  ware_i_id        NUMBER(30)    REFERENCES warehouses(ware_i_id),
  supplier_id      NUMBER(30)    REFERENCES parties(parties_i_id),
  purchase_price   NUMBER        DEFAULT 0,
  CURRENCY_I_ID    NUMBER(30)    REFERENCES CURRENCY_MAST(CURRENCY_I_ID),
  sale_price       NUMBER        DEFAULT 0,
  opening_quantity NUMBER        DEFAULT 0,
  current_quantity NUMBER        DEFAULT 0,
  min_quantity     NUMBER        DEFAULT 0,
  is_active        NUMBER(1)     DEFAULT 1,
  notes            CLOB,
  created_at       TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  updated_at       TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  user_i_id        NUMBER(30)    REFERENCES users(user_i_id)
);

-- ============================================================
-- GROUP: Application Data Model - Sales & Purchases
-- ============================================================

CREATE TABLE invoices
(
  invoice_i_id   NUMBER(30)    PRIMARY KEY,
  invoice_number VARCHAR2(100) UNIQUE NOT NULL,
  invoice_c_type VARCHAR2(50)  NOT NULL,   -- 'sale' | 'purchase' | 'sale_return' | 'purchase_return'
  -- FIX 4: Removed extra closing parenthesis in party_id reference
  party_id       NUMBER(30)    REFERENCES parties(parties_i_id),
  warehouse_id   NUMBER(30)    REFERENCES warehouses(ware_i_id),
  AGENT_I_ID     NUMBER(30)    REFERENCES AGENT_MAST(AGENT_I_ID),
  invoice_d_DATE DATE          NOT NULL,
  due_d_date     DATE,
  subtotal       NUMBER        DEFAULT 0,
  CURRENCY_I_ID  NUMBER(30)    REFERENCES CURRENCY_MAST(CURRENCY_I_ID),
  discount_type  VARCHAR2(20)  DEFAULT 'amount',   -- 'amount' | 'percentage'
  discount_value NUMBER        DEFAULT 0,
  discount_amount NUMBER       DEFAULT 0,
  tax_enabled    NUMBER(1)     DEFAULT 0,
  tax_rate       NUMBER        DEFAULT 0,
  tax_amount     NUMBER        DEFAULT 0,
  total          NUMBER        DEFAULT 0,
  paid_amount    NUMBER        DEFAULT 0,
  remaining_amount NUMBER      DEFAULT 0,
  payment_method VARCHAR2(50)  NOT NULL,   -- 'cash' | 'partial' | 'credit'
  status         VARCHAR2(50)  DEFAULT 'posted',   -- 'draft' | 'posted' | 'cancelled'
  notes          CLOB,
  created_by     NUMBER(30)    REFERENCES users(user_i_id),
  created_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  updated_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE invoice_items
(
  invitem_i_id  NUMBER(30)    PRIMARY KEY,
  invoice_id    NUMBER(30)    REFERENCES invoices(invoice_i_id) ON DELETE CASCADE,
  product_id    NUMBER(30)    REFERENCES products(prod_i_id),
  product_name  VARCHAR2(255) NOT NULL,
  product_code  VARCHAR2(100),
  unit_name     VARCHAR2(100),
  AGENT_I_ID    NUMBER(30)    REFERENCES AGENT_MAST(AGENT_I_ID),
  quantity      NUMBER        NOT NULL,
  unit_price    NUMBER        NOT NULL,
  CURRENCY_I_ID NUMBER(30)    REFERENCES CURRENCY_MAST(CURRENCY_I_ID),
  discount_amount NUMBER      DEFAULT 0,
  total         NUMBER        NOT NULL,
  notes         CLOB,
  created_by    NUMBER(30)    REFERENCES users(user_i_id)
);

CREATE TABLE payments
(
  pay_i_id      NUMBER(30)   PRIMARY KEY,
  invoice_id    NUMBER(30)   REFERENCES invoices(invoice_i_id),
  party_id      NUMBER(30)   REFERENCES parties(parties_i_id),
  pay_c_type    VARCHAR2(50) NOT NULL,   -- 'receipt' | 'payment'
  pay_i_amount  NUMBER       NOT NULL,
  AGENT_I_ID    NUMBER(30)   REFERENCES AGENT_MAST(AGENT_I_ID),
  CURRENCY_I_ID NUMBER(30)   REFERENCES CURRENCY_MAST(CURRENCY_I_ID),
  pay_d_DATE    DATE         NOT NULL,
  pay_c_method  VARCHAR2(50) DEFAULT 'cash',
  pay_reference VARCHAR2(255),
  notes         CLOB,
  created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  created_by    NUMBER(30)   REFERENCES users(user_i_id)
);

-- ============================================================
-- GROUP: Application Data Model - Treasury & Accounting Entries
-- ============================================================

CREATE TABLE treasury_transactions
(
  trn_i_id        NUMBER(30)   PRIMARY KEY,
  trn_c_type      VARCHAR2(20) NOT NULL,   -- 'in' | 'out'
  category_id     NUMBER(30)   REFERENCES categories(categ_i_id),
  party_id        NUMBER(30),
  invoice_id      NUMBER(30),
  trn_i_amount    NUMBER       NOT NULL,
  ACT_C_TYPE      VARCHAR2(10) REFERENCES ACCOUNT_MAST(ACT_C_TYPE),
  CURRENCY_I_ID   NUMBER(30)   REFERENCES CURRENCY_MAST(CURRENCY_I_ID),
  CUST_I_NO       NUMBER(20),
  trn_d_DATE      DATE         NOT NULL,
  description     VARCHAR2(500),
  TRN_C_STS       CHAR(1),
  SQE_I_CODE      NUMBER(5),
  TRN_C_CHQNO     VARCHAR2(20),
  TRN_D_CHQDATE   DATE,
  TRN_F_BALANCE   NUMBER(23,5),
  TRN_C_BALSTS    CHAR(2),
  TRN_C_TO_TYPE   VARCHAR2(10),
  TRN_I_TO_NO     NUMBER(20),
  UTI             NUMBER(12)   DEFAULT 0 NOT NULL,
  reference       VARCHAR2(255),
  created_by      NUMBER(30)   REFERENCES users(user_i_id),
  created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  AGENT_I_ID      NUMBER(30)   REFERENCES AGENT_MAST(AGENT_I_ID)
);

CREATE TABLE BATCH
(
  BBD_I_ID      NUMBER(30)   PRIMARY KEY,
  AGENT_I_ID    NUMBER(30)   REFERENCES AGENT_MAST(AGENT_I_ID),
  BBD_D_DATE    DATE         NOT NULL,
  BBD_I_SLNO    NUMBER(20)   NOT NULL,
  BBD_C_DRCR    VARCHAR2(2)  NOT NULL,
  ACT_C_TYPE    VARCHAR2(10) NOT NULL,
  CUST_I_NO     NUMBER(20),
  CURRENCY_I_ID NUMBER(30)   REFERENCES CURRENCY_MAST(CURRENCY_I_ID),
  BBD_F_AMT     NUMBER(23,5) NOT NULL,
  BBD_C_CHQNO   VARCHAR2(20),
  BBD_C_CHQDT   DATE,
  BBD_C_STS     VARCHAR2(2),
  OPER_I_ID     NUMBER(20),
  BBD_C_USRID   NUMBER(30)   REFERENCES users(user_i_id),
  BBD_C_PAYEE   VARCHAR2(200),
  BBD_F_ODAMT   NUMBER(23,5),
  BBD_C_MODE    VARCHAR2(4),
  UTI           NUMBER(12)   DEFAULT 0 NOT NULL,
  BBD_D_DATETIME DATE        DEFAULT SYSDATE
);

CREATE TABLE DAY_LEDG
(
  TRN_I_NO      NUMBER(30)   PRIMARY KEY,
  AGENT_I_ID    NUMBER(30)   REFERENCES AGENT_MAST(AGENT_I_ID),
  TRN_D_DATE    DATE         NOT NULL,
  ACT_C_TYPE    VARCHAR2(10) NOT NULL,
  CUST_I_NO     NUMBER(20),
  TRN_C_NARR    VARCHAR2(250),
  CURRENCY_I_ID NUMBER(30)   REFERENCES CURRENCY_MAST(CURRENCY_I_ID),
  TRN_F_TRNAMT  NUMBER(23,5) NOT NULL,
  TRN_C_DRCR    VARCHAR2(2)  NOT NULL,
  TRT_C_TYPE    VARCHAR2(10),
  TRN_C_STS     VARCHAR2(1),
  SEQ_I_CODE    FLOAT,
  TRN_C_CHQNO   VARCHAR2(10),
  TRN_D_CHQDATE DATE,
  TRN_C_USRID   NUMBER(30)   REFERENCES users(user_i_id),
  TRN_F_BALANCE NUMBER(20,2),
  TRN_C_BALSTS  VARCHAR2(2),
  TRN_C_TOTYPE  VARCHAR2(10),
  TRN_I_TONO    FLOAT,
  UTI           NUMBER(12)   DEFAULT 0 NOT NULL,
  SCREEN_NAME   VARCHAR2(40) DEFAULT 'No Screen Name' NOT NULL
);

-- ============================================================
-- GROUP: Application Data Model - Stock Movements
-- ============================================================

CREATE TABLE stock_movements
(
  move_i_id     NUMBER(30)   PRIMARY KEY,
  -- FIX 5: products(id) → products(prod_i_id)
  product_id    NUMBER(30)   REFERENCES products(prod_i_id),
  invoice_id    NUMBER(30)   REFERENCES invoices(invoice_i_id),
  move_c_type   VARCHAR2(20) NOT NULL,   -- 'in' | 'out' | 'adjustment'
  move_quantity NUMBER       NOT NULL,
  move_unit_price NUMBER,
  CURRENCY_I_ID NUMBER(30)   REFERENCES CURRENCY_MAST(CURRENCY_I_ID),
  warehouse_id  NUMBER(30)   REFERENCES warehouses(ware_i_id),
  move_d_DATE   DATE         NOT NULL,
  notes         CLOB,
  AGENT_I_ID    NUMBER(30)   REFERENCES AGENT_MAST(AGENT_I_ID),
  created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  created_by    NUMBER(30)   REFERENCES users(user_i_id)
);

-- ============================================================
-- END OF SCHEMA
-- Total Tables: 27
-- ============================================================
