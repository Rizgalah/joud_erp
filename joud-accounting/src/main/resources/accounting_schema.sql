-- ============================================================
-- Joud ERP — joud-accounting Plugin Schema
-- الجداول الجديدة والمُحسَّنة لوحدة المحاسبة
-- ============================================================
-- الجداول الجديدة:
--   GENLDG_MAST    -- الأرصدة التراكمية (قلب التقارير الختامية)
--   EXCH_RATE_HIST -- تاريخ أسعار الصرف (متعدد العملات)
-- الجداول المنقولة من القلب إلى هذه الوحدة:
--   DAY_LEDG, BATCH, TREASURY_TRANSACTIONS, STOCK_MOVEMENTS
-- ============================================================

-- ============================================================
-- 1. EXCH_RATE_HIST — تاريخ أسعار الصرف
-- ============================================================
-- يحفظ سعر كل عملة مقابل العملة الأساسية (BASE) يومياً.
-- يُستخدم في:
--   - تحويل أرصدة الفروع الخارجية عند التجميع
--   - إعادة تقييم العملة الأجنبية في نهاية الفترة
--   - حساب فروق العملة (Forex Gain/Loss)
-- ============================================================
CREATE TABLE EXCH_RATE_HIST
(
  EXCH_I_ID          NUMBER(30)    PRIMARY KEY,
  -- العملة المصدر (مثلاً USD, EUR, SAR)
  CURRENCY_I_ID      NUMBER(30)    NOT NULL REFERENCES CURRENCY_MAST(CURRENCY_I_ID),
  -- العملة الأساسية للشركة (مثلاً USD إذا كانت الرئاسة أمريكية)
  BASE_CURRENCY_I_ID NUMBER(30)    NOT NULL REFERENCES CURRENCY_MAST(CURRENCY_I_ID),
  -- الفرع المعني (كل فرع قد يملك عملته المحلية)
  AGENT_I_ID         NUMBER(30)    REFERENCES AGENT_MAST(AGENT_I_ID),
  -- نوع السعر: B=شراء Buy, S=بيع Sell, M=وسط Middle, O=رسمي Official
  EXCH_C_RATE_TYPE   CHAR(1)       NOT NULL DEFAULT 'M',
  -- تاريخ السعر
  EXCH_D_DATE        DATE          NOT NULL,
  -- سعر الصرف (1 وحدة من العملة = X وحدة من العملة الأساسية)
  EXCH_F_RATE        NUMBER(23,8)  NOT NULL,
  -- السعر العكسي (1 وحدة أساسية = X وحدة من العملة)
  EXCH_F_INVERSE     NUMBER(23,8),
  -- مصدر السعر: M=يدوي Manual, A=تلقائي Auto/API
  EXCH_C_SOURCE      CHAR(1)       DEFAULT 'M',
  EXCH_C_NOTES       VARCHAR2(250),
  EXCH_D_ENTRY       DATE          DEFAULT SYSDATE,
  user_i_id          NUMBER(30)    REFERENCES users(user_i_id),
  -- منع تكرار سعر نفس العملة في نفس اليوم لنفس النوع
  CONSTRAINT uq_exch_rate UNIQUE (CURRENCY_I_ID, BASE_CURRENCY_I_ID, EXCH_D_DATE, EXCH_C_RATE_TYPE, AGENT_I_ID)
);

CREATE INDEX idx_exch_date     ON EXCH_RATE_HIST(EXCH_D_DATE);
CREATE INDEX idx_exch_currency ON EXCH_RATE_HIST(CURRENCY_I_ID, BASE_CURRENCY_I_ID);

-- ============================================================
-- 2. GENLDG_MAST — الأرصدة التراكمية (General Ledger Master)
-- ============================================================
-- القلب الفعلي لاستخراج التقارير الختامية.
-- يُحدَّث تلقائياً عند كل قيد في DAY_LEDG أو BATCH.
-- يحتفظ بـ:
--   - رصيد افتتاح الفترة
--   - إجمالي المدين والدائن خلال الفترة
--   - رصيد الإغلاق بعملة الحساب
--   - رصيد الإغلاق بالعملة الأساسية (للتجميع)
-- ============================================================
CREATE TABLE GENLDG_MAST
(
  GEN_I_ID            NUMBER(30)    PRIMARY KEY,
  -- الحساب المعني
  ACT_I_ID            NUMBER(30)    NOT NULL REFERENCES ACCOUNT_MAST(ACT_I_ID),
  ACT_C_TYPE          VARCHAR2(10)  NOT NULL REFERENCES ACCOUNT_MAST(ACT_C_TYPE),
  -- الفرع والبلد
  AGENT_I_ID          NUMBER(30)    NOT NULL REFERENCES AGENT_MAST(AGENT_I_ID),
  -- البلد (لتجميع التقارير على مستوى البلد)
  COUNTRY_C_CODE      VARCHAR2(10)  NOT NULL,
  -- الفترة المحاسبية: السنة والشهر
  GEN_I_YEAR          NUMBER(4)     NOT NULL,
  GEN_I_MONTH         NUMBER(2)     NOT NULL,  -- 0 = رصيد افتتاح السنة
  -- عملة الحساب الأصلية
  CURRENCY_I_ID       NUMBER(30)    NOT NULL REFERENCES CURRENCY_MAST(CURRENCY_I_ID),
  -- العملة الأساسية للشركة (للتجميع الموحد)
  BASE_CURRENCY_I_ID  NUMBER(30)    REFERENCES CURRENCY_MAST(CURRENCY_I_ID),
  -- ─── أرصدة بعملة الحساب ───────────────────────────
  -- رصيد الافتتاح (من إغلاق الفترة السابقة)
  GEN_F_OP_BAL        NUMBER(23,5)  DEFAULT 0,
  GEN_C_OP_DRCR       CHAR(1)       DEFAULT 'D', -- D=مدين C=دائن
  -- إجمالي المدين خلال الفترة
  GEN_F_DR_AMT        NUMBER(23,5)  DEFAULT 0,
  -- إجمالي الدائن خلال الفترة
  GEN_F_CR_AMT        NUMBER(23,5)  DEFAULT 0,
  -- رصيد الإغلاق = OP_BAL ± (DR - CR)
  GEN_F_CL_BAL        NUMBER(23,5)  DEFAULT 0,
  GEN_C_CL_DRCR       CHAR(1)       DEFAULT 'D',
  -- ─── أرصدة بالعملة الأساسية (للتجميع) ────────────
  GEN_F_OP_BAL_BASE   NUMBER(23,5)  DEFAULT 0,
  GEN_F_DR_AMT_BASE   NUMBER(23,5)  DEFAULT 0,
  GEN_F_CR_AMT_BASE   NUMBER(23,5)  DEFAULT 0,
  GEN_F_CL_BAL_BASE   NUMBER(23,5)  DEFAULT 0,
  GEN_C_CL_DRCR_BASE  CHAR(1)       DEFAULT 'D',
  -- سعر الصرف المستخدم في التحويل للعملة الأساسية
  GEN_F_EXCH_RATE     NUMBER(23,8)  DEFAULT 1,
  -- فروق إعادة التقييم (Forex Revaluation)
  GEN_F_FOREX_DIFF    NUMBER(23,5)  DEFAULT 0,
  -- ─── معلومات التدقيق ─────────────────────────────
  -- عدد الحركات المُسجلة في هذه الفترة
  GEN_I_TRN_COUNT     NUMBER(10)    DEFAULT 0,
  -- حالة الفترة: O=مفتوحة Open, C=مغلقة Closed, L=مقفلة Locked
  GEN_C_PERIOD_STS    CHAR(1)       DEFAULT 'O',
  -- تاريخ آخر تحديث
  GEN_D_LAST_UPDATE   DATE          DEFAULT SYSDATE,
  user_i_id           NUMBER(30)    REFERENCES users(user_i_id),
  -- منع تكرار نفس الحساب في نفس الفترة لنفس الفرع
  CONSTRAINT uq_genldg UNIQUE (ACT_I_ID, AGENT_I_ID, GEN_I_YEAR, GEN_I_MONTH, CURRENCY_I_ID)
);

-- فهارس لتسريع استخراج التقارير
CREATE INDEX idx_gen_agent_period  ON GENLDG_MAST(AGENT_I_ID, GEN_I_YEAR, GEN_I_MONTH);
CREATE INDEX idx_gen_country       ON GENLDG_MAST(COUNTRY_C_CODE, GEN_I_YEAR, GEN_I_MONTH);
CREATE INDEX idx_gen_act_period    ON GENLDG_MAST(ACT_I_ID, GEN_I_YEAR, GEN_I_MONTH);
CREATE INDEX idx_gen_period        ON GENLDG_MAST(GEN_I_YEAR, GEN_I_MONTH);

-- ============================================================
-- 3. DAY_LEDG — دفتر اليومية (محسَّن)
-- ============================================================
-- أُضيفت حقول: COUNTRY_C_CODE, EXCH_F_RATE, TRN_F_AMT_BASE
-- لدعم متعدد العملات والتجميع
-- ============================================================
CREATE TABLE DAY_LEDG
(
  TRN_I_NO        NUMBER(30)    PRIMARY KEY,
  AGENT_I_ID      NUMBER(30)    NOT NULL REFERENCES AGENT_MAST(AGENT_I_ID),
  -- البلد (مشتق من AGENT_MAST لكنه مُخزَّن هنا لتسريع التقارير)
  COUNTRY_C_CODE  VARCHAR2(10)  NOT NULL,
  TRN_D_DATE      DATE          NOT NULL,
  ACT_C_TYPE      VARCHAR2(10)  NOT NULL,
  CUST_I_NO       NUMBER(20),
  TRN_C_NARR      VARCHAR2(250),
  -- عملة الحركة
  CURRENCY_I_ID   NUMBER(30)    NOT NULL REFERENCES CURRENCY_MAST(CURRENCY_I_ID),
  -- مبلغ الحركة بعملة الحساب
  TRN_F_TRNAMT    NUMBER(23,5)  NOT NULL,
  TRN_C_DRCR      VARCHAR2(2)   NOT NULL,  -- DR أو CR
  -- سعر الصرف وقت الحركة
  EXCH_F_RATE     NUMBER(23,8)  DEFAULT 1,
  -- المبلغ بالعملة الأساسية للشركة (للتجميع)
  TRN_F_AMT_BASE  NUMBER(23,5)  DEFAULT 0,
  -- ─── معلومات القيد ───────────────────────────────
  TRT_C_TYPE      VARCHAR2(10),            -- نوع القيد: INV, PAY, JV, ...
  TRN_C_STS       VARCHAR2(1)   DEFAULT 'P', -- P=معلق, A=معتمد, V=ملغى
  SEQ_I_CODE      FLOAT,
  TRN_C_CHQNO     VARCHAR2(10),
  TRN_D_CHQDATE   DATE,
  TRN_C_USRID     NUMBER(30)    REFERENCES users(user_i_id),
  TRN_F_BALANCE   NUMBER(20,2), -- الرصيد التراكمي بعد الحركة
  TRN_C_BALSTS    VARCHAR2(2),
  TRN_C_TOTYPE    VARCHAR2(10),
  TRN_I_TONO      FLOAT,
  -- مرجع لجدول GENLDG_MAST (لتسهيل التتبع)
  GEN_I_ID        NUMBER(30)    REFERENCES GENLDG_MAST(GEN_I_ID),
  UTI             NUMBER(12)    DEFAULT 0 NOT NULL,
  SCREEN_NAME     VARCHAR2(40)  DEFAULT 'No Screen Name' NOT NULL
);

CREATE INDEX idx_dayldg_agent_date ON DAY_LEDG(AGENT_I_ID, TRN_D_DATE);
CREATE INDEX idx_dayldg_country    ON DAY_LEDG(COUNTRY_C_CODE, TRN_D_DATE);
CREATE INDEX idx_dayldg_act        ON DAY_LEDG(ACT_C_TYPE, TRN_D_DATE);
CREATE INDEX idx_dayldg_currency   ON DAY_LEDG(CURRENCY_I_ID, TRN_D_DATE);

-- ============================================================
-- 4. BATCH — القيود المجمعة (محسَّن)
-- ============================================================
CREATE TABLE BATCH
(
  BBD_I_ID        NUMBER(30)    PRIMARY KEY,
  AGENT_I_ID      NUMBER(30)    NOT NULL REFERENCES AGENT_MAST(AGENT_I_ID),
  COUNTRY_C_CODE  VARCHAR2(10)  NOT NULL,
  BBD_D_DATE      DATE          NOT NULL,
  BBD_I_SLNO      NUMBER(20)    NOT NULL,
  BBD_C_DRCR      VARCHAR2(2)   NOT NULL,
  ACT_C_TYPE      VARCHAR2(10)  NOT NULL,
  CUST_I_NO       NUMBER(20),
  CURRENCY_I_ID   NUMBER(30)    NOT NULL REFERENCES CURRENCY_MAST(CURRENCY_I_ID),
  BBD_F_AMT       NUMBER(23,5)  NOT NULL,
  -- مبلغ بالعملة الأساسية
  BBD_F_AMT_BASE  NUMBER(23,5)  DEFAULT 0,
  EXCH_F_RATE     NUMBER(23,8)  DEFAULT 1,
  BBD_C_CHQNO     VARCHAR2(20),
  BBD_C_CHQDT     DATE,
  BBD_C_STS       VARCHAR2(2)   DEFAULT 'P', -- P=معلق, A=معتمد, V=ملغى
  OPER_I_ID       NUMBER(20),
  BBD_C_USRID     NUMBER(30)    REFERENCES users(user_i_id),
  BBD_C_PAYEE     VARCHAR2(200),
  BBD_F_ODAMT     NUMBER(23,5),
  BBD_C_MODE      VARCHAR2(4),
  -- مرجع لرقم الدفعة (لربط الأسطر ببعضها)
  BATCH_REF_NO    VARCHAR2(30),
  GEN_I_ID        NUMBER(30)    REFERENCES GENLDG_MAST(GEN_I_ID),
  UTI             NUMBER(12)    DEFAULT 0 NOT NULL,
  BBD_D_DATETIME  DATE          DEFAULT SYSDATE
);

CREATE INDEX idx_batch_agent_date ON BATCH(AGENT_I_ID, BBD_D_DATE);
CREATE INDEX idx_batch_country    ON BATCH(COUNTRY_C_CODE, BBD_D_DATE);
CREATE INDEX idx_batch_ref        ON BATCH(BATCH_REF_NO);

-- ============================================================
-- 5. TREASURY_TRANSACTIONS — حركات الخزينة (محسَّن)
-- ============================================================
CREATE TABLE TREASURY_TRANSACTIONS
(
  TRN_I_ID        NUMBER(30)    PRIMARY KEY,
  AGENT_I_ID      NUMBER(30)    NOT NULL REFERENCES AGENT_MAST(AGENT_I_ID),
  COUNTRY_C_CODE  VARCHAR2(10)  NOT NULL,
  TRN_C_TYPE      VARCHAR2(20)  NOT NULL,  -- 'in' | 'out' | 'transfer'
  CATEGORY_ID     NUMBER(30)    REFERENCES categories(categ_i_id),
  PARTY_ID        NUMBER(30)    REFERENCES parties(parties_i_id),
  INVOICE_ID      NUMBER(30)    REFERENCES invoices(invoice_i_id),
  TRN_I_AMOUNT    NUMBER(23,5)  NOT NULL,
  ACT_C_TYPE      VARCHAR2(10)  REFERENCES ACCOUNT_MAST(ACT_C_TYPE),
  CURRENCY_I_ID   NUMBER(30)    NOT NULL REFERENCES CURRENCY_MAST(CURRENCY_I_ID),
  EXCH_F_RATE     NUMBER(23,8)  DEFAULT 1,
  -- المبلغ بالعملة الأساسية
  TRN_F_AMT_BASE  NUMBER(23,5)  DEFAULT 0,
  CUST_I_NO       NUMBER(20),
  TRN_D_DATE      DATE          NOT NULL,
  DESCRIPTION     VARCHAR2(500),
  TRN_C_STS       CHAR(1)       DEFAULT 'P',
  SQE_I_CODE      NUMBER(5),
  TRN_C_CHQNO     VARCHAR2(20),
  TRN_D_CHQDATE   DATE,
  TRN_F_BALANCE   NUMBER(23,5),
  TRN_C_BALSTS    CHAR(2),
  TRN_C_TO_TYPE   VARCHAR2(10),
  TRN_I_TO_NO     NUMBER(20),
  UTI             NUMBER(12)    DEFAULT 0 NOT NULL,
  REFERENCE       VARCHAR2(255),
  CREATED_BY      NUMBER(30)    REFERENCES users(user_i_id),
  CREATED_AT      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  GEN_I_ID        NUMBER(30)    REFERENCES GENLDG_MAST(GEN_I_ID)
);

CREATE INDEX idx_treasury_agent_date ON TREASURY_TRANSACTIONS(AGENT_I_ID, TRN_D_DATE);
CREATE INDEX idx_treasury_country    ON TREASURY_TRANSACTIONS(COUNTRY_C_CODE, TRN_D_DATE);

-- ============================================================
-- 6. ACCOUNTING_PERIOD — الفترات المحاسبية
-- ============================================================
-- يتحكم في فتح وإغلاق الفترات المحاسبية لكل فرع.
-- يمنع التسجيل في فترة مغلقة.
-- ============================================================
CREATE TABLE ACCOUNTING_PERIOD
(
  PERIOD_I_ID     NUMBER(30)    PRIMARY KEY,
  AGENT_I_ID      NUMBER(30)    NOT NULL REFERENCES AGENT_MAST(AGENT_I_ID),
  PERIOD_I_YEAR   NUMBER(4)     NOT NULL,
  PERIOD_I_MONTH  NUMBER(2)     NOT NULL,  -- 1-12
  PERIOD_D_FROM   DATE          NOT NULL,
  PERIOD_D_TO     DATE          NOT NULL,
  -- O=مفتوحة, C=مغلقة (لا تسمح بقيود), L=مقفلة (بعد الإغلاق السنوي)
  PERIOD_C_STS    CHAR(1)       DEFAULT 'O',
  PERIOD_D_CLOSED DATE,
  CLOSED_BY       NUMBER(30)    REFERENCES users(user_i_id),
  PERIOD_C_NOTES  VARCHAR2(250),
  CREATED_AT      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_period UNIQUE (AGENT_I_ID, PERIOD_I_YEAR, PERIOD_I_MONTH)
);

-- ============================================================
-- 7. JOURNAL_ENTRY — رأس القيد اليومي (Journal Voucher)
-- ============================================================
-- يجمع أسطر DAY_LEDG في مجموعة واحدة متوازنة (DR = CR).
-- يضمن عدم حفظ قيد غير متوازن.
-- ============================================================
CREATE TABLE JOURNAL_ENTRY
(
  JV_I_ID         NUMBER(30)    PRIMARY KEY,
  JV_C_REF        VARCHAR2(30)  UNIQUE NOT NULL, -- رقم القيد مثل JV-2026-00001
  AGENT_I_ID      NUMBER(30)    NOT NULL REFERENCES AGENT_MAST(AGENT_I_ID),
  COUNTRY_C_CODE  VARCHAR2(10)  NOT NULL,
  JV_D_DATE       DATE          NOT NULL,
  PERIOD_I_ID     NUMBER(30)    REFERENCES ACCOUNTING_PERIOD(PERIOD_I_ID),
  JV_C_TYPE       VARCHAR2(20)  NOT NULL, -- JV=يدوي, INV=فاتورة, PAY=دفع, SAL=راتب
  JV_C_NARR       VARCHAR2(500),
  -- إجمالي المدين (يجب أن يساوي إجمالي الدائن)
  JV_F_TOTAL_DR   NUMBER(23,5)  DEFAULT 0,
  JV_F_TOTAL_CR   NUMBER(23,5)  DEFAULT 0,
  CURRENCY_I_ID   NUMBER(30)    NOT NULL REFERENCES CURRENCY_MAST(CURRENCY_I_ID),
  EXCH_F_RATE     NUMBER(23,8)  DEFAULT 1,
  -- O=مسودة, P=معلق للاعتماد, A=معتمد, V=ملغى
  JV_C_STS        CHAR(1)       DEFAULT 'O',
  APPROVED_BY     NUMBER(30)    REFERENCES users(user_i_id),
  APPROVED_AT     DATE,
  CREATED_BY      NUMBER(30)    REFERENCES users(user_i_id),
  CREATED_AT      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_jv_agent_date ON JOURNAL_ENTRY(AGENT_I_ID, JV_D_DATE);
CREATE INDEX idx_jv_period     ON JOURNAL_ENTRY(PERIOD_I_ID);
CREATE INDEX idx_jv_type       ON JOURNAL_ENTRY(JV_C_TYPE, JV_D_DATE);

-- ============================================================
-- 8. Views للتقارير الجاهزة
-- ============================================================

-- ─── ميزان المراجعة — على مستوى الفرع ─────────────────────
CREATE OR REPLACE VIEW V_TRIAL_BALANCE_AGENT AS
SELECT
  g.AGENT_I_ID,
  g.COUNTRY_C_CODE,
  g.GEN_I_YEAR,
  g.GEN_I_MONTH,
  a.ACT_C_TYPE,
  a.ACT_C_NAME,
  sl.SUB_C_LDGDESC    AS SUB_LEDGER_NAME,
  ml.LDG_C_DESC       AS MAIN_LEDGER_NAME,
  lg.GROUP_C_DESC     AS GROUP_NAME,
  lg.GROUP_C_TYPE     AS GROUP_TYPE,  -- A=أصول, L=خصوم, I=إيراد, E=مصروف
  g.CURRENCY_I_ID,
  -- بعملة الحساب
  g.GEN_F_OP_BAL,
  g.GEN_C_OP_DRCR,
  g.GEN_F_DR_AMT,
  g.GEN_F_CR_AMT,
  g.GEN_F_CL_BAL,
  g.GEN_C_CL_DRCR,
  -- بالعملة الأساسية
  g.GEN_F_CL_BAL_BASE,
  g.GEN_C_CL_DRCR_BASE,
  g.GEN_F_EXCH_RATE
FROM GENLDG_MAST g
JOIN ACCOUNT_MAST  a  ON g.ACT_I_ID   = a.ACT_I_ID
JOIN SUBLDG_MAST   sl ON a.SUB_I_ID   = sl.SUB_I_ID
JOIN MAINLDG_MAST  ml ON sl.LDG_I_ID  = ml.LDG_I_ID
JOIN LEDGER_GROUP  lg ON ml.GROUP_I_ID = lg.GROUP_I_ID;

-- ─── ميزان المراجعة — على مستوى البلد ─────────────────────
CREATE OR REPLACE VIEW V_TRIAL_BALANCE_COUNTRY AS
SELECT
  COUNTRY_C_CODE,
  GEN_I_YEAR,
  GEN_I_MONTH,
  ACT_C_TYPE,
  BASE_CURRENCY_I_ID,
  SUM(GEN_F_DR_AMT_BASE)  AS TOTAL_DR,
  SUM(GEN_F_CR_AMT_BASE)  AS TOTAL_CR,
  SUM(
    CASE GEN_C_CL_DRCR_BASE
      WHEN 'D' THEN  GEN_F_CL_BAL_BASE
      ELSE          -GEN_F_CL_BAL_BASE
    END
  ) AS NET_BAL_BASE
FROM GENLDG_MAST
GROUP BY COUNTRY_C_CODE, GEN_I_YEAR, GEN_I_MONTH, ACT_C_TYPE, BASE_CURRENCY_I_ID;

-- ─── ميزان المراجعة — على مستوى الشركة كاملاً ─────────────
CREATE OR REPLACE VIEW V_TRIAL_BALANCE_COMPANY AS
SELECT
  GEN_I_YEAR,
  GEN_I_MONTH,
  ACT_C_TYPE,
  BASE_CURRENCY_I_ID,
  SUM(GEN_F_DR_AMT_BASE)  AS TOTAL_DR,
  SUM(GEN_F_CR_AMT_BASE)  AS TOTAL_CR,
  SUM(
    CASE GEN_C_CL_DRCR_BASE
      WHEN 'D' THEN  GEN_F_CL_BAL_BASE
      ELSE          -GEN_F_CL_BAL_BASE
    END
  ) AS NET_BAL_BASE
FROM GENLDG_MAST
GROUP BY GEN_I_YEAR, GEN_I_MONTH, ACT_C_TYPE, BASE_CURRENCY_I_ID;

-- ─── الميزانية العمومية — Balance Sheet ─────────────────────
CREATE OR REPLACE VIEW V_BALANCE_SHEET_COMPANY AS
SELECT
  GEN_I_YEAR,
  GEN_I_MONTH,
  BASE_CURRENCY_I_ID,
  -- الأصول
  SUM(CASE WHEN lg.GROUP_C_TYPE = 'A' THEN
    CASE g.GEN_C_CL_DRCR_BASE WHEN 'D' THEN g.GEN_F_CL_BAL_BASE ELSE -g.GEN_F_CL_BAL_BASE END
  ELSE 0 END) AS TOTAL_ASSETS,
  -- الخصوم
  SUM(CASE WHEN lg.GROUP_C_TYPE = 'L' THEN
    CASE g.GEN_C_CL_DRCR_BASE WHEN 'C' THEN g.GEN_F_CL_BAL_BASE ELSE -g.GEN_F_CL_BAL_BASE END
  ELSE 0 END) AS TOTAL_LIABILITIES,
  -- حقوق الملكية
  SUM(CASE WHEN lg.GROUP_C_TYPE = 'E' THEN
    CASE g.GEN_C_CL_DRCR_BASE WHEN 'C' THEN g.GEN_F_CL_BAL_BASE ELSE -g.GEN_F_CL_BAL_BASE END
  ELSE 0 END) AS TOTAL_EQUITY
FROM GENLDG_MAST g
JOIN ACCOUNT_MAST a  ON g.ACT_I_ID    = a.ACT_I_ID
JOIN SUBLDG_MAST  sl ON a.SUB_I_ID    = sl.SUB_I_ID
JOIN MAINLDG_MAST ml ON sl.LDG_I_ID   = ml.LDG_I_ID
JOIN LEDGER_GROUP lg ON ml.GROUP_I_ID = lg.GROUP_I_ID
GROUP BY GEN_I_YEAR, GEN_I_MONTH, BASE_CURRENCY_I_ID;

-- ─── قائمة الدخل — Income Statement ─────────────────────────
CREATE OR REPLACE VIEW V_INCOME_STATEMENT_COMPANY AS
SELECT
  GEN_I_YEAR,
  GEN_I_MONTH,
  BASE_CURRENCY_I_ID,
  -- الإيرادات
  SUM(CASE WHEN lg.GROUP_C_TYPE = 'I' THEN
    CASE g.GEN_C_CL_DRCR_BASE WHEN 'C' THEN g.GEN_F_CL_BAL_BASE ELSE -g.GEN_F_CL_BAL_BASE END
  ELSE 0 END) AS TOTAL_REVENUE,
  -- المصروفات
  SUM(CASE WHEN lg.GROUP_C_TYPE = 'X' THEN
    CASE g.GEN_C_CL_DRCR_BASE WHEN 'D' THEN g.GEN_F_CL_BAL_BASE ELSE -g.GEN_F_CL_BAL_BASE END
  ELSE 0 END) AS TOTAL_EXPENSES,
  -- صافي الربح/الخسارة
  SUM(CASE
    WHEN lg.GROUP_C_TYPE = 'I' THEN
      CASE g.GEN_C_CL_DRCR_BASE WHEN 'C' THEN  g.GEN_F_CL_BAL_BASE ELSE -g.GEN_F_CL_BAL_BASE END
    WHEN lg.GROUP_C_TYPE = 'X' THEN
      CASE g.GEN_C_CL_DRCR_BASE WHEN 'D' THEN -g.GEN_F_CL_BAL_BASE ELSE  g.GEN_F_CL_BAL_BASE END
    ELSE 0
  END) AS NET_PROFIT_LOSS
FROM GENLDG_MAST g
JOIN ACCOUNT_MAST a  ON g.ACT_I_ID    = a.ACT_I_ID
JOIN SUBLDG_MAST  sl ON a.SUB_I_ID    = sl.SUB_I_ID
JOIN MAINLDG_MAST ml ON sl.LDG_I_ID   = ml.LDG_I_ID
JOIN LEDGER_GROUP lg ON ml.GROUP_I_ID = lg.GROUP_I_ID
WHERE lg.GROUP_C_TYPE IN ('I', 'X')
GROUP BY GEN_I_YEAR, GEN_I_MONTH, BASE_CURRENCY_I_ID;

-- ============================================================
-- END OF ACCOUNTING SCHEMA
-- الجداول الجديدة: 6
-- الـ Views الجاهزة: 5
-- ============================================================
