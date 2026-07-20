package com.joud.core.report;

import java.util.List;

/**
 * ReportDefinition — وصف تقرير مسجل في ماكنة التقارير
 *
 * كل وحدة تسجل تقاريرها عبر JoudModule.getReports().
 * Report Engine يجمعها ويعرضها في شاشة التقارير الموحدة.
 */
public class ReportDefinition {

    /** نوع مصدر البيانات */
    public enum SourceType { JPQL, SQL, BEAN }

    /** صيغ التصدير المتاحة */
    public enum ExportFormat { PDF, EXCEL, HTML, CSV }

    private final String id;              // معرف فريد (e.g. "hr.payroll.monthly")
    private final String moduleId;        // الوحدة المالكة (e.g. "hr")
    private final String titleKey;        // مفتاح الترجمة للعنوان
    private final String templatePath;    // مسار قالب JasperReports (.jrxml)
    private final SourceType sourceType;  // نوع مصدر البيانات
    private final String query;           // JPQL أو SQL لجلب البيانات
    private final List<ExportFormat> formats;     // صيغ التصدير المدعومة
    private final List<ReportParameter> parameters; // معاملات الفلترة
    private final String permission;      // صلاحية مطلوبة لتشغيل التقرير
    private final String[] columnHeaders; // عناوين أعمدة الجدول (بالترتيب نفسه لنتيجة الاستعلام)
    private final int[] columnWidthsPct;  // نسب عرض كل عمود (تجمع 100) — لتصدير PDF فقط

    private ReportDefinition(Builder builder) {
        this.id          = builder.id;
        this.moduleId    = builder.moduleId;
        this.titleKey    = builder.titleKey;
        this.templatePath = builder.templatePath;
        this.sourceType  = builder.sourceType;
        this.query       = builder.query;
        this.formats     = builder.formats;
        this.parameters  = builder.parameters;
        this.permission  = builder.permission;
        this.columnHeaders   = builder.columnHeaders;
        this.columnWidthsPct = builder.columnWidthsPct;
    }

    // ─── Getters ───────────────────────────────────
    public String getId()                      { return id; }
    public String getModuleId()                { return moduleId; }
    public String getTitleKey()                { return titleKey; }
    public String getTemplatePath()            { return templatePath; }
    public SourceType getSourceType()          { return sourceType; }
    public String getQuery()                   { return query; }
    public List<ExportFormat> getFormats()     { return formats; }
    public List<ReportParameter> getParameters() { return parameters; }
    public String getPermission()              { return permission; }
    public String[] getColumnHeaders()         { return columnHeaders; }
    public int[] getColumnWidthsPct()          { return columnWidthsPct; }

    // ─── Builder ───────────────────────────────────

    public static Builder builder(String id, String moduleId) {
        return new Builder(id, moduleId);
    }

    public static class Builder {
        private final String id;
        private final String moduleId;
        private String titleKey      = "";
        private String templatePath  = "";
        private SourceType sourceType = SourceType.JPQL;
        private String query         = "";
        private List<ExportFormat> formats     = List.of(ExportFormat.PDF, ExportFormat.EXCEL);
        private List<ReportParameter> parameters = List.of();
        private String permission    = "";
        private String[] columnHeaders   = new String[0];
        private int[]    columnWidthsPct = new int[0];

        private Builder(String id, String moduleId) {
            this.id       = id;
            this.moduleId = moduleId;
        }

        public Builder titleKey(String key)              { this.titleKey = key; return this; }
        public Builder template(String path)             { this.templatePath = path; return this; }
        public Builder sourceType(SourceType type)       { this.sourceType = type; return this; }
        public Builder query(String query)               { this.query = query; return this; }
        public Builder formats(List<ExportFormat> f)     { this.formats = f; return this; }
        public Builder parameters(List<ReportParameter> p) { this.parameters = p; return this; }
        public Builder permission(String perm)           { this.permission = perm; return this; }
        /** عناوين أعمدة الجدول + نسب عرضها (تجمع 100) — تُستخدم في تصدير PDF/Excel */
        public Builder columns(String[] headers, int[] widthsPct) {
            this.columnHeaders = headers; this.columnWidthsPct = widthsPct; return this;
        }
        public ReportDefinition build()                  { return new ReportDefinition(this); }
    }
}
