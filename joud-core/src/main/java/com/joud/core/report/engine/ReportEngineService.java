package com.joud.core.report.engine;

import com.joud.core.report.ReportDefinition;
import com.joud.core.report.ReportParameter;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * ReportEngineService — ماكنة التقارير الموحَّدة
 *
 * نقطة دخول واحدة لكل الوحدات: تأخذ ReportDefinition + معاملات
 * (مدخلة من المستخدم في شاشة التقارير) وتُصدِّر PDF أو Excel.
 *
 * يدعم نوعي مصدر بيانات:
 *   SQL  → native query عبر EntityManager.createNativeQuery()
 *   JPQL → createQuery() — يتطلب أن يعيد الاستعلام Object[] أو كيانات بسيطة
 *
 * أعمدة وعناوين الجدول تُمرَّر عبر ReportColumns لأن JPA/JDBC لا يوفر
 * دائماً أسماء أعمدة عربية جاهزة من نتيجة الاستعلام مباشرة.
 */
@ApplicationScoped
public class ReportEngineService {

    private static final Logger LOG = Logger.getLogger(ReportEngineService.class.getName());

    @PersistenceContext
    private EntityManager em;

    /**
     * تنفيذ الاستعلام المرتبط بالتقرير وإرجاع الصفوف كـ List&lt;Object[]&gt;
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> executeQuery(ReportDefinition def, Map<String, Object> params) {
        if (def.getQuery() == null || def.getQuery().isBlank()) {
            throw new ReportException("لا يوجد استعلام مرتبط بهذا التقرير: " + def.getId());
        }

        Query query = (def.getSourceType() == ReportDefinition.SourceType.SQL)
            ? em.createNativeQuery(def.getQuery())
            : em.createQuery(def.getQuery());

        // ربط المعاملات المعروفة في تعريف التقرير فقط (حماية من حقن معاملات غير متوقعة)
        for (ReportParameter p : def.getParameters()) {
            Object value = params.getOrDefault(p.getKey(), p.getDefaultValue());
            if (value != null) {
                query.setParameter(p.getKey(), value);
            } else if (p.isRequired()) {
                throw new ReportException("المعامل المطلوب غير موجود: " + p.getKey());
            }
        }

        List<Object> rawResults = query.getResultList();
        List<Object[]> rows = new ArrayList<>(rawResults.size());
        for (Object r : rawResults) {
            rows.add(r instanceof Object[] ? (Object[]) r : new Object[]{ r });
        }
        return rows;
    }

    /**
     * يُصدِّر التقرير إلى PDF
     */
    public byte[] exportPdf(ReportDefinition def, ReportColumns columns,
                            Map<String, Object> params, Object[] totalsRow) {
        try {
            List<Object[]> rows = executeQuery(def, params);
            JasperReportBuilder builder = new JasperReportBuilder(
                columns.title(), columns.subtitle(),
                columns.headers(), columns.widthsPct(), true);
            return builder.exportToPdf(rows, totalsRow);
        } catch (ReportException re) {
            throw re;
        } catch (Exception e) {
            LOG.severe("PDF export failed for " + def.getId() + ": " + e.getMessage());
            throw new ReportException("فشل تصدير PDF: " + e.getMessage());
        }
    }

    /**
     * يُصدِّر التقرير إلى Excel
     */
    public byte[] exportExcel(ReportDefinition def, ReportColumns columns,
                              Map<String, Object> params, Object[] totalsRow) {
        try {
            List<Object[]> rows = executeQuery(def, params);
            ExcelReportBuilder builder = new ExcelReportBuilder(
                columns.title(), columns.subtitle(), columns.headers(), true);
            return builder.exportToExcel(rows, totalsRow);
        } catch (ReportException re) {
            throw re;
        } catch (Exception e) {
            LOG.severe("Excel export failed for " + def.getId() + ": " + e.getMessage());
            throw new ReportException("فشل تصدير Excel: " + e.getMessage());
        }
    }

    /** بيانات عرض التقرير: العنوان والعناوين الفرعية وأعمدة الجدول ونسب عرضها */
    public record ReportColumns(String title, String subtitle,
                                String[] headers, int[] widthsPct) {}

    // ════════════════════════════════════════════════
    // تصدير مباشر بدون ReportDefinition (Ad-hoc)
    // ════════════════════════════════════════════════
    // يُستخدم داخل شاشات الوحدات لتصدير "نفس ما يراه المستخدم على الشاشة"
    // بالضبط (بيانات محمَّلة مسبقاً في الـ Bean) بدلاً من تعريف تقرير
    // كامل في PluginRegistry لكل شاشة. مثال: زر "PDF" في شاشة ميزان
    // المراجعة يُصدِّر صفوف trialBalanceData الموجودة فعلاً في الذاكرة.

    /** تصدير PDF من صفوف جاهزة (بدون استعلام داخلي) */
    public byte[] exportPdf(String title, String subtitle, String[] headers,
                            int[] widthsPct, List<Object[]> rows, Object[] totalsRow) {
        try {
            JasperReportBuilder builder = new JasperReportBuilder(title, subtitle, headers, widthsPct, true);
            return builder.exportToPdf(rows, totalsRow);
        } catch (Exception e) {
            LOG.severe("Ad-hoc PDF export failed: " + e.getMessage());
            throw new ReportException("فشل تصدير PDF: " + e.getMessage());
        }
    }

    /** تصدير Excel من صفوف جاهزة (بدون استعلام داخلي) */
    public byte[] exportExcel(String title, String subtitle, String[] headers,
                              List<Object[]> rows, Object[] totalsRow) {
        try {
            ExcelReportBuilder builder = new ExcelReportBuilder(title, subtitle, headers, true);
            return builder.exportToExcel(rows, totalsRow);
        } catch (Exception e) {
            LOG.severe("Ad-hoc Excel export failed: " + e.getMessage());
            throw new ReportException("فشل تصدير Excel: " + e.getMessage());
        }
    }

    public static class ReportException extends RuntimeException {
        public ReportException(String msg) { super(msg); }
    }
}
