package com.joud.hr.bean;

import com.joud.hr.entity.PayrollMast;
import com.joud.hr.service.PayrollService;
import com.joud.core.report.engine.ReportDownloadUtil;
import com.joud.core.report.engine.ReportEngineService;
import com.joud.core.security.filter.AuthFilter;
import com.joud.core.security.session.UserSession;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * PayrollBean — مسير الرواتب
 */
@Named("payrollBean")
@ViewScoped
public class PayrollBean implements Serializable {

    // ─── فلاتر ──────────────────────────────────────
    private Long agentId;
    private Long currencyId;
    private int year = LocalDate.now().getYear();
    private int month = LocalDate.now().getMonthValue();

    // ─── البيانات ────────────────────────────────────
    private List<PayrollMast> payrollList;
    private PayrollMast selected;
    // ملخص بكل موظف: empId → {name, totalEarn, totalDed, net}
    private List<Object[]> empSummary;

    private boolean showDetail = false;

    @Inject
    private PayrollService payrollService;
    @Inject
    private ReportEngineService reportEngine;
    @PersistenceContext
    private EntityManager em;

    @PostConstruct
    public void init() {
        UserSession user = getCurrentUser();
        if (user != null)
            agentId = user.getAgentId();
        loadPayrollList();
        loadCurrencies();
    }

    // ════════════════════════════════════════════════
    // توليد المسير
    // ════════════════════════════════════════════════

    public void generate() {
        try {
            if (currencyId == null) {
                addError("يجب اختيار العملة");
                return;
            }
            UserSession user = getCurrentUser();
            Long userId = user != null ? user.getUserId() : 0L;

            PayrollMast p = payrollService.generatePayroll(
                    agentId, year, month, currencyId, userId);
            addInfo("تم توليد مسير " + p.getDescription() +
                    " — " + p.getEmployeeCount() + " موظف — صافي: " +
                    String.format("%,.2f", p.getNetPay()));
            loadPayrollList();
        } catch (PayrollService.HrException e) {
            addError(e.getMessage());
        }
    }

    // ════════════════════════════════════════════════
    // اعتماد وإلغاء
    // ════════════════════════════════════════════════

    public void approve(Long payrollId) {
        try {
            UserSession user = getCurrentUser();
            payrollService.approvePayroll(payrollId, user != null ? user.getUserId() : 0L);
            addInfo("تم اعتماد المسير");
            loadPayrollList();
        } catch (PayrollService.HrException e) {
            addError(e.getMessage());
        }
    }

    public void voidPayroll(Long payrollId) {
        try {
            UserSession user = getCurrentUser();
            payrollService.voidPayroll(payrollId, user != null ? user.getUserId() : 0L);
            addInfo("تم إلغاء المسير");
            loadPayrollList();
        } catch (PayrollService.HrException e) {
            addError(e.getMessage());
        }
    }

    // ════════════════════════════════════════════════
    // تفاصيل المسير
    // ════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    public void viewDetail(PayrollMast p) {
        selected = em.find(PayrollMast.class, p.getId());
        // ملخص كل موظف
        empSummary = em.createNativeQuery(
                "SELECT e.EMP_i_id, e.EMP_C_NAME, " +
                        "  SUM(CASE WHEN l.LINE_C_TYPE='E' THEN l.LINE_F_AMOUNT ELSE 0 END) EARN, " +
                        "  SUM(CASE WHEN l.LINE_C_TYPE='D' THEN l.LINE_F_AMOUNT ELSE 0 END) DED, " +
                        "  SUM(CASE WHEN l.LINE_C_TYPE='E' THEN  l.LINE_F_AMOUNT " +
                        "           WHEN l.LINE_C_TYPE='D' THEN -l.LINE_F_AMOUNT ELSE 0 END) NET " +
                        "FROM PAYROLL_LINE l " +
                        "JOIN EMPLOYEE_MAST e ON l.EMP_i_id = e.EMP_i_id " +
                        "WHERE l.PAY_I_ID = :pid " +
                        "GROUP BY e.EMP_i_id, e.EMP_C_NAME " +
                        "ORDER BY e.EMP_C_NAME")
                .setParameter("pid", p.getId()).getResultList();
        showDetail = true;
    }

    public void closeDetail() {
        showDetail = false;
        selected = null;
    }

    // ════════════════════════════════════════════════
    // التصدير — تفاصيل المسير المعروضة حالياً
    // ════════════════════════════════════════════════

    public void exportPdf() {
        if (selected == null || empSummary == null)
            return;
        String[] headers = { "اسم الموظف", "الاستحقاقات", "الخصومات", "الصافي" };
        int[] widths = { 34, 22, 22, 22 };
        byte[] bytes = reportEngine.exportPdf(
                "مسير الرواتب — " + selected.getDescription(),
                "الفترة: " + selected.getPeriodLabel(),
                headers, widths, buildExportRows(), buildTotalsRow());
        ReportDownloadUtil.stream(bytes,
                ReportDownloadUtil.buildFilename("مسير_رواتب_" + selected.getPeriodLabel(), "pdf"),
                ReportDownloadUtil.PDF_MIME);
    }

    public void exportExcel() {
        if (selected == null || empSummary == null)
            return;
        String[] headers = { "اسم الموظف", "الاستحقاقات", "الخصومات", "الصافي" };
        byte[] bytes = reportEngine.exportExcel(
                "مسير الرواتب — " + selected.getDescription(),
                "الفترة: " + selected.getPeriodLabel(),
                headers, buildExportRows(), buildTotalsRow());
        ReportDownloadUtil.stream(bytes,
                ReportDownloadUtil.buildFilename("مسير_رواتب_" + selected.getPeriodLabel(), "xlsx"),
                ReportDownloadUtil.XLSX_MIME);
    }

    private List<Object[]> buildExportRows() {
        List<Object[]> rows = new java.util.ArrayList<>();
        for (Object[] row : empSummary) {
            rows.add(new Object[] { row[1], row[2], row[3], row[4] });
        }
        return rows;
    }

    private Object[] buildTotalsRow() {
        return new Object[] {
                "الإجمالي",
                selected.getTotalEarnings(), selected.getTotalDeductions(), selected.getNetPay()
        };
    }

    // ════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════

    private void loadPayrollList() {
        try {
            payrollList = em.createQuery(
                    "SELECT p FROM PayrollMast p WHERE p.agentId=:a ORDER BY p.year DESC, p.month DESC",
                    PayrollMast.class).setParameter("a", agentId).getResultList();
        } catch (Exception e) {
            payrollList = List.of();
        }
    }

    private List<Object[]> currencies;

    @SuppressWarnings("unchecked")
    private void loadCurrencies() {
        try {
            currencies = em.createNativeQuery(
                    "SELECT CURRENCY_I_ID,CURRENCY_C_DESC,CURRENCY_C_SYMBOL FROM CURRENCY_MAST ORDER BY CURRENCY_C_DESC")
                    .getResultList();
        } catch (Exception e) {
            currencies = List.of();
        }
    }

    public String getMonthName(int m) {
        return payrollService.getMonthName(m);
    }

    public String getStatusLabel(PayrollMast.PayrollStatus s) {
        if (s == null)
            return "—";
        return switch (s) {
            case D -> "مسودة";
            case A -> "معتمد";
            case P -> "مدفوع";
            case V -> "ملغى";
        };
    }

    public String getStatusClass(PayrollMast.PayrollStatus s) {
        if (s == null)
            return "badge-draft";
        return switch (s) {
            case D -> "badge-draft";
            case A -> "badge-approved";
            case P -> "badge-paid";
            case V -> "badge-void";
        };
    }

    private UserSession getCurrentUser() {
        return (UserSession) FacesContext.getCurrentInstance()
                .getExternalContext().getRequestMap().get(AuthFilter.SESSION_ATTR);
    }

    private void addInfo(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO, msg, null));
    }

    private void addError(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, msg, null));
    }

    // Getters & Setters
    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long v) {
        this.agentId = v;
    }

    public Long getCurrencyId() {
        return currencyId;
    }

    public void setCurrencyId(Long v) {
        this.currencyId = v;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int v) {
        this.year = v;
    }

    public int getMonth() {
        return month;
    }

    public void setMonth(int v) {
        this.month = v;
    }

    public List<PayrollMast> getPayrollList() {
        return payrollList;
    }

    public PayrollMast getSelected() {
        return selected;
    }

    public List<Object[]> getEmpSummary() {
        return empSummary;
    }

    public boolean isShowDetail() {
        return showDetail;
    }

    public List<Object[]> getCurrencies() {
        return currencies;
    }
}
