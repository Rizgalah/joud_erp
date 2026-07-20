package com.joud.core.report.bean;

import com.joud.core.module.PluginRegistry;
import com.joud.core.report.ReportDefinition;
import com.joud.core.report.ReportParameter;
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

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ReportsBean — مركز التقارير الموحَّد
 *
 * يعرض كل التقارير المسجَّلة عبر JoudModule.getReports() من جميع
 * الوحدات المثبتة، مجمَّعة حسب الوحدة ومفلترة بصلاحيات المستخدم.
 * يبني نموذج معاملات ديناميكياً من ReportParameter لكل تقرير،
 * يعرض معاينة الصفوف، ثم يُصدِّر PDF أو Excel عبر ReportEngineService.
 */
@Named("reportsBean")
@ViewScoped
public class ReportsBean implements Serializable {

    @Inject private PluginRegistry        registry;
    @Inject private ReportEngineService   engine;

    // ─── القائمة المجمَّعة حسب الوحدة ────────────────
    private Map<String, List<ReportDefinition>> reportsByModule = new LinkedHashMap<>();

    // ─── التقرير المختار وحالته ──────────────────────
    private ReportDefinition selected;
    private Map<String, Object> paramValues = new HashMap<>();
    private boolean showParamDialog = false;
    private boolean hasPreview      = false;

    // ─── نتائج المعاينة ──────────────────────────────
    private List<Object[]> previewRows  = new ArrayList<>();
    private List<DynamicColumn> previewColumns = new ArrayList<>();
    private Object[] totalsRow;

    @PostConstruct
    public void init() {
        loadReports();
    }

    // ════════════════════════════════════════════════
    // تحميل التقارير المسموح بها
    // ════════════════════════════════════════════════

    private void loadReports() {
        UserSession user = getCurrentUser();
        List<ReportDefinition> all = registry.getAllReports();

        reportsByModule = all.stream()
            .filter(r -> user == null || r.getPermission() == null
                      || r.getPermission().isBlank()
                      || user.hasPermission(r.getPermission()))
            .collect(Collectors.groupingBy(
                ReportDefinition::getModuleId, LinkedHashMap::new, Collectors.toList()));
    }

    // ════════════════════════════════════════════════
    // اختيار تقرير وتجهيز نموذج المعاملات
    // ════════════════════════════════════════════════

    public void selectReport(ReportDefinition def) {
        this.selected   = def;
        this.paramValues = new HashMap<>();
        this.hasPreview  = false;
        this.previewRows = new ArrayList<>();
        this.previewColumns = new ArrayList<>();
        this.totalsRow   = null;

        // تعبئة القيم الافتراضية لكل معامل
        for (ReportParameter p : def.getParameters()) {
            Object def0 = p.getDefaultValue();
            if (def0 == null && p.getType() == ReportParameter.ParamType.DATE) {
                def0 = LocalDate.now();
            }
            paramValues.put(p.getKey(), def0);
        }
        showParamDialog = true;
    }

    public void closeDialog() { showParamDialog = false; }

    // ════════════════════════════════════════════════
    // تشغيل المعاينة
    // ════════════════════════════════════════════════

    public void runPreview() {
        if (selected == null) return;
        if (!validateRequired()) return;

        try {
            previewRows = engine.executeQuery(selected, paramValues);
            buildPreviewColumns();
            totalsRow   = computeTotals(previewRows);
            hasPreview  = true;
            if (previewRows.isEmpty()) {
                addInfo("لا توجد بيانات مطابقة لمعاملات البحث");
            }
        } catch (ReportEngineService.ReportException e) {
            addError(e.getMessage());
            hasPreview = false;
        } catch (Exception e) {
            addError("خطأ غير متوقع: " + e.getMessage());
            hasPreview = false;
        }
    }

    private void buildPreviewColumns() {
        previewColumns.clear();
        String[] headers = selected.getColumnHeaders();
        for (int i = 0; i < headers.length; i++) {
            previewColumns.add(new DynamicColumn(i, headers[i]));
        }
    }

    /** إجمالي تلقائي: يجمع كل عمود رقمي، يترك الأعمدة النصية فارغة باستثناء أول عمود = "الإجمالي" */
    private Object[] computeTotals(List<Object[]> rows) {
        if (rows.isEmpty()) return null;
        int cols = rows.get(0).length;
        Object[] totals = new Object[cols];
        boolean anyNumeric = false;

        for (int c = 0; c < cols; c++) {
            BigDecimal sum = BigDecimal.ZERO;
            boolean numericCol = true;
            for (Object[] row : rows) {
                Object v = row[c];
                if (v == null) continue;
                if (v instanceof BigDecimal bd)      sum = sum.add(bd);
                else if (v instanceof Number n)       sum = sum.add(BigDecimal.valueOf(n.doubleValue()));
                else { numericCol = false; break; }
            }
            if (numericCol && sum.compareTo(BigDecimal.ZERO) != 0) {
                totals[c] = sum; anyNumeric = true;
            }
        }
        if (!anyNumeric) return null;
        totals[0] = totals[0] == null ? "الإجمالي" : totals[0];
        return totals;
    }

    // ════════════════════════════════════════════════
    // التصدير
    // ════════════════════════════════════════════════

    public void exportPdf() {
        if (!ensurePreview()) return;
        try {
            byte[] bytes = engine.exportPdf(selected,
                new ReportEngineService.ReportColumns(
                    resolveTitle(), buildSubtitle(),
                    selected.getColumnHeaders(), selected.getColumnWidthsPct()),
                paramValues, totalsRow);
            String fname = ReportDownloadUtil.buildFilename(resolveTitle(), "pdf");
            ReportDownloadUtil.stream(bytes, fname, ReportDownloadUtil.PDF_MIME);
        } catch (ReportEngineService.ReportException e) {
            addError(e.getMessage());
        }
    }

    public void exportExcel() {
        if (!ensurePreview()) return;
        try {
            byte[] bytes = engine.exportExcel(selected,
                new ReportEngineService.ReportColumns(
                    resolveTitle(), buildSubtitle(),
                    selected.getColumnHeaders(), selected.getColumnWidthsPct()),
                paramValues, totalsRow);
            String fname = ReportDownloadUtil.buildFilename(resolveTitle(), "xlsx");
            ReportDownloadUtil.stream(bytes, fname, ReportDownloadUtil.XLSX_MIME);
        } catch (ReportEngineService.ReportException e) {
            addError(e.getMessage());
        }
    }

    private boolean ensurePreview() {
        if (selected == null) return false;
        if (!validateRequired()) return false;
        if (!hasPreview) runPreview();
        return true;
    }

    // ════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════

    private boolean validateRequired() {
        for (ReportParameter p : selected.getParameters()) {
            Object v = paramValues.get(p.getKey());
            if (p.isRequired() && (v == null || v.toString().isBlank())) {
                addError("الحقل مطلوب: " + p.getLabelKey());
                return false;
            }
        }
        return true;
    }

    /** في غياب نظام i18n كامل، titleKey يحمل العنوان العربي الجاهز مباشرة */
    private String resolveTitle() {
        return selected.getTitleKey();
    }

    private String buildSubtitle() {
        if (selected.getParameters().isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (ReportParameter p : selected.getParameters()) {
            Object v = paramValues.get(p.getKey());
            if (v == null) continue;
            if (sb.length() > 0) sb.append("   |   ");
            sb.append(p.getLabelKey()).append(": ").append(v);
        }
        return sb.toString();
    }

    private UserSession getCurrentUser() {
        return (UserSession) FacesContext.getCurrentInstance()
            .getExternalContext().getRequestMap().get(AuthFilter.SESSION_ATTR);
    }
    private void addInfo(String m) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(FacesMessage.SEVERITY_INFO, m, null));
    }
    private void addError(String m) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(FacesMessage.SEVERITY_ERROR, m, null));
    }

    // ─── Helper لعرض الأعمدة الديناميكية في p:columns ──
    public record DynamicColumn(int index, String header) {}

    // ─── Getters & Setters ─────────────────────────
    public Map<String, List<ReportDefinition>> getReportsByModule() { return reportsByModule; }
    public ReportDefinition getSelected()             { return selected; }
    public Map<String, Object> getParamValues()       { return paramValues; }
    public boolean isShowParamDialog()                { return showParamDialog; }
    public boolean isHasPreview()                     { return hasPreview; }
    public List<Object[]> getPreviewRows()            { return previewRows; }
    public List<DynamicColumn> getPreviewColumns()    { return previewColumns; }
    public Object[] getTotalsRow()                    { return totalsRow; }

    public String getModuleLabel(String moduleId) {
        return registry.getModule(moduleId)
            .map(m -> m.getModuleName(Locale.of("ar")))
            .orElse(moduleId);
    }
}
