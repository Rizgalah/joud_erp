package com.joud.core.report.engine;

import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;

import java.io.IOException;
import java.io.OutputStream;

/**
 * ReportDownloadUtil — يبث ملف التصدير (PDF/Excel) إلى المتصفح كتنزيل
 *
 * مشتركة بين جميع الـ Beans في كل الوحدات — تستخدمها أي شاشة بعد
 * استدعاء ReportEngineService.exportPdf()/exportExcel() لإرسال الـ
 * byte[] الناتج للمستخدم مباشرة بدون إعادة تحميل الصفحة (يتطلب
 * ajax="false" على زر الأمر الذي يستدعيها).
 */
public final class ReportDownloadUtil {

    public static final String PDF_MIME  = "application/pdf";
    public static final String XLSX_MIME =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private ReportDownloadUtil() {}

    /** يبث المحتوى كملف يُحفظ مباشرة (Content-Disposition: attachment) */
    public static void stream(byte[] content, String filename, String mimeType) {
        FacesContext fc   = FacesContext.getCurrentInstance();
        ExternalContext ec = fc.getExternalContext();

        ec.responseReset();
        ec.setResponseContentType(mimeType);
        ec.setResponseContentLength(content.length);
        ec.setResponseHeader("Content-Disposition",
            "attachment; filename=\"" + filename + "\"");

        try (OutputStream out = ec.getResponseOutputStream()) {
            out.write(content);
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException("فشل بث الملف للمتصفح: " + e.getMessage(), e);
        }

        fc.responseComplete();
    }

    /** اسم ملف موحَّد: العنوان + التاريخ/الوقت الحالي + الامتداد */
    public static String buildFilename(String baseName, String extension) {
        String ts = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
        String safe = baseName.replaceAll("[^\\p{L}\\p{N}_\\-]", "_");
        return safe + "_" + ts + "." + extension;
    }
}
