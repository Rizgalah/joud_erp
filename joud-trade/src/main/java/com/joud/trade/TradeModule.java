package com.joud.trade;

import com.joud.core.menu.MenuItem;
import com.joud.core.module.JoudModule;
import com.joud.core.report.ReportDefinition;
import com.joud.core.report.ReportParameter;
import com.joud.core.report.ReportParameter.ParamType;
import com.joud.trade.entity.*;

import com.joud.core.db.JoudRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * TradeModule — تسجيل وحدة التجارة (مبيعات + مشتريات)
 */
public class TradeModule implements JoudModule {

    @Override public String getModuleId()         { return "trade"; }
    @Override public String getVersion()          { return "1.0.0"; }
    @Override public String getModuleName(Locale l) {
        return "ar".equals(l.getLanguage()) ? "المبيعات والمشتريات" : "Trade";
    }
    @Override public String getDescription(Locale l) {
        return "فواتير المبيعات والمشتريات، إدارة العملاء والموردين، المدفوعات";
    }
    @Override public int getMenuOrder() { return 40; }

    @Override
    public List<MenuItem> getMenuItems(Locale locale) {
        boolean ar = "ar".equals(locale.getLanguage());
        return List.of(
            MenuItem.builder("trade.root", ar ? "المبيعات والمشتريات" : "Trade")
                .icon("pi pi-shopping-cart").order(40)
                .child(MenuItem.builder("trade.sales", ar ? "فواتير المبيعات" : "Sales")
                    .icon("pi pi-arrow-up-right")
                    .outcome("/views/trade/invoice-form")
                    .permission("trade.invoice.sale").order(1).build())
                .child(MenuItem.builder("trade.purchases", ar ? "فواتير المشتريات" : "Purchases")
                    .icon("pi pi-arrow-down-left")
                    .outcome("/views/trade/invoice-form")
                    .permission("trade.invoice.purchase").order(2).build())
                .child(MenuItem.builder("trade.payments", ar ? "المدفوعات والمقبوضات" : "Payments")
                    .icon("pi pi-credit-card")
                    .outcome("/views/trade/payments")
                    .permission("trade.payment.view").order(3).build())
                .child(MenuItem.builder("trade.parties", ar ? "العملاء والموردون" : "Parties")
                    .icon("pi pi-users")
                    .outcome("/views/trade/parties")
                    .permission("trade.party.view").order(4).build())
                .build()
        );
    }

    @Override
    public List<Class<?>> getEntityClasses() {
        return List.of(Party.class, Invoice.class, InvoiceItem.class, Payment.class);
    }

    @Override
    public List<String> getPermissions() {
        return List.of(
            "trade.invoice.sale",    "trade.invoice.purchase",
            "trade.invoice.post",    "trade.invoice.cancel",
            "trade.payment.view",    "trade.payment.create",
            "trade.party.view",      "trade.party.create",
            "trade.party.edit",      "trade.reports.view"
        );
    }

    @Override
    public List<String> getRequiredModules() {
        return List.of("accounting"); // لتوليد القيود المحاسبية
    }

    @Override
    public void onInstall(JoudRepository db) {
        // إنشاء تسلسلات الفواتير الأولية لكل نوع
        try {
            for (String type : List.of("sale","purchase","sale_return","purchase_return")) {
                db.executeUpdate(
                    "INSERT INTO INVOICE_SEQUENCE " +
                    "(SEQ_I_ID,AGENT_I_ID,SEQ_C_TYPE,SEQ_I_YEAR,SEQ_I_CURRENT) " +
                    "SELECT ROWNUM,AGENT_I_ID,?1,EXTRACT(YEAR FROM SYSDATE),0 " +
                    "FROM AGENT_MAST", type);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public List<ReportDefinition> getReports() {
        List<ReportParameter> yearMonth = List.of(
            new ReportParameter("year",  "السنة", ParamType.NUMBER, true, LocalDate.now().getYear()),
            new ReportParameter("month", "الشهر", ParamType.NUMBER, true, LocalDate.now().getMonthValue())
        );

        return List.of(
            // ─── تقرير المبيعات الشهري ──────────────
            ReportDefinition.builder("trade.sales_report", "trade")
                .titleKey("تقرير المبيعات الشهري")
                .sourceType(ReportDefinition.SourceType.SQL)
                .query("""
                    SELECT CURRENCY_I_ID, INVOICE_COUNT, TOTAL_SALES,
                           TOTAL_DISC, TOTAL_TAX, TOTAL_PAID, TOTAL_BALANCE
                    FROM V_SALES_MONTHLY
                    WHERE SALE_YEAR = :year AND SALE_MONTH = :month
                    ORDER BY CURRENCY_I_ID
                    """)
                .parameters(yearMonth)
                .permission("trade.reports.view")
                .formats(List.of(ReportDefinition.ExportFormat.PDF, ReportDefinition.ExportFormat.EXCEL))
                .columns(
                    new String[]{"العملة", "عدد الفواتير", "إجمالي المبيعات", "الخصم", "الضريبة", "المدفوع", "المتبقي"},
                    new int[]{12, 13, 18, 13, 13, 15, 16})
                .build(),

            // ─── تقرير المشتريات الشهري ─────────────
            ReportDefinition.builder("trade.purchase_report", "trade")
                .titleKey("تقرير المشتريات الشهري")
                .sourceType(ReportDefinition.SourceType.SQL)
                .query("""
                    SELECT CURRENCY_I_ID, COUNT(*) AS CNT, SUM(total) AS TOTAL,
                           SUM(invoice_f_paid) AS PAID, SUM(invoice_f_balance) AS BAL
                    FROM invoices
                    WHERE invoice_c_type='purchase' AND status='posted'
                      AND EXTRACT(YEAR FROM invoice_d_date)=:year
                      AND EXTRACT(MONTH FROM invoice_d_date)=:month
                    GROUP BY CURRENCY_I_ID
                    ORDER BY CURRENCY_I_ID
                    """)
                .parameters(yearMonth)
                .permission("trade.reports.view")
                .formats(List.of(ReportDefinition.ExportFormat.PDF, ReportDefinition.ExportFormat.EXCEL))
                .columns(
                    new String[]{"العملة", "عدد الفواتير", "إجمالي المشتريات", "المدفوع", "المتبقي"},
                    new int[]{18, 18, 22, 21, 21})
                .build(),

            // ─── كشف حساب طرف ───────────────────────
            ReportDefinition.builder("trade.party_statement", "trade")
                .titleKey("كشف حساب عميل / مورد")
                .sourceType(ReportDefinition.SourceType.SQL)
                .query("""
                    SELECT invoice_number, invoice_c_type, invoice_d_date,
                           total, invoice_f_paid, invoice_f_balance
                    FROM V_PARTY_STATEMENT
                    WHERE parties_i_id = :partyId
                    ORDER BY invoice_d_date
                    """)
                .parameters(List.of(
                    new ReportParameter("partyId", "كود العميل/المورد", ParamType.NUMBER, true, null)))
                .permission("trade.reports.view")
                .formats(List.of(ReportDefinition.ExportFormat.PDF, ReportDefinition.ExportFormat.EXCEL))
                .columns(
                    new String[]{"رقم الفاتورة", "النوع", "التاريخ", "الإجمالي", "المدفوع", "الرصيد"},
                    new int[]{18, 17, 15, 18, 16, 16})
                .build(),

            // ─── أكثر الأصناف مبيعاً ────────────────
            ReportDefinition.builder("trade.top_products", "trade")
                .titleKey("أكثر الأصناف مبيعاً")
                .sourceType(ReportDefinition.SourceType.SQL)
                .query("""
                    SELECT product_name, TOTAL_QTY, TOTAL_AMOUNT, INVOICE_COUNT
                    FROM V_TOP_PRODUCTS
                    ORDER BY TOTAL_AMOUNT DESC
                    """)
                .permission("trade.reports.view")
                .formats(List.of(ReportDefinition.ExportFormat.PDF, ReportDefinition.ExportFormat.EXCEL))
                .columns(
                    new String[]{"اسم الصنف", "الكمية المباعة", "إجمالي القيمة", "عدد الفواتير"},
                    new int[]{40, 20, 22, 18})
                .build()
        );
    }
}
