package com.joud.hr;

import com.joud.core.menu.MenuItem;
import com.joud.core.module.JoudModule;
import com.joud.core.report.ReportDefinition;
import com.joud.core.report.ReportParameter;
import com.joud.core.report.ReportParameter.ParamType;
import com.joud.hr.entity.Employee;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * HrModule — تسجيل وحدة الموارد البشرية
 */
public class HrModule implements JoudModule {

    @Override
    public String getModuleId() {
        return "hr";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getModuleName(Locale l) {
        return "ar".equals(l.getLanguage()) ? "الموارد البشرية" : "Human Resources";
    }

    @Override
    public String getDescription(Locale l) {
        return "إدارة الموظفين، مسير الرواتب، الحضور والإجازات";
    }

    @Override
    public int getMenuOrder() {
        return 30;
    }

    @Override
    public List<MenuItem> getMenuItems(Locale locale) {
        boolean ar = "ar".equals(locale.getLanguage());
        return List.of(
                MenuItem.builder("hr.root", ar ? "الموارد البشرية" : "HR")
                        .icon("pi pi-users").order(30)
                        .child(MenuItem.builder("hr.employees", ar ? "الموظفون" : "Employees")
                                .icon("pi pi-id-card")
                                .outcome("/views/hr/employees")
                                .permission("hr.employee.view").order(1).build())
                        .child(MenuItem.builder("hr.payroll", ar ? "مسير الرواتب" : "Payroll")
                                .icon("pi pi-dollar")
                                .outcome("/views/hr/payroll")
                                .permission("hr.payroll.view").order(2).build())
                        .child(MenuItem.builder("hr.attendance", ar ? "الحضور والغياب" : "Attendance")
                                .icon("pi pi-calendar-plus")
                                .outcome("/views/hr/attendance")
                                .permission("hr.attendance.view").order(3).build())
                        .child(MenuItem.builder("hr.leaves", ar ? "الإجازات" : "Leaves")
                                .icon("pi pi-calendar-minus")
                                .outcome("/views/hr/leaves")
                                .permission("hr.leave.view").order(4).build())
                        .child(MenuItem.builder("hr.salary_structure", ar ? "هيكل الرواتب" : "Salary Structure")
                                .icon("pi pi-sitemap")
                                .outcome("/views/hr/salary-structure")
                                .permission("hr.salary.manage").order(5).build())
                        .build());
    }

    @Override
    public List<Class<?>> getEntityClasses() {
        return List.of(
                Employee.class,
                com.joud.hr.entity.Department.class,
                com.joud.hr.entity.Designation.class,
                com.joud.hr.entity.SalaryComponent.class,
                com.joud.hr.entity.SalaryStruct.class,
                com.joud.hr.entity.PayrollMast.class,
                com.joud.hr.entity.PayrollLine.class,
                com.joud.hr.entity.Attendance.class,
                com.joud.hr.entity.LeaveType.class,
                com.joud.hr.entity.LeaveRequest.class);
    }

    @Override
    public List<String> getPermissions() {
        return List.of(
                "hr.employee.view", "hr.employee.create",
                "hr.employee.edit", "hr.employee.deactivate",
                "hr.payroll.view", "hr.payroll.generate",
                "hr.payroll.approve", "hr.payroll.void",
                "hr.attendance.view", "hr.attendance.edit",
                "hr.leave.view", "hr.leave.approve",
                "hr.salary.manage");
    }

    @Override
    public List<String> getRequiredModules() {
        return List.of("accounting"); // مسير الرواتب يولّد قيوداً محاسبية
    }

    @Override
    public List<ReportDefinition> getReports() {
        List<ReportParameter> yearMonth = List.of(
                new ReportParameter("year", "السنة", ParamType.NUMBER, true, LocalDate.now().getYear()),
                new ReportParameter("month", "الشهر", ParamType.NUMBER, true, LocalDate.now().getMonthValue()));

        return List.of(
                // ─── قائمة الموظفين ──────────────────────
                ReportDefinition.builder("hr.employee_list", "hr")
                        .titleKey("قائمة الموظفين")
                        .sourceType(ReportDefinition.SourceType.SQL)
                        .query("""
                                SELECT e.EMP_C_NAME, d.DEPT_C_NAME, ds.DESIG_C_NAME,
                                       e.EMP_C_EMP_TYPE, e.EMP_D_DOJ, e.EMP_C_STATUS
                                FROM EMPLOYEE_MAST e
                                LEFT JOIN DEPARTMENT_MAST  d  ON e.DEPT_I_ID  = d.DEPT_I_ID
                                LEFT JOIN DESIGNATION_MAST ds ON e.DESIG_I_ID = ds.DESIG_I_ID
                                WHERE e.EMP_C_STATUS = 'A'
                                ORDER BY e.EMP_C_NAME
                                """)
                        .permission("hr.employee.view")
                        .formats(List.of(ReportDefinition.ExportFormat.PDF, ReportDefinition.ExportFormat.EXCEL))
                        .columns(
                                new String[] { "اسم الموظف", "القسم", "المسمى الوظيفي", "نوع التوظيف", "تاريخ الانضمام",
                                        "الحالة" },
                                new int[] { 25, 18, 18, 14, 14, 11 })
                        .build(),

                // ─── مسير الرواتب — تفصيل بالموظف ──────
                ReportDefinition.builder("hr.payroll_sheet", "hr")
                        .titleKey("مسير الرواتب التفصيلي")
                        .sourceType(ReportDefinition.SourceType.SQL)
                        .query("""
                                SELECT e.EMP_C_NAME,
                                       SUM(CASE WHEN l.LINE_C_TYPE='E' THEN l.LINE_F_AMOUNT ELSE 0 END) AS EARN,
                                       SUM(CASE WHEN l.LINE_C_TYPE='D' THEN l.LINE_F_AMOUNT ELSE 0 END) AS DED,
                                       SUM(CASE WHEN l.LINE_C_TYPE='E' THEN  l.LINE_F_AMOUNT
                                                WHEN l.LINE_C_TYPE='D' THEN -l.LINE_F_AMOUNT ELSE 0 END) AS NET
                                FROM PAYROLL_LINE l
                                JOIN PAYROLL_MAST p   ON l.PAY_I_ID = p.PAY_I_ID
                                JOIN EMPLOYEE_MAST e  ON l.EMP_i_id  = e.EMP_i_id
                                WHERE p.PAY_I_YEAR = :year AND p.PAY_I_MONTH = :month
                                  AND p.PAY_C_STATUS <> 'V'
                                GROUP BY e.EMP_C_NAME
                                ORDER BY e.EMP_C_NAME
                                """)
                        .parameters(yearMonth)
                        .permission("hr.payroll.view")
                        .formats(List.of(ReportDefinition.ExportFormat.PDF, ReportDefinition.ExportFormat.EXCEL))
                        .columns(
                                new String[] { "اسم الموظف", "إجمالي الاستحقاقات", "إجمالي الخصومات", "صافي الراتب" },
                                new int[] { 34, 22, 22, 22 })
                        .build(),

                // ─── تقرير الحضور الشهري ────────────────
                ReportDefinition.builder("hr.attendance_report", "hr")
                        .titleKey("تقرير الحضور الشهري")
                        .sourceType(ReportDefinition.SourceType.SQL)
                        .query("""
                                SELECT e.EMP_C_NAME, a.PRESENT_DAYS, a.ABSENT_DAYS,
                                       a.LEAVE_DAYS, a.TOTAL_HOURS, a.TOTAL_OT_HOURS
                                FROM V_ATTENDANCE_MONTHLY a
                                JOIN EMPLOYEE_MAST e ON a.EMP_i_id = e.EMP_i_id
                                WHERE a.ATT_YEAR = :year AND a.ATT_MONTH = :month
                                ORDER BY e.EMP_C_NAME
                                """)
                        .parameters(yearMonth)
                        .permission("hr.attendance.view")
                        .formats(List.of(ReportDefinition.ExportFormat.PDF, ReportDefinition.ExportFormat.EXCEL))
                        .columns(
                                new String[] { "اسم الموظف", "أيام الحضور", "أيام الغياب", "أيام الإجازة",
                                        "ساعات العمل", "ساعات إضافية" },
                                new int[] { 28, 14, 14, 14, 15, 15 })
                        .build());
    }
}
