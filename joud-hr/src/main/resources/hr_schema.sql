-- ============================================================
-- Joud ERP — joud-hr Plugin Schema
-- ============================================================
-- الجداول الجديدة:
--   DEPARTMENT_MAST   -- الأقسام
--   DESIGNATION_MAST  -- المسميات الوظيفية
--   SALARY_COMPONENT  -- مكونات الراتب (أساسي، بدلات، خصومات)
--   SALARY_STRUCT     -- هيكل راتب كل موظف
--   PAYROLL_MAST      -- رأس مسير الرواتب
--   PAYROLL_LINE      -- تفاصيل مسير الرواتب
--   LEAVE_TYPE        -- أنواع الإجازات
--   LEAVE_REQUEST     -- طلبات الإجازة
--   ATTENDANCE_MAST   -- سجل الحضور والغياب
-- ============================================================

-- ============================================================
-- 1. DEPARTMENT_MAST — الأقسام
-- ============================================================
CREATE TABLE DEPARTMENT_MAST
(
  DEPT_I_ID       NUMBER(30)    PRIMARY KEY,
  AGENT_I_ID      NUMBER(30)    NOT NULL REFERENCES AGENT_MAST(AGENT_I_ID),
  DEPT_C_CODE     VARCHAR2(20)  UNIQUE NOT NULL,
  DEPT_C_NAME     VARCHAR2(100) NOT NULL,
  DEPT_C_NAME_EN  VARCHAR2(100),
  -- الرئيس المباشر للقسم
  MANAGER_EMP_ID  NUMBER(30)    REFERENCES EMPLOYEE_MAST(EMP_i_id),
  -- القسم الأب (للأقسام الفرعية)
  PARENT_DEPT_ID  NUMBER(30)    REFERENCES DEPARTMENT_MAST(DEPT_I_ID),
  DEPT_C_STATUS   CHAR(1)       DEFAULT 'A',  -- A=نشط, I=غير نشط
  DEPT_C_NOTES    VARCHAR2(500),
  CREATED_AT      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  user_i_id       NUMBER(30)    REFERENCES users(user_i_id)
);

-- ============================================================
-- 2. DESIGNATION_MAST — المسميات الوظيفية
-- ============================================================
CREATE TABLE DESIGNATION_MAST
(
  DESIG_I_ID      NUMBER(30)    PRIMARY KEY,
  AGENT_I_ID      NUMBER(30)    NOT NULL REFERENCES AGENT_MAST(AGENT_I_ID),
  DESIG_C_CODE    VARCHAR2(20)  NOT NULL,
  DESIG_C_NAME    VARCHAR2(100) NOT NULL,
  DESIG_C_NAME_EN VARCHAR2(100),
  DEPT_I_ID       NUMBER(30)    REFERENCES DEPARTMENT_MAST(DEPT_I_ID),
  -- درجة الوظيفة (Grade)
  DESIG_C_GRADE   VARCHAR2(10),
  -- الحد الأدنى والأقصى للراتب لهذا المسمى
  DESIG_F_MIN_SAL NUMBER(23,5)  DEFAULT 0,
  DESIG_F_MAX_SAL NUMBER(23,5)  DEFAULT 0,
  CURRENCY_I_ID   NUMBER(30)    REFERENCES CURRENCY_MAST(CURRENCY_I_ID),
  DESIG_C_STATUS  CHAR(1)       DEFAULT 'A',
  CREATED_AT      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  user_i_id       NUMBER(30)    REFERENCES users(user_i_id),
  CONSTRAINT uq_desig UNIQUE (AGENT_I_ID, DESIG_C_CODE)
);

-- ============================================================
-- 3. إضافة حقول للـ EMPLOYEE_MAST (ALTER TABLE)
-- ============================================================
-- الجدول موجود مسبقاً في erp_database_schema_fixed.sql
-- نضيف الحقول الناقصة:
ALTER TABLE EMPLOYEE_MAST ADD (
  DEPT_I_ID       NUMBER(30)    REFERENCES DEPARTMENT_MAST(DEPT_I_ID),
  DESIG_I_ID      NUMBER(30)    REFERENCES DESIGNATION_MAST(DESIG_I_ID),
  -- نوع التوظيف: F=دوام كامل, P=دوام جزئي, C=عقد, T=مؤقت
  EMP_C_EMP_TYPE  CHAR(1)       DEFAULT 'F',
  -- الجنسية
  EMP_C_NATIONALITY VARCHAR2(50),
  -- رقم الهوية / الإقامة
  EMP_C_ID_NO     VARCHAR2(50),
  EMP_D_ID_EXP    DATE,
  -- رقم جواز السفر
  EMP_C_PASSPORT  VARCHAR2(30),
  EMP_D_PASS_EXP  DATE,
  -- رقم الحساب البنكي
  EMP_C_BANK_ACC  VARCHAR2(50),
  EMP_C_BANK_NAME VARCHAR2(100),
  -- الراتب الأساسي المعتمد (يُحسب من SALARY_STRUCT)
  EMP_F_APPROVED_SAL NUMBER(23,5) DEFAULT 0,
  CURRENCY_I_ID   NUMBER(30)    REFERENCES CURRENCY_MAST(CURRENCY_I_ID),
  -- المدير المباشر
  MANAGER_EMP_ID  NUMBER(30)    REFERENCES EMPLOYEE_MAST(EMP_i_id),
  -- بريد العمل
  EMP_C_WORK_EMAIL VARCHAR2(100),
  EMP_C_MOBILE    VARCHAR2(30),
  -- صورة الموظف
  EMP_C_PHOTO_PATH VARCHAR2(500)
);

-- ============================================================
-- 4. SALARY_COMPONENT — مكونات الراتب
-- ============================================================
CREATE TABLE SALARY_COMPONENT
(
  COMP_I_ID       NUMBER(30)    PRIMARY KEY,
  AGENT_I_ID      NUMBER(30)    NOT NULL REFERENCES AGENT_MAST(AGENT_I_ID),
  COMP_C_CODE     VARCHAR2(20)  NOT NULL,
  COMP_C_NAME     VARCHAR2(100) NOT NULL,
  COMP_C_NAME_EN  VARCHAR2(100),
  -- النوع: E=إضافة Earning, D=خصم Deduction, I=معلومة Info
  COMP_C_TYPE     CHAR(1)       NOT NULL,
  -- طريقة الحساب: F=ثابت Fixed, P=نسبة Percentage, A=آلي Auto
  COMP_C_CALC     CHAR(1)       DEFAULT 'F',
  -- النسبة (إذا كانت طريقة الحساب نسبة مئوية من الأساسي)
  COMP_F_PERCENT  NUMBER(5,2)   DEFAULT 0,
  -- هل يدخل في حساب GOSI/ضريبة؟
  COMP_C_TAXABLE  CHAR(1)       DEFAULT 'N',
  COMP_C_STATUS   CHAR(1)       DEFAULT 'A',
  COMP_I_ORDER    NUMBER(5)     DEFAULT 10,  -- ترتيب العرض
  CREATED_AT      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_comp UNIQUE (AGENT_I_ID, COMP_C_CODE)
);

-- ============================================================
-- 5. SALARY_STRUCT — هيكل راتب الموظف
-- ============================================================
CREATE TABLE SALARY_STRUCT
(
  STRUCT_I_ID     NUMBER(30)    PRIMARY KEY,
  EMP_i_id        NUMBER(30)    NOT NULL REFERENCES EMPLOYEE_MAST(EMP_i_id),
  AGENT_I_ID      NUMBER(30)    NOT NULL REFERENCES AGENT_MAST(AGENT_I_ID),
  COMP_I_ID       NUMBER(30)    NOT NULL REFERENCES SALARY_COMPONENT(COMP_I_ID),
  CURRENCY_I_ID   NUMBER(30)    NOT NULL REFERENCES CURRENCY_MAST(CURRENCY_I_ID),
  -- القيمة الشهرية لهذا المكون
  STRUCT_F_AMOUNT NUMBER(23,5)  DEFAULT 0,
  -- تاريخ بدء السريان
  STRUCT_D_FROM   DATE          NOT NULL,
  STRUCT_D_TO     DATE,         -- null = لا يزال سارياً
  STRUCT_C_STATUS CHAR(1)       DEFAULT 'A',
  CREATED_AT      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  user_i_id       NUMBER(30)    REFERENCES users(user_i_id)
);

CREATE INDEX idx_struct_emp ON SALARY_STRUCT(EMP_i_id, STRUCT_D_FROM);

-- ============================================================
-- 6. PAYROLL_MAST — رأس مسير الرواتب
-- ============================================================
CREATE TABLE PAYROLL_MAST
(
  PAY_I_ID        NUMBER(30)    PRIMARY KEY,
  AGENT_I_ID      NUMBER(30)    NOT NULL REFERENCES AGENT_MAST(AGENT_I_ID),
  -- وصف الدورة: مثل "رواتب مايو 2026"
  PAY_C_DESC      VARCHAR2(200) NOT NULL,
  PAY_I_YEAR      NUMBER(4)     NOT NULL,
  PAY_I_MONTH     NUMBER(2)     NOT NULL,
  PAY_D_FROM      DATE          NOT NULL,
  PAY_D_TO        DATE          NOT NULL,
  PAY_D_PAYMENT   DATE,         -- تاريخ صرف الرواتب
  CURRENCY_I_ID   NUMBER(30)    NOT NULL REFERENCES CURRENCY_MAST(CURRENCY_I_ID),
  -- الإجماليات
  PAY_F_TOTAL_EARN  NUMBER(23,5) DEFAULT 0,  -- إجمالي الاستحقاقات
  PAY_F_TOTAL_DED   NUMBER(23,5) DEFAULT 0,  -- إجمالي الخصومات
  PAY_F_NET_PAY     NUMBER(23,5) DEFAULT 0,  -- صافي الراتب
  PAY_I_EMP_COUNT   NUMBER(10)  DEFAULT 0,   -- عدد الموظفين
  -- الحالة: D=مسودة, A=معتمد, P=مدفوع, V=ملغى
  PAY_C_STATUS    CHAR(1)       DEFAULT 'D',
  APPROVED_BY     NUMBER(30)    REFERENCES users(user_i_id),
  APPROVED_AT     DATE,
  -- القيد المحاسبي المولَّد تلقائياً
  JV_I_ID         NUMBER(30)    REFERENCES JOURNAL_ENTRY(JV_I_ID),
  CREATED_BY      NUMBER(30)    REFERENCES users(user_i_id),
  CREATED_AT      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_payroll UNIQUE (AGENT_I_ID, PAY_I_YEAR, PAY_I_MONTH)
);

-- ============================================================
-- 7. PAYROLL_LINE — تفاصيل مسير الرواتب
-- ============================================================
CREATE TABLE PAYROLL_LINE
(
  LINE_I_ID       NUMBER(30)    PRIMARY KEY,
  PAY_I_ID        NUMBER(30)    NOT NULL REFERENCES PAYROLL_MAST(PAY_I_ID) ON DELETE CASCADE,
  EMP_i_id        NUMBER(30)    NOT NULL REFERENCES EMPLOYEE_MAST(EMP_i_id),
  COMP_I_ID       NUMBER(30)    NOT NULL REFERENCES SALARY_COMPONENT(COMP_I_ID),
  -- نوع السطر: E=إضافة, D=خصم
  LINE_C_TYPE     CHAR(1)       NOT NULL,
  LINE_F_AMOUNT   NUMBER(23,5)  DEFAULT 0,
  -- أيام/ساعات العمل الفعلية (للحساب التناسبي)
  LINE_F_DAYS     NUMBER(5,2)   DEFAULT 30,
  LINE_C_NOTES    VARCHAR2(250),
  CREATED_AT      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pline_pay_emp ON PAYROLL_LINE(PAY_I_ID, EMP_i_id);

-- ============================================================
-- 8. LEAVE_TYPE — أنواع الإجازات
-- ============================================================
CREATE TABLE LEAVE_TYPE
(
  LVT_I_ID        NUMBER(30)    PRIMARY KEY,
  AGENT_I_ID      NUMBER(30)    NOT NULL REFERENCES AGENT_MAST(AGENT_I_ID),
  LVT_C_CODE      VARCHAR2(20)  NOT NULL,
  LVT_C_NAME      VARCHAR2(100) NOT NULL,
  -- الرصيد السنوي بالأيام
  LVT_I_ANNUAL_DAYS NUMBER(5)   DEFAULT 0,
  -- هل يُحتسب من الراتب عند تجاوز الرصيد؟
  LVT_C_PAID      CHAR(1)       DEFAULT 'Y',
  LVT_C_STATUS    CHAR(1)       DEFAULT 'A',
  CONSTRAINT uq_lvt UNIQUE (AGENT_I_ID, LVT_C_CODE)
);

-- ============================================================
-- 9. LEAVE_REQUEST — طلبات الإجازة
-- ============================================================
CREATE TABLE LEAVE_REQUEST
(
  LVR_I_ID        NUMBER(30)    PRIMARY KEY,
  EMP_i_id        NUMBER(30)    NOT NULL REFERENCES EMPLOYEE_MAST(EMP_i_id),
  AGENT_I_ID      NUMBER(30)    NOT NULL REFERENCES AGENT_MAST(AGENT_I_ID),
  LVT_I_ID        NUMBER(30)    NOT NULL REFERENCES LEAVE_TYPE(LVT_I_ID),
  LVR_D_FROM      DATE          NOT NULL,
  LVR_D_TO        DATE          NOT NULL,
  LVR_I_DAYS      NUMBER(5,2)   NOT NULL,
  LVR_C_REASON    VARCHAR2(500),
  -- الحالة: P=معلق, A=موافق, R=مرفوض, C=ملغى
  LVR_C_STATUS    CHAR(1)       DEFAULT 'P',
  APPROVED_BY     NUMBER(30)    REFERENCES EMPLOYEE_MAST(EMP_i_id),
  APPROVED_AT     DATE,
  LVR_C_NOTES     VARCHAR2(500),
  CREATED_AT      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_lvr_emp ON LEAVE_REQUEST(EMP_i_id, LVR_D_FROM);

-- ============================================================
-- 10. ATTENDANCE_MAST — سجل الحضور والغياب
-- ============================================================
CREATE TABLE ATTENDANCE_MAST
(
  ATT_I_ID        NUMBER(30)    PRIMARY KEY,
  EMP_i_id        NUMBER(30)    NOT NULL REFERENCES EMPLOYEE_MAST(EMP_i_id),
  AGENT_I_ID      NUMBER(30)    NOT NULL REFERENCES AGENT_MAST(AGENT_I_ID),
  ATT_D_DATE      DATE          NOT NULL,
  ATT_T_IN        TIMESTAMP,    -- وقت الحضور
  ATT_T_OUT       TIMESTAMP,    -- وقت الانصراف
  -- ساعات العمل الفعلية
  ATT_F_HOURS     NUMBER(5,2)   DEFAULT 0,
  ATT_F_OT_HOURS  NUMBER(5,2)   DEFAULT 0,  -- ساعات إضافية
  -- الحالة: P=حاضر, A=غائب, L=إجازة, H=عطلة, E=مأمورية
  ATT_C_STATUS    CHAR(1)       DEFAULT 'P',
  ATT_C_NOTES     VARCHAR2(250),
  CREATED_AT      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_att UNIQUE (EMP_i_id, ATT_D_DATE)
);

CREATE INDEX idx_att_emp_date ON ATTENDANCE_MAST(EMP_i_id, ATT_D_DATE);
CREATE INDEX idx_att_agent    ON ATTENDANCE_MAST(AGENT_I_ID, ATT_D_DATE);

-- ============================================================
-- View: ملخص راتب كل موظف (آخر هيكل راتب ساري)
-- ============================================================
CREATE OR REPLACE VIEW V_EMPLOYEE_SALARY AS
SELECT
  e.EMP_i_id,
  e.EMP_C_NAME,
  e.AGENT_I_ID,
  e.DEPT_I_ID,
  e.DESIG_I_ID,
  d.DEPT_C_NAME,
  ds.DESIG_C_NAME,
  -- إجمالي الاستحقاقات
  SUM(CASE WHEN sc.COMP_C_TYPE='E' THEN s.STRUCT_F_AMOUNT ELSE 0 END) AS TOTAL_EARNINGS,
  -- إجمالي الخصومات
  SUM(CASE WHEN sc.COMP_C_TYPE='D' THEN s.STRUCT_F_AMOUNT ELSE 0 END) AS TOTAL_DEDUCTIONS,
  -- صافي الراتب
  SUM(CASE WHEN sc.COMP_C_TYPE='E' THEN s.STRUCT_F_AMOUNT
           WHEN sc.COMP_C_TYPE='D' THEN -s.STRUCT_F_AMOUNT
           ELSE 0 END) AS NET_SALARY,
  s.CURRENCY_I_ID
FROM EMPLOYEE_MAST e
LEFT JOIN DEPARTMENT_MAST  d  ON e.DEPT_I_ID  = d.DEPT_I_ID
LEFT JOIN DESIGNATION_MAST ds ON e.DESIG_I_ID = ds.DESIG_I_ID
LEFT JOIN SALARY_STRUCT     s  ON e.EMP_i_id   = s.EMP_i_id
  AND s.STRUCT_D_FROM <= SYSDATE
  AND (s.STRUCT_D_TO IS NULL OR s.STRUCT_D_TO >= SYSDATE)
  AND s.STRUCT_C_STATUS = 'A'
LEFT JOIN SALARY_COMPONENT sc ON s.COMP_I_ID = sc.COMP_I_ID
WHERE e.EMP_C_STATUS = 'A'
GROUP BY e.EMP_i_id, e.EMP_C_NAME, e.AGENT_I_ID,
         e.DEPT_I_ID, e.DESIG_I_ID,
         d.DEPT_C_NAME, ds.DESIG_C_NAME, s.CURRENCY_I_ID;

-- ============================================================
-- View: ملخص الحضور الشهري لكل موظف
-- ============================================================
CREATE OR REPLACE VIEW V_ATTENDANCE_MONTHLY AS
SELECT
  a.EMP_i_id,
  a.AGENT_I_ID,
  EXTRACT(YEAR  FROM a.ATT_D_DATE) AS ATT_YEAR,
  EXTRACT(MONTH FROM a.ATT_D_DATE) AS ATT_MONTH,
  COUNT(*)                          AS TOTAL_DAYS,
  SUM(CASE WHEN a.ATT_C_STATUS='P' THEN 1 ELSE 0 END) AS PRESENT_DAYS,
  SUM(CASE WHEN a.ATT_C_STATUS='A' THEN 1 ELSE 0 END) AS ABSENT_DAYS,
  SUM(CASE WHEN a.ATT_C_STATUS='L' THEN 1 ELSE 0 END) AS LEAVE_DAYS,
  SUM(NVL(a.ATT_F_HOURS,0))   AS TOTAL_HOURS,
  SUM(NVL(a.ATT_F_OT_HOURS,0)) AS TOTAL_OT_HOURS
FROM ATTENDANCE_MAST a
GROUP BY a.EMP_i_id, a.AGENT_I_ID,
         EXTRACT(YEAR FROM a.ATT_D_DATE),
         EXTRACT(MONTH FROM a.ATT_D_DATE);

-- ============================================================
-- END OF HR SCHEMA
-- الجداول الجديدة: 9
-- تعديلات: EMPLOYEE_MAST (ALTER)
-- الـ Views: 2
-- ============================================================
