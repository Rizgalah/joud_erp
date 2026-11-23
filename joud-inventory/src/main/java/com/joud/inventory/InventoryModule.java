package com.joud.inventory;

import com.joud.core.menu.MenuItem;
import com.joud.core.module.JoudModule;
import com.joud.core.report.ReportDefinition;
import com.joud.core.report.ReportParameter;
import com.joud.core.report.ReportParameter.ParamType;
import com.joud.inventory.entity.*;

import com.joud.core.db.JoudRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * InventoryModule — تسجيل وحدة المخزون
 * يُكتشف تلقائياً عبر Java SPI عند التشغيل
 */
public class InventoryModule implements JoudModule {

    @Override
    public String getModuleId() {
        return "inventory";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getModuleName(Locale l) {
        return "ar".equals(l.getLanguage()) ? "المخزون" : "Inventory";
    }

    @Override
    public String getDescription(Locale l) {
        return "إدارة الأصناف، المستودعات، الجرد، حركات المخزون، تنبيهات إعادة الطلب";
    }

    @Override
    public int getMenuOrder() {
        return 50;
    }

    @Override
    public List<MenuItem> getMenuItems(Locale locale) {
        boolean ar = "ar".equals(locale.getLanguage());
        return List.of(
                MenuItem.builder("inventory.root", ar ? "المخزون" : "Inventory")
                        .icon("pi pi-warehouse").order(50)
                        .child(MenuItem.builder("inv.products", ar ? "الأصناف" : "Products")
                                .icon("pi pi-box")
                                .outcome("/views/inventory/products")
                                .permission("inventory.product.view").order(1).build())
                        .child(MenuItem.builder("inv.warehouses", ar ? "المستودعات" : "Warehouses")
                                .icon("pi pi-building")
                                .outcome("/views/inventory/warehouses")
                                .permission("inventory.warehouse.view").order(2).build())
                        .child(MenuItem.builder("inv.movements", ar ? "حركات المخزون" : "Movements")
                                .icon("pi pi-arrows-v")
                                .outcome("/views/inventory/stock-movements")
                                .permission("inventory.movement.view").order(3).build())
                        .child(MenuItem.builder("inv.count", ar ? "الجرد" : "Stock Count")
                                .icon("pi pi-clipboard")
                                .outcome("/views/inventory/stock-count")
                                .permission("inventory.count.view").order(4).build())
                        .build());
    }

    @Override
    public List<Class<?>> getEntityClasses() {
        return List.of(
                Product.class,
                Warehouse.class,
                StockCountMast.class,
                StockCountLine.class);
    }

    @Override
    public List<String> getPermissions() {
        return List.of(
                "inventory.product.view", "inventory.product.create",
                "inventory.product.edit", "inventory.product.deactivate",
                "inventory.warehouse.view", "inventory.warehouse.manage",
                "inventory.movement.view", "inventory.movement.manual",
                "inventory.count.view", "inventory.count.create",
                "inventory.count.post", "inventory.reports.view");
    }

    @Override
    public List<String> getRequiredModules() {
        return List.of("trade"); // حركات المخزون مرتبطة بالفواتير
    }

    @Override
    public void onInstall(JoudRepository db) {
        // إنشاء مستودع افتراضي عند أول تثبيت
        try {
            db.executeUpdate(
                    "INSERT INTO warehouses " +
                            "(ware_i_id,ware_c_code,ware_c_name,ware_c_type,is_active) " +
                            "SELECT 1,'WH-001','المستودع الرئيسي','main',1 " +
                            "FROM DUAL WHERE NOT EXISTS " +
                            "(SELECT 1 FROM warehouses WHERE ware_c_code='WH-001')");
        } catch (Exception ignored) {
        }
    }

    @Override
    public List<ReportDefinition> getReports() {
        return List.of(
                // ─── مستوى المخزون الحالي ───────────────
                ReportDefinition.builder("inv.stock_level", "inventory")
                        .titleKey("مستوى المخزون الحالي")
                        .sourceType(ReportDefinition.SourceType.SQL)
                        .query("""
                                SELECT prod_c_code, prod_c_name, categ_c_name,
                                       total_qty, min_qty, stock_value, below_min
                                FROM V_STOCK_LEVEL
                                ORDER BY prod_c_name
                                """)
                        .permission("inventory.reports.view")
                        .formats(List.of(
                                ReportDefinition.ExportFormat.PDF,
                                ReportDefinition.ExportFormat.EXCEL))
                        .columns(
                                new String[] { "الكود", "اسم الصنف", "الفئة", "الكمية الحالية", "الحد الأدنى",
                                        "قيمة المخزون", "تحت الحد؟" },
                                new int[] { 12, 26, 16, 14, 12, 14, 6 })
                        .build(),

                // ─── حركات المخزون خلال فترة ─────────────
                ReportDefinition.builder("inv.movements_report", "inventory")
                        .titleKey("تقرير حركات المخزون")
                        .sourceType(ReportDefinition.SourceType.SQL)
                        .query("""
                                SELECT MOVE_D_DATE, MOVE_C_TYPE, prod_c_code, prod_c_name,
                                       ware_c_name, MOVE_I_QTY, MOVE_F_VALUE
                                FROM V_STOCK_MOVEMENTS_DETAIL
                                WHERE MOVE_D_DATE BETWEEN :dateFrom AND :dateTo
                                ORDER BY MOVE_D_DATE DESC
                                """)
                        .parameters(List.of(
                                new ReportParameter("dateFrom", "من تاريخ", ParamType.DATE, true,
                                        LocalDate.now().withDayOfMonth(1)),
                                new ReportParameter("dateTo", "إلى تاريخ", ParamType.DATE, true,
                                        LocalDate.now())))
                        .permission("inventory.reports.view")
                        .formats(List.of(
                                ReportDefinition.ExportFormat.PDF,
                                ReportDefinition.ExportFormat.EXCEL))
                        .columns(
                                new String[] { "التاريخ", "النوع", "الكود", "اسم الصنف", "المستودع", "الكمية",
                                        "القيمة" },
                                new int[] { 12, 10, 12, 26, 16, 12, 12 })
                        .build(),

                // ─── قيمة المخزون حسب الفئة ──────────────
                ReportDefinition.builder("inv.stock_value", "inventory")
                        .titleKey("قيمة المخزون حسب الفئة")
                        .sourceType(ReportDefinition.SourceType.SQL)
                        .query("""
                                SELECT categ_c_name, PROD_COUNT, TOTAL_QTY, TOTAL_VALUE, TOTAL_SALE_VALUE
                                FROM V_STOCK_VALUE
                                ORDER BY TOTAL_VALUE DESC
                                """)
                        .permission("inventory.reports.view")
                        .formats(List.of(
                                ReportDefinition.ExportFormat.PDF,
                                ReportDefinition.ExportFormat.EXCEL))
                        .columns(
                                new String[] { "الفئة", "عدد الأصناف", "الكمية الإجمالية", "قيمة التكلفة",
                                        "قيمة البيع" },
                                new int[] { 28, 16, 18, 19, 19 })
                        .build(),

                // ─── تنبيهات إعادة الطلب المفتوحة ────────
                ReportDefinition.builder("inv.reorder_alerts", "inventory")
                        .titleKey("تنبيهات إعادة الطلب")
                        .sourceType(ReportDefinition.SourceType.SQL)
                        .query("""
                                SELECT p.prod_c_code, p.prod_c_name, w.ware_c_name,
                                       r.ALERT_F_QTY, r.ALERT_F_MIN, r.CREATED_AT
                                FROM REORDER_ALERT r
                                JOIN products p ON r.PROD_I_ID = p.prod_i_id
                                LEFT JOIN warehouses w ON r.WARE_I_ID = w.ware_i_id
                                WHERE r.ALERT_C_STS = 'N'
                                ORDER BY r.CREATED_AT DESC
                                """)
                        .permission("inventory.reports.view")
                        .formats(List.of(ReportDefinition.ExportFormat.PDF))
                        .columns(
                                new String[] { "الكود", "اسم الصنف", "المستودع", "الكمية الحالية", "الحد الأدنى",
                                        "تاريخ التنبيه" },
                                new int[] { 14, 28, 18, 14, 12, 14 })
                        .build());
    }
}
