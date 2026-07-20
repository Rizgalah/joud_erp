package com.joud.dashboard.bean;

import com.joud.core.security.filter.AuthFilter;
import com.joud.core.security.session.UserSession;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * DashboardBean — لوحة التحكم الرئيسية
 * يجمع مؤشرات الأداء من جميع الوحدات المثبتة
 */
@Named("dashBean")
@ViewScoped
public class DashboardBean implements Serializable {

    // ─── KPI بطاقات المبيعات ────────────────────────
    private BigDecimal salesToday      = BigDecimal.ZERO;
    private BigDecimal salesMonth      = BigDecimal.ZERO;
    private BigDecimal salesLastMonth  = BigDecimal.ZERO;
    private BigDecimal salesGrowth     = BigDecimal.ZERO;
    private long       invoicesToday   = 0;
    private long       unpaidInvoices  = 0;

    // ─── KPI المخزون ────────────────────────────────
    private BigDecimal stockValue      = BigDecimal.ZERO;
    private long       totalProducts   = 0;
    private long       belowMinCount   = 0;
    private long       reorderAlerts   = 0;

    // ─── KPI الموظفين ───────────────────────────────
    private long       totalEmployees  = 0;
    private BigDecimal payrollMonth    = BigDecimal.ZERO;
    private long       pendingLeaves   = 0;
    private long       expiringDocs    = 0;

    // ─── KPI المحاسبة ───────────────────────────────
    private BigDecimal netProfitMonth  = BigDecimal.ZERO;
    private BigDecimal totalAssets     = BigDecimal.ZERO;
    private long       openPeriods     = 0;
    private long       pendingJVs      = 0;

    // ─── بيانات الرسوم البيانية ──────────────────────
    // مبيعات آخر 6 أشهر
    private List<String>     chartMonthLabels  = new ArrayList<>();
    private List<BigDecimal> chartSalesData    = new ArrayList<>();
    private List<BigDecimal> chartPurchaseData = new ArrayList<>();

    // توزيع المخزون حسب الفئة
    private List<String>     stockCatLabels = new ArrayList<>();
    private List<BigDecimal> stockCatValues = new ArrayList<>();

    // ─── تنبيهات ────────────────────────────────────
    private List<AlertItem>  alerts = new ArrayList<>();

    // ─── آخر الفواتير ───────────────────────────────
    private List<Object[]>   recentInvoices   = new ArrayList<>();
    private List<Object[]>   recentMovements  = new ArrayList<>();

    private Long agentId;

    @PersistenceContext
    private EntityManager em;

    @PostConstruct
    public void init() {
        UserSession user = getCurrentUser();
        if (user != null) agentId = user.getAgentId();
        loadSalesKPIs();
        loadInventoryKPIs();
        loadHrKPIs();
        loadAccountingKPIs();
        loadChartData();
        loadAlerts();
        loadRecentData();
    }

    // ════════════════════════════════════════════════
    // تحميل البيانات
    // ════════════════════════════════════════════════

    private void loadSalesKPIs() {
        try {
            LocalDate today = LocalDate.now();
            int year = today.getYear(), month = today.getMonthValue();
            int lastYear  = month == 1 ? year - 1 : year;
            int lastMonth = month == 1 ? 12 : month - 1;

            // مبيعات اليوم
            Object r = em.createNativeQuery(
                "SELECT NVL(SUM(total),0) FROM invoices " +
                "WHERE AGENT_I_ID=:a AND invoice_c_type='sale' " +
                "AND status='posted' AND invoice_d_date=:d")
                .setParameter("a", agentId).setParameter("d", today)
                .getSingleResult();
            salesToday = toBD(r);

            // مبيعات الشهر
            r = em.createNativeQuery(
                "SELECT NVL(SUM(total),0) FROM invoices " +
                "WHERE AGENT_I_ID=:a AND invoice_c_type='sale' AND status='posted' " +
                "AND EXTRACT(YEAR FROM invoice_d_date)=:y " +
                "AND EXTRACT(MONTH FROM invoice_d_date)=:m")
                .setParameter("a",agentId).setParameter("y",year).setParameter("m",month)
                .getSingleResult();
            salesMonth = toBD(r);

            // مبيعات الشهر الماضي
            r = em.createNativeQuery(
                "SELECT NVL(SUM(total),0) FROM invoices " +
                "WHERE AGENT_I_ID=:a AND invoice_c_type='sale' AND status='posted' " +
                "AND EXTRACT(YEAR FROM invoice_d_date)=:y " +
                "AND EXTRACT(MONTH FROM invoice_d_date)=:m")
                .setParameter("a",agentId).setParameter("y",lastYear).setParameter("m",lastMonth)
                .getSingleResult();
            salesLastMonth = toBD(r);

            // نسبة النمو
            if (salesLastMonth.compareTo(BigDecimal.ZERO) > 0) {
                salesGrowth = salesMonth.subtract(salesLastMonth)
                    .multiply(new BigDecimal("100"))
                    .divide(salesLastMonth, 1, java.math.RoundingMode.HALF_UP);
            }

            // فواتير اليوم
            invoicesToday = toLong(em.createNativeQuery(
                "SELECT COUNT(*) FROM invoices WHERE AGENT_I_ID=:a " +
                "AND status='posted' AND invoice_d_date=:d")
                .setParameter("a",agentId).setParameter("d",today).getSingleResult());

            // فواتير غير مسددة
            unpaidInvoices = toLong(em.createNativeQuery(
                "SELECT COUNT(*) FROM invoices WHERE AGENT_I_ID=:a " +
                "AND invoice_f_balance>0 AND status='posted'")
                .setParameter("a",agentId).getSingleResult());

        } catch (Exception e) { /* وحدة التجارة غير مثبتة */ }
    }

    private void loadInventoryKPIs() {
        try {
            stockValue = toBD(em.createNativeQuery(
                "SELECT NVL(SUM(current_quantity*prod_f_avg_cost),0) " +
                "FROM products WHERE AGENT_I_ID=:a AND prod_c_status='A'")
                .setParameter("a",agentId).getSingleResult());

            totalProducts = toLong(em.createNativeQuery(
                "SELECT COUNT(*) FROM products WHERE AGENT_I_ID=:a AND prod_c_status='A'")
                .setParameter("a",agentId).getSingleResult());

            belowMinCount = toLong(em.createNativeQuery(
                "SELECT COUNT(*) FROM products " +
                "WHERE current_quantity<=min_quantity AND prod_c_status='A' AND prod_c_track_stock='Y'")
                .getSingleResult());

            reorderAlerts = toLong(em.createNativeQuery(
                "SELECT COUNT(*) FROM REORDER_ALERT WHERE AGENT_I_ID=:a AND ALERT_C_STS='N'")
                .setParameter("a",agentId).getSingleResult());

        } catch (Exception e) { /* وحدة المخزون غير مثبتة */ }
    }

    private void loadHrKPIs() {
        try {
            totalEmployees = toLong(em.createNativeQuery(
                "SELECT COUNT(*) FROM EMPLOYEE_MAST WHERE AGENT_I_ID=:a AND EMP_C_STATUS='A'")
                .setParameter("a",agentId).getSingleResult());

            // رواتب الشهر الحالي
            int month = LocalDate.now().getMonthValue();
            int year  = LocalDate.now().getYear();
            try {
                payrollMonth = toBD(em.createNativeQuery(
                    "SELECT NVL(PAY_F_NET_PAY,0) FROM PAYROLL_MAST " +
                    "WHERE AGENT_I_ID=:a AND PAY_I_YEAR=:y AND PAY_I_MONTH=:m AND PAY_C_STATUS<>'V'")
                    .setParameter("a",agentId).setParameter("y",year).setParameter("m",month)
                    .getSingleResult());
            } catch (Exception ignored) {}

            // إجازات معلقة
            try {
                pendingLeaves = toLong(em.createNativeQuery(
                    "SELECT COUNT(*) FROM LEAVE_REQUEST WHERE AGENT_I_ID=:a AND LVR_C_STATUS='P'")
                    .setParameter("a",agentId).getSingleResult());
            } catch (Exception ignored) {}

            // وثائق تنتهي خلال 30 يوم
            try {
                LocalDate limit = LocalDate.now().plusDays(30);
                expiringDocs = toLong(em.createNativeQuery(
                    "SELECT COUNT(*) FROM EMPLOYEE_MAST WHERE AGENT_I_ID=:a AND EMP_C_STATUS='A' " +
                    "AND (EMP_D_ID_EXP<=:l OR EMP_D_PASS_EXP<=:l)")
                    .setParameter("a",agentId).setParameter("l",limit).getSingleResult());
            } catch (Exception ignored) {}

        } catch (Exception e) { /* وحدة HR غير مثبتة */ }
    }

    private void loadAccountingKPIs() {
        try {
            int year = LocalDate.now().getYear(), month = LocalDate.now().getMonthValue();

            // صافي الربح من GENLDG_MAST
            try {
                Object r = em.createNativeQuery(
                    "SELECT NVL(SUM(CASE WHEN lg.GROUP_C_TYPE='I' " +
                    "  THEN CASE g.GEN_C_CL_DRCR_BASE WHEN 'C' THEN g.GEN_F_CL_BAL_BASE ELSE -g.GEN_F_CL_BAL_BASE END " +
                    "WHEN lg.GROUP_C_TYPE='X' " +
                    "  THEN CASE g.GEN_C_CL_DRCR_BASE WHEN 'D' THEN -g.GEN_F_CL_BAL_BASE ELSE g.GEN_F_CL_BAL_BASE END " +
                    "ELSE 0 END),0) " +
                    "FROM GENLDG_MAST g " +
                    "JOIN ACCOUNT_MAST a ON g.ACT_I_ID=a.ACT_I_ID " +
                    "JOIN SUBLDG_MAST s ON a.SUB_I_ID=s.SUB_I_ID " +
                    "JOIN MAINLDG_MAST ml ON s.LDG_I_ID=ml.LDG_I_ID " +
                    "JOIN LEDGER_GROUP lg ON ml.GROUP_I_ID=lg.GROUP_I_ID " +
                    "WHERE g.AGENT_I_ID=:a AND g.GEN_I_YEAR=:y AND g.GEN_I_MONTH=:m " +
                    "AND lg.GROUP_C_TYPE IN('I','X')")
                    .setParameter("a",agentId).setParameter("y",year).setParameter("m",month)
                    .getSingleResult();
                netProfitMonth = toBD(r);
            } catch (Exception ignored) {}

            // فترات مفتوحة
            openPeriods = toLong(em.createNativeQuery(
                "SELECT COUNT(*) FROM ACCOUNTING_PERIOD WHERE AGENT_I_ID=:a AND PERIOD_C_STS='O'")
                .setParameter("a",agentId).getSingleResult());

            // قيود معلقة
            pendingJVs = toLong(em.createNativeQuery(
                "SELECT COUNT(*) FROM JOURNAL_ENTRY WHERE AGENT_I_ID=:a AND JV_C_STS='P'")
                .setParameter("a",agentId).getSingleResult());

        } catch (Exception e) { /* وحدة المحاسبة غير مثبتة */ }
    }

    @SuppressWarnings("unchecked")
    private void loadChartData() {
        // مبيعات آخر 6 أشهر
        LocalDate now = LocalDate.now();
        for (int i = 5; i >= 0; i--) {
            LocalDate d = now.minusMonths(i);
            int y = d.getYear(), m = d.getMonthValue();
            chartMonthLabels.add(d.format(DateTimeFormatter.ofPattern("MMM yyyy",
                java.util.Locale.forLanguageTag("ar"))));

            try {
                Object sales = em.createNativeQuery(
                    "SELECT NVL(SUM(total),0) FROM invoices " +
                    "WHERE AGENT_I_ID=:a AND invoice_c_type='sale' AND status='posted' " +
                    "AND EXTRACT(YEAR FROM invoice_d_date)=:y AND EXTRACT(MONTH FROM invoice_d_date)=:m")
                    .setParameter("a",agentId).setParameter("y",y).setParameter("m",m).getSingleResult();
                chartSalesData.add(toBD(sales));

                Object purch = em.createNativeQuery(
                    "SELECT NVL(SUM(total),0) FROM invoices " +
                    "WHERE AGENT_I_ID=:a AND invoice_c_type='purchase' AND status='posted' " +
                    "AND EXTRACT(YEAR FROM invoice_d_date)=:y AND EXTRACT(MONTH FROM invoice_d_date)=:m")
                    .setParameter("a",agentId).setParameter("y",y).setParameter("m",m).getSingleResult();
                chartPurchaseData.add(toBD(purch));
            } catch (Exception e) {
                chartSalesData.add(BigDecimal.ZERO);
                chartPurchaseData.add(BigDecimal.ZERO);
            }
        }

        // توزيع المخزون حسب الفئة
        try {
            List<Object[]> cats = em.createNativeQuery(
                "SELECT c.categ_c_name, NVL(SUM(p.current_quantity*p.prod_f_avg_cost),0) " +
                "FROM products p LEFT JOIN categories c ON p.categ_i_id=c.categ_i_id " +
                "WHERE p.prod_c_status='A' AND p.prod_c_track_stock='Y' " +
                "GROUP BY c.categ_c_name ORDER BY 2 DESC")
                .setMaxResults(6).getResultList();
            for (Object[] row : cats) {
                stockCatLabels.add(row[0] != null ? row[0].toString() : "أخرى");
                stockCatValues.add(toBD(row[1]));
            }
        } catch (Exception ignored) {}
    }

    private void loadAlerts() {
        // تنبيهات المخزون
        if (belowMinCount > 0)
            alerts.add(new AlertItem("warning", "مخزون",
                belowMinCount + " صنف تحت الحد الأدنى", "/views/inventory/products"));
        if (reorderAlerts > 0)
            alerts.add(new AlertItem("danger", "إعادة طلب",
                reorderAlerts + " تنبيه إعادة طلب جديد", "/views/inventory/products"));

        // تنبيهات المبيعات
        if (unpaidInvoices > 0)
            alerts.add(new AlertItem("warning", "فواتير",
                unpaidInvoices + " فاتورة غير مسددة", "/views/trade/payments"));

        // تنبيهات الموظفين
        if (expiringDocs > 0)
            alerts.add(new AlertItem("warning", "وثائق",
                expiringDocs + " وثيقة موظف تنتهي قريباً", "/views/hr/employees"));
        if (pendingLeaves > 0)
            alerts.add(new AlertItem("info", "إجازات",
                pendingLeaves + " طلب إجازة ينتظر الموافقة", "/views/hr/employees"));

        // تنبيهات المحاسبة
        if (pendingJVs > 0)
            alerts.add(new AlertItem("info", "قيود",
                pendingJVs + " قيد يومي ينتظر الاعتماد", "/views/accounting/journal-entry"));
    }

    @SuppressWarnings("unchecked")
    private void loadRecentData() {
        try {
            recentInvoices = em.createNativeQuery(
                "SELECT i.invoice_number, p.parties_c_name, i.total, " +
                "i.invoice_f_balance, i.status, i.invoice_d_date " +
                "FROM invoices i JOIN parties p ON i.party_id=p.parties_i_id " +
                "WHERE i.AGENT_I_ID=:a AND i.invoice_c_type='sale' " +
                "ORDER BY i.invoice_d_date DESC")
                .setParameter("a",agentId).setMaxResults(8).getResultList();
        } catch (Exception ignored) {}

        try {
            recentMovements = em.createNativeQuery(
                "SELECT sm.MOVE_D_DATE, sm.MOVE_C_TYPE, p.prod_c_name, " +
                "sm.MOVE_I_QTY, sm.MOVE_F_QTY_AFTER " +
                "FROM STOCK_MOVEMENTS sm JOIN products p ON sm.PROD_I_ID=p.prod_i_id " +
                "WHERE sm.AGENT_I_ID=:a ORDER BY sm.CREATED_AT DESC")
                .setParameter("a",agentId).setMaxResults(8).getResultList();
        } catch (Exception ignored) {}
    }

    // ─── Helpers ───────────────────────────────────
    private BigDecimal toBD(Object v) {
        if (v == null) return BigDecimal.ZERO;
        try { return new BigDecimal(v.toString()); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }
    private long toLong(Object v) {
        if (v == null) return 0;
        try { return ((Number) v).longValue(); }
        catch (Exception e) { return 0; }
    }

    public String formatGrowth() {
        String sign = salesGrowth.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
        return sign + salesGrowth.toPlainString() + "%";
    }
    public boolean isGrowthPositive() {
        return salesGrowth.compareTo(BigDecimal.ZERO) >= 0;
    }
    public boolean isProfitPositive() {
        return netProfitMonth.compareTo(BigDecimal.ZERO) >= 0;
    }

    private UserSession getCurrentUser() {
        return (UserSession) FacesContext.getCurrentInstance()
            .getExternalContext().getRequestMap().get(AuthFilter.SESSION_ATTR);
    }

    // ─── Inner Record ──────────────────────────────
    public record AlertItem(String level, String module, String message, String link) {}

    // ─── Getters ───────────────────────────────────
    public BigDecimal getSalesToday()            { return salesToday; }
    public BigDecimal getSalesMonth()            { return salesMonth; }
    public BigDecimal getSalesLastMonth()        { return salesLastMonth; }
    public BigDecimal getSalesGrowth()           { return salesGrowth; }
    public long getInvoicesToday()               { return invoicesToday; }
    public long getUnpaidInvoices()              { return unpaidInvoices; }
    public BigDecimal getStockValue()            { return stockValue; }
    public long getTotalProducts()               { return totalProducts; }
    public long getBelowMinCount()               { return belowMinCount; }
    public long getReorderAlerts()               { return reorderAlerts; }
    public long getTotalEmployees()              { return totalEmployees; }
    public BigDecimal getPayrollMonth()          { return payrollMonth; }
    public long getPendingLeaves()               { return pendingLeaves; }
    public long getExpiringDocs()                { return expiringDocs; }
    public BigDecimal getNetProfitMonth()        { return netProfitMonth; }
    public BigDecimal getTotalAssets()           { return totalAssets; }
    public long getOpenPeriods()                 { return openPeriods; }
    public long getPendingJVs()                  { return pendingJVs; }
    public List<String> getChartMonthLabels()    { return chartMonthLabels; }
    public List<BigDecimal> getChartSalesData()  { return chartSalesData; }
    public List<BigDecimal> getChartPurchaseData(){ return chartPurchaseData; }
    public List<String> getStockCatLabels()      { return stockCatLabels; }
    public List<BigDecimal> getStockCatValues()  { return stockCatValues; }
    public List<AlertItem> getAlerts()           { return alerts; }
    public List<Object[]> getRecentInvoices()    { return recentInvoices; }
    public List<Object[]> getRecentMovements()   { return recentMovements; }
}
