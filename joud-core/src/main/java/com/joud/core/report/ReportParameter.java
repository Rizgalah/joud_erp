package com.joud.core.report;

/**
 * ReportParameter — معامل فلترة في التقرير
 * مثال: تاريخ البداية، تاريخ النهاية، الفرع، العملة
 */
public class ReportParameter {

    public enum ParamType { DATE, STRING, NUMBER, BOOLEAN, LIST }

    private final String key;        // اسم المعامل (e.g. "dateFrom")
    private final String labelKey;   // مفتاح الترجمة للتسمية
    private final ParamType type;    // نوع المعامل
    private final boolean required;  // هل هو إلزامي؟
    private final Object defaultValue;

    public ReportParameter(String key, String labelKey, ParamType type,
                           boolean required, Object defaultValue) {
        this.key          = key;
        this.labelKey     = labelKey;
        this.type         = type;
        this.required     = required;
        this.defaultValue = defaultValue;
    }

    public String getKey()          { return key; }
    public String getLabelKey()     { return labelKey; }
    public ParamType getType()      { return type; }
    public boolean isRequired()     { return required; }
    public Object getDefaultValue() { return defaultValue; }
}
