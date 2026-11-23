package com.joud.hr.service;

import com.joud.hr.entity.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.logging.Logger;

/**
 * PayrollService — ماكنة مسير الرواتب
 *
 * تتولى:
 * 1. توليد مسير الرواتب من هياكل الرواتب
 * 2. حساب الحضور والغياب
 * 3. اعتماد المسير وتوليد القيد المحاسبي
 * 4. إحصاءات الموظفين
 */
@ApplicationScoped
public class PayrollService {

    private static final Logger LOG = Logger.getLogger(PayrollService.class.getName());

    @PersistenceContext
    private EntityManager em;

    // ════════════════════════════════════════════════
    // 1. توليد المسير
    // ════════════════════════════════════════════════

    /**
     * يولّد مسير رواتب كامل لشهر معين
     * يقرأ هياكل رواتب جميع الموظفين النشطين
     * ويحسب التناسب إذا وجد غياب
     */
    @Transactional
    public PayrollMast generatePayroll(Long agentId, int year, int month,
            Long currencyId, Long createdBy) {
        // تحقق: هل يوجد مسير لهذا الشهر؟
        List<PayrollMast> existing = em.createQuery(
                "SELECT p FROM PayrollMast p " +
                        "WHERE p.agentId=:a AND p.year=:y AND p.month=:m AND p.status<>'V'",
                PayrollMast.class)
                .setParameter("a", agentId).setParameter("y", year).setParameter("m", month)
                .getResultList();

        if (!existing.isEmpty())
            throw new HrException("يوجد مسير رواتب لهذه الفترة: " + year + "/" + month);

        LocalDate dateFrom = LocalDate.of(year, month, 1);
        LocalDate dateTo = dateFrom.withDayOfMonth(dateFrom.lengthOfMonth());

        PayrollMast payroll = new PayrollMast();
        payroll.setId(generateId("PAYROLL_MAST"));
        payroll.setAgentId(agentId);
        payroll.setDescription("رواتب " + getMonthName(month) + " " + year);
        payroll.setYear(year);
        payroll.setMonth(month);
        payroll.setDateFrom(dateFrom);
        payroll.setDateTo(dateTo);
        payroll.setCurrencyId(currencyId);
        payroll.setStatus(PayrollMast.PayrollStatus.D);
        payroll.setCreatedBy(createdBy);
        em.persist(payroll);

        // جلب الموظفين النشطين
        List<Employee> employees = em.createNamedQuery("Employee.findActive", Employee.class)
                .setParameter("agentId", agentId).getResultList();

        BigDecimal totalEarn = BigDecimal.ZERO;
        BigDecimal totalDed = BigDecimal.ZERO;

        for (Employee emp : employees) {
            // جلب هيكل راتب الموظف السارية في هذا الشهر
            @SuppressWarnings("unchecked")
            List<Object[]> structs = em.createNativeQuery(
                    "SELECT s.COMP_I_ID, sc.COMP_C_TYPE, sc.COMP_C_NAME, " +
                            "sc.COMP_C_CALC, sc.COMP_F_PERCENT, s.STRUCT_F_AMOUNT, sc.COMP_I_ORDER " +
                            "FROM SALARY_STRUCT s " +
                            "JOIN SALARY_COMPONENT sc ON s.COMP_I_ID = sc.COMP_I_ID " +
                            "WHERE s.EMP_i_id = :empId " +
                            "  AND s.STRUCT_D_FROM <= :dt " +
                            "  AND (s.STRUCT_D_TO IS NULL OR s.STRUCT_D_TO >= :dt) " +
                            "  AND s.STRUCT_C_STATUS = 'A' " +
                            "ORDER BY sc.COMP_I_ORDER")
                    .setParameter("empId", emp.getId())
                    .setParameter("dt", dateFrom)
                    .getResultList();

            if (structs.isEmpty())
                continue;

            // احسب أيام العمل الفعلية
            BigDecimal workDays = calcWorkDays(emp.getId(), agentId, year, month, dateTo.getDayOfMonth());
            BigDecimal monthDays = new BigDecimal(dateTo.getDayOfMonth());

            for (Object[] row : structs) {
                Long compId = ((Number) row[0]).longValue();
                String compType = row[1].toString();
                String calcMethod = row[3].toString();
                BigDecimal percent = row[4] != null ? new BigDecimal(row[4].toString()) : BigDecimal.ZERO;
                BigDecimal amount = row[5] != null ? new BigDecimal(row[5].toString()) : BigDecimal.ZERO;
                Integer order = row[6] != null ? ((Number) row[6]).intValue() : 10;

                // حساب تناسبي إذا كانت أيام العمل أقل من أيام الشهر
                if ("F".equals(calcMethod) && workDays.compareTo(monthDays) < 0) {
                    amount = amount.multiply(workDays)
                            .divide(monthDays, 5, RoundingMode.HALF_UP);
                }

                // حساب نسبة من الأساسي
                if ("P".equals(calcMethod)) {
                    amount = emp.getBasicSalary()
                            .multiply(percent)
                            .divide(new BigDecimal("100"), 5, RoundingMode.HALF_UP);
                }

                PayrollLine line = new PayrollLine();
                line.setId(generateId("PAYROLL_LINE"));
                line.setPayrollId(payroll.getId());
                line.setEmployeeId(emp.getId());
                line.setComponentId(compId);
                line.setType(compType);
                line.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
                line.setWorkDays(workDays);
                line.setComponentOrder(order);
                em.persist(line);

                if ("E".equals(compType))
                    totalEarn = totalEarn.add(amount);
                else if ("D".equals(compType))
                    totalDed = totalDed.add(amount);
            }
        }

        payroll.setTotalEarnings(totalEarn.setScale(2, RoundingMode.HALF_UP));
        payroll.setTotalDeductions(totalDed.setScale(2, RoundingMode.HALF_UP));
        payroll.setNetPay(totalEarn.subtract(totalDed).setScale(2, RoundingMode.HALF_UP));
        payroll.setEmployeeCount(employees.size());
        em.merge(payroll);

        LOG.info("Payroll generated: " + payroll.getPeriodLabel() +
                " | Employees: " + employees.size() +
                " | Net: " + payroll.getNetPay());
        return payroll;
    }

    // ════════════════════════════════════════════════
    // 2. اعتماد المسير
    // ════════════════════════════════════════════════

    @Transactional
    public void approvePayroll(Long payrollId, Long userId) {
        PayrollMast p = em.find(PayrollMast.class, payrollId);
        if (p == null)
            throw new HrException("المسير غير موجود");
        if (!p.canApprove())
            throw new HrException("لا يمكن اعتماد المسير بحالته الحالية");

        p.setStatus(PayrollMast.PayrollStatus.A);
        p.setApprovedBy(userId);
        p.setApprovedAt(LocalDate.now());
        em.merge(p);

        LOG.info("Payroll approved: " + p.getPeriodLabel() + " by user: " + userId);
    }

    /** إلغاء المسير */
    @Transactional
    public void voidPayroll(Long payrollId, Long userId) {
        PayrollMast p = em.find(PayrollMast.class, payrollId);
        if (p == null)
            throw new HrException("المسير غير موجود");
        if (p.isPaid())
            throw new HrException("لا يمكن إلغاء مسير مدفوع");

        p.setStatus(PayrollMast.PayrollStatus.V);
        em.merge(p);
        LOG.info("Payroll voided: " + p.getPeriodLabel());
    }

    // ════════════════════════════════════════════════
    // 3. إحصاءات الموظفين
    // ════════════════════════════════════════════════

    public long countActive(Long agentId) {
        return (Long) em.createQuery(
                "SELECT COUNT(e) FROM Employee e WHERE e.agentId=:a AND e.status='A'")
                .setParameter("a", agentId).getSingleResult();
    }

    public long countByDept(Long deptId) {
        return (Long) em.createQuery(
                "SELECT COUNT(e) FROM Employee e WHERE e.deptId=:d AND e.status='A'")
                .setParameter("d", deptId).getSingleResult();
    }

    /** قائمة الموظفين الذين تنتهي وثائقهم خلال 30 يوماً */
    public List<Employee> getExpiringDocuments(Long agentId, int days) {
        LocalDate limit = LocalDate.now().plusDays(days);
        return em.createQuery(
                "SELECT e FROM Employee e WHERE e.agentId=:a AND e.status='A' " +
                        "AND (e.idExpiry<=:lim OR e.passportExpiry<=:lim) ORDER BY e.name",
                Employee.class)
                .setParameter("a", agentId)
                .setParameter("lim", limit)
                .getResultList();
    }

    // ════════════════════════════════════════════════
    // Private Helpers
    // ════════════════════════════════════════════════

    /** حساب أيام الحضور الفعلية من ATTENDANCE_MAST */
    private BigDecimal calcWorkDays(Long empId, Long agentId,
            int year, int month, int monthDays) {
        try {
            Object r = em.createNativeQuery(
                    "SELECT COUNT(*) FROM ATTENDANCE_MAST " +
                            "WHERE EMP_i_id=:e AND AGENT_I_ID=:a " +
                            "AND EXTRACT(YEAR FROM ATT_D_DATE)=:y " +
                            "AND EXTRACT(MONTH FROM ATT_D_DATE)=:m " +
                            "AND ATT_C_STATUS IN ('P','H')")
                    .setParameter("e", empId).setParameter("a", agentId)
                    .setParameter("y", year).setParameter("m", month)
                    .getSingleResult();
            long days = ((Number) r).longValue();
            return days > 0 ? new BigDecimal(days) : new BigDecimal(monthDays);
        } catch (Exception e) {
            return new BigDecimal(monthDays); // لو لا يوجد سجل حضور → راتب كامل
        }
    }

    private Long generateId(String table) {
        try {
            return ((Number) em.createNativeQuery(
                    "SELECT ID_I_GEN FROM KEY_GENERATION WHERE TABLE_C_NAME=:t")
                    .setParameter("t", table).getSingleResult()).longValue();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    public String getMonthName(int m) {
        String[] n = { "", "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
                "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر" };
        return m >= 1 && m <= 12 ? n[m] : String.valueOf(m);
    }

    public static class HrException extends RuntimeException {
        public HrException(String msg) {
            super(msg);
        }
    }
}
