package com.joud.core.report.engine;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddress;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ExcelReportBuilder — يبني ملف Excel (.xlsx) باستخدام Apache POI
 *
 * يدعم: عنوان مدمج بأعلى الصفحة، رأس أعمدة ملوّن، تنسيق أرقام/تواريخ صحيح
 * (وليس نصاً) كي تعمل الصيغ والفلاتر في Excel بشكل طبيعي، صف إجمالي
 * بخط عريض في الأسفل، واتجاه RTL لكامل الورقة.
 */
public class ExcelReportBuilder {

    private final String   reportTitle;
    private final String   subtitle;
    private final String[] columnHeaders;
    private final boolean  rtl;

    public ExcelReportBuilder(String reportTitle, String subtitle,
                               String[] columnHeaders, boolean rtl) {
        this.reportTitle   = reportTitle;
        this.subtitle      = subtitle;
        this.columnHeaders = columnHeaders;
        this.rtl           = rtl;
    }

    /**
     * @param rows كل صف Object[] — يدعم String, Number, BigDecimal, LocalDate/LocalDateTime
     * @param totalsRow صف إجمالي اختياري (null لتجاهله)
     */
    public byte[] exportToExcel(List<Object[]> rows, Object[] totalsRow) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("التقرير");
            sheet.setRightToLeft(rtl);

            CellStyle titleStyle      = titleStyle(wb);
            CellStyle subtitleStyle   = subtitleStyle(wb);
            CellStyle headerStyle     = headerStyle(wb);
            CellStyle textStyle       = textStyle(wb);
            CellStyle numberStyle     = numberStyle(wb);
            CellStyle dateStyle       = dateStyle(wb);
            CellStyle totalTextStyle  = totalTextStyle(wb);
            CellStyle totalNumStyle   = totalNumStyle(wb);

            int rowIdx = 0;

            // ─── العنوان ─────────────────────────────
            Row titleRow = sheet.createRow(rowIdx++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(reportTitle);
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, columnHeaders.length - 1));
            titleRow.setHeightInPoints(26);

            if (subtitle != null && !subtitle.isBlank()) {
                Row subRow = sheet.createRow(rowIdx++);
                Cell subCell = subRow.createCell(0);
                subCell.setCellValue(subtitle);
                subCell.setCellStyle(subtitleStyle);
                sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, columnHeaders.length - 1));
            }

            rowIdx++; // صف فارغ فاصل

            // ─── رأس الأعمدة ─────────────────────────
            Row headerRow = sheet.createRow(rowIdx++);
            headerRow.setHeightInPoints(20);
            for (int i = 0; i < columnHeaders.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(columnHeaders[i]);
                c.setCellStyle(headerStyle);
            }

            // ─── صفوف البيانات ───────────────────────
            for (Object[] dataRow : rows) {
                Row r = sheet.createRow(rowIdx++);
                for (int i = 0; i < dataRow.length; i++) {
                    writeCell(r.createCell(i), dataRow[i], textStyle, numberStyle, dateStyle);
                }
            }

            // ─── صف الإجمالي ──────────────────────────
            if (totalsRow != null) {
                Row r = sheet.createRow(rowIdx++);
                for (int i = 0; i < totalsRow.length; i++) {
                    Cell c = r.createCell(i);
                    Object v = totalsRow[i];
                    if (v == null) { c.setCellStyle(totalTextStyle); continue; }
                    if (v instanceof Number || v instanceof BigDecimal) {
                        c.setCellValue(toDouble(v));
                        c.setCellStyle(totalNumStyle);
                    } else {
                        c.setCellValue(v.toString());
                        c.setCellStyle(totalTextStyle);
                    }
                }
            }

            // ─── ضبط عرض الأعمدة تلقائياً ─────────────
            for (int i = 0; i < columnHeaders.length; i++) {
                sheet.autoSizeColumn(i);
                int width = sheet.getColumnWidth(i);
                sheet.setColumnWidth(i, Math.min(Math.max(width, 2800), 9000));
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ════════════════════════════════════════════════
    // كتابة الخلية بالنوع الصحيح
    // ════════════════════════════════════════════════

    private void writeCell(Cell c, Object v, CellStyle textStyle,
                            CellStyle numberStyle, CellStyle dateStyle) {
        if (v == null) { c.setCellValue("—"); c.setCellStyle(textStyle); return; }

        if (v instanceof BigDecimal bd) {
            c.setCellValue(bd.doubleValue());
            c.setCellStyle(numberStyle);
        } else if (v instanceof Number n) {
            c.setCellValue(n.doubleValue());
            c.setCellStyle(numberStyle);
        } else if (v instanceof LocalDate ld) {
            c.setCellValue(ld.toString());
            c.setCellStyle(dateStyle);
        } else if (v instanceof LocalDateTime ldt) {
            c.setCellValue(ldt.toLocalDate().toString());
            c.setCellStyle(dateStyle);
        } else {
            c.setCellValue(v.toString());
            c.setCellStyle(textStyle);
        }
    }

    private double toDouble(Object v) {
        if (v instanceof BigDecimal bd) return bd.doubleValue();
        if (v instanceof Number n)      return n.doubleValue();
        return 0;
    }

    // ════════════════════════════════════════════════
    // الأنماط (Styles)
    // ════════════════════════════════════════════════

    private CellStyle titleStyle(Workbook wb) {
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 14);
        f.setColor(IndexedColors.WHITE.getIndex());
        CellStyle s = wb.createCellStyle();
        s.setFont(f);
        s.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(
            new byte[]{0x1B,0x3A,0x6B}, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        return s;
    }

    private CellStyle subtitleStyle(Workbook wb) {
        Font f = wb.createFont();
        f.setFontHeightInPoints((short) 10);
        f.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        CellStyle s = wb.createCellStyle();
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        return s;
    }

    private CellStyle headerStyle(Workbook wb) {
        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        f.setFontHeightInPoints((short) 10);
        CellStyle s = wb.createCellStyle();
        s.setFont(f);
        s.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(
            new byte[]{0x1B,0x3A,0x6B}, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setBorderBottom(BorderStyle.THIN);
        return s;
    }

    private CellStyle textStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setBorderBottom(BorderStyle.THIN);
        s.setAlignment(HorizontalAlignment.RIGHT);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        Font f = wb.createFont();
        f.setFontHeightInPoints((short) 10);
        s.setFont(f);
        return s;
    }

    private CellStyle numberStyle(Workbook wb) {
        CellStyle s = textStyle(wb);
        DataFormat fmt = wb.createDataFormat();
        s.setDataFormat(fmt.getFormat("#,##0.00"));
        s.setAlignment(HorizontalAlignment.LEFT); // الأرقام تُعرض LTR منطقياً
        return s;
    }

    private CellStyle dateStyle(Workbook wb) {
        CellStyle s = textStyle(wb);
        s.setAlignment(HorizontalAlignment.CENTER);
        return s;
    }

    private CellStyle totalTextStyle(Workbook wb) {
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 10);
        CellStyle s = wb.createCellStyle();
        s.setFont(f);
        s.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(
            new byte[]{(byte)0xE8,(byte)0xF0,(byte)0xFA}, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderTop(BorderStyle.MEDIUM);
        s.setAlignment(HorizontalAlignment.RIGHT);
        return s;
    }

    private CellStyle totalNumStyle(Workbook wb) {
        CellStyle s = totalTextStyle(wb);
        DataFormat fmt = wb.createDataFormat();
        s.setDataFormat(fmt.getFormat("#,##0.00"));
        s.setAlignment(HorizontalAlignment.LEFT);
        return s;
    }
}
