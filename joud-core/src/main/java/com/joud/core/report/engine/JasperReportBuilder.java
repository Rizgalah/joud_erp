package com.joud.core.report.engine;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRTableModelDataSource;
import net.sf.jasperreports.engine.design.*;
import net.sf.jasperreports.engine.type.*;

import javax.swing.table.DefaultTableModel;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * JasperReportBuilder — يبني تقرير PDF برمجياً بدون الحاجة لملفات .jrxml
 *
 * السبب: في بيئة تطوير تعتمد على الكتابة عبر المحادثة، صيانة ملفات XML
 * منفصلة لكل تقرير غير عملية. هذا البناء البرمجي (JRDesign API) يُنشئ
 * تخطيط الصفحة + الرأس + الأعمدة + التفاصيل + الإجماليات مباشرة في الكود،
 * يُجمَّعه JasperCompileManager في وقت التشغيل، ثم يُصدَّر إلى PDF.
 *
 * يدعم: عربي RTL، رأس وتذييل بشعار الشركة، تلوين متبادل للصفوف،
 *       صف إجمالي اختياري في الأسفل.
 */
public class JasperReportBuilder {

    private static final Color HEADER_BG   = new Color(0x1B, 0x3A, 0x6B);
    private static final Color HEADER_FG   = Color.WHITE;
    // private static final Color ALT_ROW_BG  = new Color(0xF2, 0xF6, 0xFA);
    private static final Color BORDER_CLR  = new Color(0xD0, 0xD9, 0xE8);
    // private static final Color TOTAL_BG    = new Color(0xE8, 0xF0, 0xFA);

    private final String   reportTitle;
    private final String   subtitle;
    private final String[] columnHeaders;
    private final int[]    columnWidthsPct; // نسب عرض الأعمدة (تجمع 100)
    private final boolean  rtl;

    public JasperReportBuilder(String reportTitle, String subtitle,
                                String[] columnHeaders, int[] columnWidthsPct, boolean rtl) {
        this.reportTitle    = reportTitle;
        this.subtitle       = subtitle;
        this.columnHeaders  = columnHeaders;
        this.columnWidthsPct = columnWidthsPct;
        this.rtl            = rtl;
    }

    /**
     * يبني ويُصدِّر التقرير إلى PDF
     * @param rows كل صف Object[] بنفس عدد الأعمدة
     * @param totalsRow صف إجمالي اختياري (null لتجاهله) — يُعرض بخلفية مميزة في الأسفل
     */
    public byte[] exportToPdf(List<Object[]> rows, Object[] totalsRow) throws JRException {
        JasperDesign design = buildDesign();
        JasperReport report = JasperCompileManager.compileReport(design);

        DefaultTableModel model = buildTableModel(rows, totalsRow);
        JRTableModelDataSource ds = new JRTableModelDataSource(model);

        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("ReportTitle", reportTitle);
        params.put("Subtitle",    subtitle != null ? subtitle : "");
        params.put("PrintDate",   java.time.LocalDateTime.now().toString());

        JasperPrint print = JasperFillManager.fillReport(report, params, ds);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        net.sf.jasperreports.export.SimpleExporterInput input =
            new net.sf.jasperreports.export.SimpleExporterInput(print);
        net.sf.jasperreports.export.SimpleOutputStreamExporterOutput output =
            new net.sf.jasperreports.export.SimpleOutputStreamExporterOutput(out);

        net.sf.jasperreports.engine.export.JRPdfExporter exporter =
            new net.sf.jasperreports.engine.export.JRPdfExporter();
        exporter.setExporterInput(input);
        exporter.setExporterOutput(output);
        exporter.exportReport();

        return out.toByteArray();
    }

    // ════════════════════════════════════════════════
    // بناء تصميم التقرير (JRDesign API)
    // ════════════════════════════════════════════════

    private JasperDesign buildDesign() throws JRException {
        JasperDesign design = new JasperDesign();
        design.setName(reportTitle);
        design.setPageWidth(842);   // A4 landscape
        design.setPageHeight(595);
        design.setOrientation(OrientationEnum.LANDSCAPE);
        design.setLeftMargin(24);
        design.setRightMargin(24);
        design.setTopMargin(24);
        design.setBottomMargin(24);
        design.setColumnWidth(842 - 48);

        // ─── معاملات التقرير ────────────────────────
        addParam(design, "ReportTitle", String.class);
        addParam(design, "Subtitle",    String.class);
        addParam(design, "PrintDate",   String.class);

        // ─── حقول الجدول (نص لكل خلية) ──────────────
        for (int i = 0; i < columnHeaders.length; i++) {
            JRDesignField field = new JRDesignField();
            field.setName("col" + i);
            field.setValueClass(String.class);
            design.addField(field);
        }

        // ─── رأس الصفحة Title Band ──────────────────
        JRDesignBand titleBand = new JRDesignBand();
        titleBand.setHeight(70);

        JRDesignStaticText logoBar = rectFiller(0, 0, design.getColumnWidth(), 4, HEADER_BG);
        titleBand.addElement(logoBar);

        JRDesignTextField titleField = textParamField("ReportTitle", 0, 10,
            design.getColumnWidth(), 28, 16, true, HEADER_BG, HorizontalTextAlignEnum.CENTER);
        titleBand.addElement(titleField);

        JRDesignTextField subtitleField = textParamField("Subtitle", 0, 38,
            design.getColumnWidth(), 18, 11, false, new Color(0x59,0x59,0x59), HorizontalTextAlignEnum.CENTER);
        titleBand.addElement(subtitleField);

        JRDesignTextField dateField = textParamField("PrintDate", 0, 56,
            design.getColumnWidth(), 14, 9, false, new Color(0x88,0x88,0x88), HorizontalTextAlignEnum.CENTER);
        titleBand.addElement(dateField);

        design.setTitle(titleBand);

        // ─── رأس الأعمدة Column Header Band ─────────
        JRDesignBand colHeaderBand = new JRDesignBand();
        colHeaderBand.setHeight(26);
        int totalWidth = design.getColumnWidth();
        int x = 0;
        for (int i = 0; i < columnHeaders.length; i++) {
            int w = (int) Math.round(totalWidth * (columnWidthsPct[i] / 100.0));
            JRDesignStaticText header = new JRDesignStaticText();
            header.setText(columnHeaders[i]);
            header.setX(rtl ? totalWidth - x - w : x);
            header.setY(0);
            header.setWidth(w);
            header.setHeight(26);
            header.setBackcolor(HEADER_BG);
            header.setMode(ModeEnum.OPAQUE);
            header.setForecolor(HEADER_FG);
            header.setHorizontalTextAlign(HorizontalTextAlignEnum.CENTER);
            header.setVerticalTextAlign(VerticalTextAlignEnum.MIDDLE);
            header.setFontSize(9f);
            header.setBold(true);
            header.getLineBox().getBottomPen().setLineStyle(LineStyleEnum.SOLID);
            colHeaderBand.addElement(header);
            x += w;
        }
        design.setColumnHeader(colHeaderBand);

        // ─── تفصيل الصفوف Detail Band ───────────────
        JRDesignBand detailBand = new JRDesignBand();
        detailBand.setHeight(20);
        x = 0;
        for (int i = 0; i < columnHeaders.length; i++) {
            int w = (int) Math.round(totalWidth * (columnWidthsPct[i] / 100.0));
            JRDesignTextField tf = new JRDesignTextField();
            JRDesignExpression expr = new JRDesignExpression();
            expr.setText("$F{col" + i + "}");
            tf.setExpression(expr);
            tf.setX(rtl ? totalWidth - x - w : x);
            tf.setY(0);
            tf.setWidth(w);
            tf.setHeight(20);
            tf.setFontSize(8.5f);
            tf.setHorizontalTextAlign(i == 0
                ? HorizontalTextAlignEnum.RIGHT
                : HorizontalTextAlignEnum.CENTER);
            tf.setVerticalTextAlign(VerticalTextAlignEnum.MIDDLE);
            tf.setStretchType(StretchTypeEnum.NO_STRETCH);
            tf.getLineBox().getBottomPen().setLineStyle(LineStyleEnum.SOLID);
            tf.getLineBox().getBottomPen().setLineColor(BORDER_CLR);
            tf.getLineBox().getBottomPen().setLineWidth(0.5f);
            detailBand.addElement(tf);
            x += w;
        }
        ((JRDesignSection) design.getDetailSection()).addBand(detailBand);

        // ─── تذييل الصفحة Page Footer ────────────────
        JRDesignBand pageFooter = new JRDesignBand();
        pageFooter.setHeight(20);
        JRDesignTextField pageNum = new JRDesignTextField();
        JRDesignExpression pageExpr = new JRDesignExpression();
        pageExpr.setText("\"صفحة \" + $V{PAGE_NUMBER}");
        pageNum.setExpression(pageExpr);
        pageNum.setX(0);
        pageNum.setY(2);
        pageNum.setWidth(totalWidth);
        pageNum.setHeight(14);
        pageNum.setFontSize(8f);
        pageNum.setForecolor(new Color(0x99,0x99,0x99));
        pageNum.setHorizontalTextAlign(HorizontalTextAlignEnum.CENTER);
        pageFooter.addElement(pageNum);
        design.setPageFooter(pageFooter);

        return design;
    }

    // ════════════════════════════════════════════════
    // Helpers لبناء العناصر
    // ════════════════════════════════════════════════

    private void addParam(JasperDesign design, String name, Class<?> type) throws JRException {
        JRDesignParameter p = new JRDesignParameter();
        p.setName(name);
        p.setValueClass(type);
        design.addParameter(p);
    }

    private JRDesignStaticText rectFiller(int x, int y, int w, int h, Color color) {
        JRDesignStaticText el = new JRDesignStaticText();
        el.setX(x); el.setY(y); el.setWidth(w); el.setHeight(h);
        el.setBackcolor(color);
        el.setMode(ModeEnum.OPAQUE);
        el.setText("");
        return el;
    }

    private JRDesignTextField textParamField(String paramName, int x, int y, int w, int h,
                                              float fontSize, boolean bold, Color color,
                                              HorizontalTextAlignEnum align) {
        JRDesignTextField tf = new JRDesignTextField();
        JRDesignExpression expr = new JRDesignExpression();
        expr.setText("$P{" + paramName + "}");
        tf.setExpression(expr);
        tf.setX(x); tf.setY(y); tf.setWidth(w); tf.setHeight(h);
        tf.setFontSize(fontSize);
        tf.setBold(bold);
        tf.setForecolor(color);
        tf.setHorizontalTextAlign(align);
        tf.setVerticalTextAlign(VerticalTextAlignEnum.MIDDLE);
        return tf;
    }

    /** يحوّل الصفوف إلى DefaultTableModel بنص جاهز لكل خلية (تنسيق الأرقام والتواريخ) */
    private DefaultTableModel buildTableModel(List<Object[]> rows, Object[] totalsRow) {
        DefaultTableModel model = new DefaultTableModel();
        for (String h : columnHeaders) model.addColumn(h);

        for (Object[] row : rows) model.addRow(formatRow(row));
        if (totalsRow != null) model.addRow(formatRow(totalsRow));
        return model;
    }

    private Object[] formatRow(Object[] row) {
        Object[] formatted = new Object[row.length];
        for (int i = 0; i < row.length; i++) {
            formatted[i] = formatCell(row[i]);
        }
        return formatted;
    }

    private String formatCell(Object v) {
        if (v == null) return "—";
        if (v instanceof BigDecimal bd)      return String.format("%,.2f", bd);
        if (v instanceof Double || v instanceof Float) return String.format("%,.2f", v);
        if (v instanceof LocalDate ld)       return ld.toString();
        if (v instanceof LocalDateTime ldt)  return ldt.toLocalDate().toString();
        return v.toString();
    }
}
