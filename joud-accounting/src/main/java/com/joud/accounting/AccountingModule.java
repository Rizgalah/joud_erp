package com.joud.accounting;

import com.joud.accounting.entity.AccountingPeriod;
import com.joud.accounting.entity.AccountingPeriod.PeriodStatus;
import com.joud.accounting.entity.AgentMast;
import com.joud.core.menu.MenuItem;
import com.joud.core.module.JoudModule;
import com.joud.core.db.JoudRepository;
import com.joud.core.report.ReportDefinition;
import com.joud.core.report.ReportParameter;
import com.joud.core.report.ReportParameter.ParamType;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * AccountingModule — تسجيل وحدة المحاسبة في القلب
 *
 * يُكتشف تلقائياً عبر Java SPI عند التشغيل.
 * ملف التسجيل: META-INF/services/com.joud.core.module.JoudModule
 */
public class AccountingModule implements JoudModule {

        @Override
        public String getModuleId() {
                return "accounting";
        }

        @Override
        public String getModuleName(Locale locale) {
                return "ar".equals(locale.getLanguage()) ? "المحاسبة" : "Accounting";
        }

        @Override
        public String getVersion() {
                return "1.0.0";
        }

        @Override
        public String getDescription(Locale locale) {
                return "القيود اليومية، ميزان المراجعة، الميزانية، قائمة الدخل";
        }

        @Override
        public int getMenuOrder() {
                return 20;
        }

        @Override
        public List<MenuItem> getMenuItems(Locale locale) {
                boolean ar = "ar".equals(locale.getLanguage());

                // ─── المحاسبة ───────────────────────────────
                MenuItem dailyOps = MenuItem.builder("accounting.root", ar ? "المحاسبة" : "Accounting")
                                .icon("pi pi-calculator")
                                .order(20)
                                .child(
                                                MenuItem.builder("accounting.journal",
                                                                ar ? "القيد اليومي" : "Journal Entry")
                                                                .icon("pi pi-pencil")
                                                                .outcome("/views/accounting/journal-entry")
                                                                .permission("accounting.journal.view")
                                                                .order(1).build())
                                .child(
                                                MenuItem.builder("accounting.ledger",
                                                                ar ? "كشف الحساب" : "Account Statement")
                                                                .icon("pi pi-book")
                                                                .outcome("/views/accounting/account-statement")
                                                                .permission("accounting.ledger.view")
                                                                .order(2).build())
                                .child(
                                                MenuItem.builder("accounting.trial",
                                                                ar ? "ميزان المراجعة" : "Trial Balance")
                                                                .icon("pi pi-list-check")
                                                                .outcome("/views/accounting/trial-balance")
                                                                .permission("accounting.trial.view")
                                                                .order(3).build())
                                .child(
                                                MenuItem.builder("accounting.bs",
                                                                ar ? "الميزانية العمومية" : "Balance Sheet")
                                                                .icon("pi pi-chart-bar")
                                                                .outcome("/views/accounting/trial-balance")
                                                                .permission("accounting.reports.view")
                                                                .order(4).build())
                                .child(
                                                MenuItem.builder("accounting.income",
                                                                ar ? "قائمة الدخل" : "Income Statement")
                                                                .icon("pi pi-dollar")
                                                                .outcome("/views/accounting/trial-balance")
                                                                .permission("accounting.reports.view")
                                                                .order(5).build())
                                .child(
                                                MenuItem.builder("accounting.period",
                                                                ar ? "الفترات المحاسبية" : "Periods")
                                                                .icon("pi pi-calendar")
                                                                .outcome("/views/accounting/periods")
                                                                .permission("accounting.period.manage")
                                                                .order(6).build())
                                .child(
                                                MenuItem.builder("accounting.exchrate",
                                                                ar ? "أسعار الصرف" : "Exchange Rates")
                                                                .icon("pi pi-sync")
                                                                .outcome("/views/accounting/exchange-rates")
                                                                .permission("accounting.exchrate.view")
                                                                .order(7).build())
                                .build();
                MenuItem masterInsertion = MenuItem
                                .builder("accounting.masters", ar ? "إدخال الثوابت" : "Master Insertion")
                                .icon("pi pi-cog")
                                .order(21) // يظهر مباشرة بعد المحاسبة
                                .permission("accounting.masters.view") // الصلاحية الكبرى الحاكمة لظهور هذا التبويب
                                                                       // بالكامل!
                                .child(
                                                MenuItem.builder("accounting.subldg",
                                                                ar ? "الحسابات المساعدة" : "Subsidiary Ledger")
                                                                .icon("pi pi-sitemap")
                                                                .outcome("/subldg_mast")
                                                                .permission("accounting.subldg.view")
                                                                .order(1).build())
                                .child(
                                                MenuItem.builder("accounting.period",
                                                                ar ? "الفترات المحاسبية" : "Periods")
                                                                .icon("pi pi-calendar")
                                                                .outcome("/views/accounting/periods")
                                                                .permission("accounting.period.manage")
                                                                .order(2).build())
                                .child(
                                                MenuItem.builder("accounting.exchrate",
                                                                ar ? "أسعار الصرف" : "Exchange Rates")
                                                                .icon("pi pi-sync")
                                                                .outcome("/views/accounting/exchange-rates")
                                                                .permission("accounting.exchrate.view")
                                                                .order(3).build())
                                .build();
                return List.of(dailyOps, masterInsertion);
        }

        @Override
        public List<Class<?>> getEntityClasses() {
                return List.of(
                                com.joud.accounting.entity.ExchangeRateHistory.class,
                                com.joud.accounting.entity.AccountingPeriod.class,
                                com.joud.accounting.entity.GeneralLedgerMaster.class,
                                com.joud.accounting.entity.JournalEntry.class,
                                com.joud.accounting.entity.DayLedger.class);
        }

        @Override
        public List<String> getPermissions() {
                return List.of("accounting.masters.view", // صلاحية رؤية تبويب الثوابت كاملاً
                                "accounting.subldg.view", // صلاحية شاشة الحسابات المساعدة
                                "accounting.journal.view",
                                "accounting.journal.create",
                                "accounting.journal.approve",
                                "accounting.journal.void",
                                "accounting.ledger.view",
                                "accounting.trial.view",
                                "accounting.reports.view",
                                "accounting.period.manage",
                                "accounting.exchrate.view",
                                "accounting.exchrate.manage");
        }

        @Override
        public void onInstall(JoudRepository db) {
                try {
                        // 1. جلب أول وكيل (Agent) من النظام بطريقة متوافقة مع كل قواعد البيانات
                        // بدلاً من استخدام ROWNUM=1 الخاصة بأوراكل فقط
                        List<AgentMast> agents = db.fetchTop("SELECT a FROM AgentMast a", AgentMast.class, 1);

                        if (agents.isEmpty()) {
                                // إذا لم يكن هناك وكيل تأسيسي، لا يمكن إنشاء فترة محاسبية
                                return;
                        }
                        AgentMast defaultAgent = agents.get(0);

                        // 2. بناء كائن الفترة المحاسبية (AccountingPeriod) برمجياً عبر الجافا
                        AccountingPeriod defaultPeriod = new AccountingPeriod();
                        defaultPeriod.setAgentId(defaultAgent.getId());

                        java.time.LocalDate today = java.time.LocalDate.now();
                        defaultPeriod.setYear(today.getYear());
                        defaultPeriod.setMonth(today.getMonthValue());

                        // حساب أول يوم وآخر يوم في الشهر الحالي برمجياً (حل عبقري بديل لـ TRUNC و
                        // LAST_DAY)[cite: 6]
                        java.time.LocalDate firstDay = today
                                        .with(java.time.temporal.TemporalAdjusters.firstDayOfMonth());
                        java.time.LocalDate lastDay = today.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());

                        defaultPeriod.setDateFrom(firstDay);
                        defaultPeriod.setDateTo(lastDay);
                        defaultPeriod.setStatus(PeriodStatus.O); // مفتوحة (Open)[cite: 6]

                        // 3. الحفظ الذهبي عبر الـ JPA
                        // هنا يتم استدعاء JoudIdGenerator تلقائياً لحساب المعرف الأول [1]
                        // وتسجيله في الـ JOUD_SEQUENCE_REGISTRY لتبدأ الحركة القادمة من [2]
                        db.persist(defaultPeriod);

                } catch (Exception e) {
                        // طباعة الخطأ في السجل الخاص بالنظام للمتابعة
                        java.util.logging.Logger.getLogger(AccountingModule.class.getName())
                                        .log(java.util.logging.Level.SEVERE,
                                                        "فشل تهيئة البيانات التأسيسية لموديول الحسابات", e);
                }
        }
        /*
         * public void onInstall(EntityManager em) {
         * 
         * // إنشاء الفترة المحاسبية الافتراضية عند أول تثبيت
         * try {
         * em.createNativeQuery(
         * "INSERT INTO ACCOUNTING_PERIOD " +
         * "(PERIOD_I_ID,AGENT_I_ID,PERIOD_I_YEAR,PERIOD_I_MONTH," +
         * " PERIOD_D_FROM,PERIOD_D_TO,PERIOD_C_STS) " +
         * "SELECT 1,AGENT_I_ID,EXTRACT(YEAR FROM SYSDATE)," +
         * "       EXTRACT(MONTH FROM SYSDATE)," +
         * "       TRUNC(SYSDATE,'MM'),LAST_DAY(SYSDATE),'O' " +
         * "FROM AGENT_MAST WHERE ROWNUM=1")
         * .executeUpdate();
         * } catch (Exception ignored) {
         * }
         * }
         */

        @Override
        public List<ReportDefinition> getReports() {
                List<ReportParameter> yearMonthParams = List.of(
                                new ReportParameter("year", "السنة", ParamType.NUMBER, true, LocalDate.now().getYear()),
                                new ReportParameter("month", "الشهر", ParamType.NUMBER, true,
                                                LocalDate.now().getMonthValue()));

                return List.of(
                                // ─── ميزان المراجعة — مستوى الشركة ──────
                                ReportDefinition.builder("accounting.trial_balance", "accounting")
                                                .titleKey("ميزان المراجعة")
                                                .sourceType(ReportDefinition.SourceType.SQL)
                                                .query("""
                                                                SELECT ACT_C_TYPE, TOTAL_DR, TOTAL_CR, NET_BAL_BASE
                                                                FROM V_TRIAL_BALANCE_COMPANY
                                                                WHERE GEN_I_YEAR = :year AND GEN_I_MONTH = :month
                                                                ORDER BY ACT_C_TYPE
                                                                """)
                                                .parameters(yearMonthParams)
                                                .permission("accounting.trial.view")
                                                .formats(List.of(ReportDefinition.ExportFormat.PDF,
                                                                ReportDefinition.ExportFormat.EXCEL))
                                                .columns(
                                                                new String[] { "كود الحساب", "إجمالي مدين",
                                                                                "إجمالي دائن",
                                                                                "الرصيد (بالعملة الأساسية)" },
                                                                new int[] { 25, 25, 25, 25 })
                                                .build(),

                                // ─── الميزانية العمومية — مستوى الشركة ──
                                ReportDefinition.builder("accounting.balance_sheet", "accounting")
                                                .titleKey("الميزانية العمومية")
                                                .sourceType(ReportDefinition.SourceType.SQL)
                                                .query("""
                                                                SELECT 'إجمالي الأصول' AS LBL, TOTAL_ASSETS AS VAL FROM V_BALANCE_SHEET_COMPANY
                                                                WHERE GEN_I_YEAR=:year AND GEN_I_MONTH=:month
                                                                UNION ALL
                                                                SELECT 'إجمالي الخصوم', TOTAL_LIABILITIES FROM V_BALANCE_SHEET_COMPANY
                                                                WHERE GEN_I_YEAR=:year AND GEN_I_MONTH=:month
                                                                UNION ALL
                                                                SELECT 'إجمالي حقوق الملكية', TOTAL_EQUITY FROM V_BALANCE_SHEET_COMPANY
                                                                WHERE GEN_I_YEAR=:year AND GEN_I_MONTH=:month
                                                                """)
                                                .parameters(yearMonthParams)
                                                .permission("accounting.reports.view")
                                                .formats(List.of(ReportDefinition.ExportFormat.PDF,
                                                                ReportDefinition.ExportFormat.EXCEL))
                                                .columns(new String[] { "البند", "المبلغ" }, new int[] { 60, 40 })
                                                .build(),

                                // ─── قائمة الدخل — مستوى الشركة ─────────
                                ReportDefinition.builder("accounting.income_statement", "accounting")
                                                .titleKey("قائمة الدخل")
                                                .sourceType(ReportDefinition.SourceType.SQL)
                                                .query("""
                                                                SELECT 'إجمالي الإيرادات' AS LBL, TOTAL_REVENUE AS VAL FROM V_INCOME_STATEMENT_COMPANY
                                                                WHERE GEN_I_YEAR=:year AND GEN_I_MONTH=:month
                                                                UNION ALL
                                                                SELECT 'إجمالي المصروفات', TOTAL_EXPENSES FROM V_INCOME_STATEMENT_COMPANY
                                                                WHERE GEN_I_YEAR=:year AND GEN_I_MONTH=:month
                                                                UNION ALL
                                                                SELECT 'صافي الربح / الخسارة', NET_PROFIT_LOSS FROM V_INCOME_STATEMENT_COMPANY
                                                                WHERE GEN_I_YEAR=:year AND GEN_I_MONTH=:month
                                                                """)
                                                .parameters(yearMonthParams)
                                                .permission("accounting.reports.view")
                                                .formats(List.of(ReportDefinition.ExportFormat.PDF,
                                                                ReportDefinition.ExportFormat.EXCEL))
                                                .columns(new String[] { "البند", "المبلغ" }, new int[] { 60, 40 })
                                                .build());
        }
}
