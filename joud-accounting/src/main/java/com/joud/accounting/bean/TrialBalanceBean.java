package com.joud.accounting.bean;

import com.joud.accounting.entity.GeneralLedgerMaster;
import com.joud.accounting.service.GeneralLedgerService;
import com.joud.core.report.engine.ReportDownloadUtil;
import com.joud.core.report.engine.ReportEngineService;
import com.joud.core.security.filter.AuthFilter;
import com.joud.core.security.session.UserSession;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * TrialBalanceBean — التقارير الختامية
 * يخدم: ميزان المراجعة + الميزانية العمومية + قائمة الدخل
 */
@Named("tbBean")
@ViewScoped
public class TrialBalanceBean implements Serializable {

    // ─── فلاتر التقرير ──────────────────────────────
    private int    year    = LocalDate.now().getYear();
    private int    month   = LocalDate.now().getMonthValue();
    private Long   agentId;
    private String countryCode;
    private String reportLevel = "AGENT"; // AGENT | COUNTRY | COMPANY
    private String activeReport = "TRIAL"; // TRIAL | BALANCE_SHEET | INCOME

    // ─── بيانات التقارير ────────────────────────────
    private List<GeneralLedgerMaster> trialBalanceData;
    private List<Object[]>            balanceSheetData;
    private List<Object[]>            incomeData;

    // ─── قوائم الاختيار ─────────────────────────────
    private List<Object[]> agents;
    private List<Object[]> countries;

    // ─── إجماليات الميزان ───────────────────────────
    private BigDecimal grandTotalDr   = BigDecimal.ZERO;
    private BigDecimal grandTotalCr   = BigDecimal.ZERO;
    private BigDecimal grandTotalBal  = BigDecimal.ZERO;

    // ─── بيانات الميزانية ───────────────────────────
    private BigDecimal totalAssets      = BigDecimal.ZERO;
    private BigDecimal totalLiabilities = BigDecimal.ZERO;
    private BigDecimal totalEquity      = BigDecimal.ZERO;

    // ─── بيانات قائمة الدخل ─────────────────────────
    private BigDecimal totalRevenue     = BigDecimal.ZERO;
    private BigDecimal totalExpenses    = BigDecimal.ZERO;
    private BigDecimal netProfitLoss    = BigDecimal.ZERO;

    @Inject private GeneralLedgerService glService;
    @Inject private ReportEngineService  reportEngine;
    @PersistenceContext private EntityManager em;

    @PostConstruct
    public void init() {
        loadLookups();
        UserSession user = getCurrentUser();
        if (user != null) agentId = user.getAgentId();
        generateReport();
    }

    // ════════════════════════════════════════════════
    // توليد التقارير
    // ════════════════════════════════════════════════

    public void generateReport() {
        switch (activeReport) {
            case "TRIAL"         -> generateTrialBalance();
            case "BALANCE_SHEET" -> generateBalanceSheet();
            case "INCOME"        -> generateIncomeStatement();
        }
    }

    private void generateTrialBalance() {
        switch (reportLevel) {
            case "AGENT"   -> {
                trialBalanceData = glService.getTrialBalanceByAgent(agentId, year, month);
            }
            case "COUNTRY" -> {
                trialBalanceData = glService.getTrialBalanceByCountry(countryCode, year, month);
            }
            case "COMPANY" -> {
                // على مستوى الشركة نجمع كل السجلات
                trialBalanceData = em.createQuery(
                    "SELECT g FROM GeneralLedgerMaster g " +
                    "WHERE g.year=:y AND g.month=:m ORDER BY g.accountType",
                    GeneralLedgerMaster.class)
                    .setParameter("y", year).setParameter("m", month).getResultList();
            }
        }
        calcTrialTotals();
    }

    private void generateBalanceSheet() {
        balanceSheetData = glService.getBalanceSheetCompany(year, month);
        if (balanceSheetData != null && !balanceSheetData.isEmpty()) {
            Object[] row = balanceSheetData.get(0);
            totalAssets      = toBD(row[2]);
            totalLiabilities = toBD(row[3]);
            totalEquity      = toBD(row[4]);
        }
    }

    private void generateIncomeStatement() {
        incomeData = glService.getIncomeStatementCompany(year, month);
        if (incomeData != null && !incomeData.isEmpty()) {
            Object[] row = incomeData.get(0);
            totalRevenue  = toBD(row[2]);
            totalExpenses = toBD(row[3]);
            netProfitLoss = toBD(row[4]);
        }
    }

    private void calcTrialTotals() {
        grandTotalDr  = BigDecimal.ZERO;
        grandTotalCr  = BigDecimal.ZERO;
        grandTotalBal = BigDecimal.ZERO;
        if (trialBalanceData == null) return;

        for (GeneralLedgerMaster g : trialBalanceData) {
            grandTotalDr  = grandTotalDr.add(safe(g.getTotalDebit()));
            grandTotalCr  = grandTotalCr.add(safe(g.getTotalCredit()));
            grandTotalBal = grandTotalBal.add(g.getNetBalance());
        }
    }

    // ════════════════════════════════════════════════
    // أوامر الواجهة
    // ════════════════════════════════════════════════

    public void onLevelChange(String level) {
        this.reportLevel = level;
        generateReport();
    }

    public void onReportChange(String report) {
        this.activeReport = report;
        generateReport();
    }

    public void refresh() { generateReport(); }

    /** تصدير التقرير — يُرسَل لـ Report Engine */
    /** يُصدِّر بالضبط ما هو معروض على الشاشة حالياً (أياً كان التقرير النشط) */
    public void exportPdf() {
        ExportData ed = buildExportData();
        if (ed == null) return;
        byte[] bytes = reportEngine.exportPdf(ed.title, ed.subtitle, ed.headers, ed.widths, ed.rows, ed.totals);
        ReportDownloadUtil.stream(bytes, ReportDownloadUtil.buildFilename(ed.title, "pdf"),
            ReportDownloadUtil.PDF_MIME);
    }

    public void exportExcel() {
        ExportData ed = buildExportData();
        if (ed == null) return;
        byte[] bytes = reportEngine.exportExcel(ed.title, ed.subtitle, ed.headers, ed.rows, ed.totals);
        ReportDownloadUtil.stream(bytes, ReportDownloadUtil.buildFilename(ed.title, "xlsx"),
            ReportDownloadUtil.XLSX_MIME);
    }

    /** يحوّل بيانات الشاشة الحالية (ميزان/ميزانية/دخل) إلى صفوف جاهزة للتصدير */
    private ExportData buildExportData() {
        String period = getMonthName(month) + " " + year;

        switch (activeReport) {
            case "TRIAL" -> {
                if (trialBalanceData == null) return null;
                String[] headers = {"كود الحساب", "رصيد الافتتاح", "إجمالي مدين", "إجمالي دائن", "رصيد الإغلاق"};
                int[]    widths  = {20, 20, 20, 20, 20};
                List<Object[]> rows = new ArrayList<>();
                for (GeneralLedgerMaster g : trialBalanceData) {
                    rows.add(new Object[]{
                        g.getAccountType(),
                        g.getOpeningBalance(), g.getTotalDebit(), g.getTotalCredit(),
                        g.getClosingBalance()
                    });
                }
                Object[] totals = {"الإجمالي", null, grandTotalDr, grandTotalCr, grandTotalBal};
                return new ExportData("ميزان المراجعة", period, headers, widths, rows, totals);
            }
            case "BALANCE_SHEET" -> {
                String[] headers = {"البند", "المبلغ"};
                int[]    widths  = {60, 40};
                List<Object[]> rows = List.of(
                    new Object[]{"إجمالي الأصول", totalAssets},
                    new Object[]{"إجمالي الخصوم", totalLiabilities},
                    new Object[]{"إجمالي حقوق الملكية", totalEquity}
                );
                return new ExportData("الميزانية العمومية", period, headers, widths, rows, null);
            }
            case "INCOME" -> {
                String[] headers = {"البند", "المبلغ"};
                int[]    widths  = {60, 40};
                List<Object[]> rows = List.of(
                    new Object[]{"إجمالي الإيرادات", totalRevenue},
                    new Object[]{"إجمالي المصروفات", totalExpenses},
                    new Object[]{getNetProfitLabel(), netProfitLoss}
                );
                return new ExportData("قائمة الدخل", period, headers, widths, rows, null);
            }
            default -> { return null; }
        }
    }

    private record ExportData(String title, String subtitle, String[] headers,
                              int[] widths, List<Object[]> rows, Object[] totals) {}

    // ════════════════════════════════════════════════
    // View Helpers
    // ════════════════════════════════════════════════

    public String getBalanceClass(GeneralLedgerMaster g) {
        if (g.getClosingBalance().compareTo(BigDecimal.ZERO) == 0) return "amt-zero";
        return GeneralLedgerMaster.DrCr.D == g.getClosingDrCr() ? "amt-dr" : "amt-cr";
    }

    public String getNetProfitClass() {
        if (netProfitLoss.compareTo(BigDecimal.ZERO) >= 0) return "profit";
        return "loss";
    }

    public String getNetProfitLabel() {
        return netProfitLoss.compareTo(BigDecimal.ZERO) >= 0 ? "صافي الربح" : "صافي الخسارة";
    }

    public String formatAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) return "—";
        return String.format("%,.2f", amount.abs());
    }

    public String getMonthName(int m) {
        String[] names = {"","يناير","فبراير","مارس","أبريل","مايو","يونيو",
                          "يوليو","أغسطس","سبتمبر","أكتوبر","نوفمبر","ديسمبر"};
        return m >= 1 && m <= 12 ? names[m] : String.valueOf(m);
    }

    // ─── Lookups ────────────────────────────────────
    @SuppressWarnings("unchecked")
    private void loadLookups() {
        try {
            agents = em.createNativeQuery(
                "SELECT AGENT_I_ID, AGENT_C_NAME FROM AGENT_MAST ORDER BY AGENT_C_NAME")
                .getResultList();
            countries = em.createNativeQuery(
                "SELECT DISTINCT COUNTRY_C_CODE FROM AGENT_MAST ORDER BY COUNTRY_C_CODE")
                .getResultList();
        } catch (Exception e) {
            agents = countries = List.of();
        }
    }

    private UserSession getCurrentUser() {
        return (UserSession) FacesContext.getCurrentInstance()
            .getExternalContext().getRequestMap().get(AuthFilter.SESSION_ATTR);
    }

    private BigDecimal safe(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
    private BigDecimal toBD(Object v) {
        if (v == null) return BigDecimal.ZERO;
        return new BigDecimal(v.toString());
    }

    // ─── Getters & Setters ─────────────────────────
    public int getYear()                          { return year; }
    public void setYear(int v)                    { this.year = v; }
    public int getMonth()                         { return month; }
    public void setMonth(int v)                   { this.month = v; }
    public Long getAgentId()                      { return agentId; }
    public void setAgentId(Long v)                { this.agentId = v; }
    public String getCountryCode()                { return countryCode; }
    public void setCountryCode(String v)          { this.countryCode = v; }
    public String getReportLevel()                { return reportLevel; }
    public String getActiveReport()               { return activeReport; }
    public List<GeneralLedgerMaster> getTrialBalanceData() { return trialBalanceData; }
    public BigDecimal getGrandTotalDr()           { return grandTotalDr; }
    public BigDecimal getGrandTotalCr()           { return grandTotalCr; }
    public BigDecimal getGrandTotalBal()          { return grandTotalBal; }
    public BigDecimal getTotalAssets()            { return totalAssets; }
    public BigDecimal getTotalLiabilities()       { return totalLiabilities; }
    public BigDecimal getTotalEquity()            { return totalEquity; }
    public BigDecimal getTotalRevenue()           { return totalRevenue; }
    public BigDecimal getTotalExpenses()          { return totalExpenses; }
    public BigDecimal getNetProfitLoss()          { return netProfitLoss; }
    public List<Object[]> getAgents()             { return agents; }
    public List<Object[]> getCountries()          { return countries; }
    public boolean isProfit()                     { return netProfitLoss.compareTo(BigDecimal.ZERO) >= 0; }
}
