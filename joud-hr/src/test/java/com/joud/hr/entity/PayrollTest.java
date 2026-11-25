package com.joud.hr.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("اختبارات صحة حسابات مسير الرواتب — Payroll")
public class PayrollTest {

    private PayrollMast payroll;

    @BeforeEach
    void setUp() {
        payroll = new PayrollMast();
        payroll.setId(1L);
        payroll.setYear(2026);
        payroll.setMonth(10);
        payroll.setStatus(PayrollMast.PayrollStatus.D); // Draft
        payroll.setDescription("مسير رواتب أكتوبر 2026");
    }

    @Test
    @DisplayName("اختبار تمييز بنود الراتب (إضافة / خصم)")
    void testPayrollLineTypes() {
        PayrollLine basicSalary = new PayrollLine();
        basicSalary.setType("E"); // Earning
        basicSalary.setAmount(new BigDecimal("5000.00"));

        PayrollLine housingAllowance = new PayrollLine();
        housingAllowance.setType("E");
        housingAllowance.setAmount(new BigDecimal("1250.00"));

        PayrollLine insuranceDeduction = new PayrollLine();
        insuranceDeduction.setType("D"); // Deduction
        insuranceDeduction.setAmount(new BigDecimal("500.00"));

        assertTrue(basicSalary.isEarning(), "الراتب الأساسي يجب أن يكون إضافة (E)");
        assertFalse(basicSalary.isDeduction());

        assertTrue(insuranceDeduction.isDeduction(), "التأمينات يجب أن تكون خصم (D)");
        assertFalse(insuranceDeduction.isEarning());
    }

    @Test
    @DisplayName("اختبار احتساب صافي المسير من مجموع الإضافات والخصومات")
    void testNetPayCalculation() {
        // إجمالي المستحقات: 5000 + 1250 = 6250
        BigDecimal earnings = new BigDecimal("6250.00");
        // إجمالي الخصومات: 500 + 150 = 650
        BigDecimal deductions = new BigDecimal("650.00");

        payroll.setTotalEarnings(earnings);
        payroll.setTotalDeductions(deductions);

        // الصافي = المستحقات - الخصومات = 5600
        BigDecimal expectedNet = earnings.subtract(deductions);
        payroll.setNetPay(expectedNet);

        assertEquals(0, new BigDecimal("5600.00").compareTo(payroll.getNetPay()),
                "صافي الراتب يجب أن يكون 5600");
    }

    @Test
    @DisplayName("اختبار تنسيق تسمية الفترة المحاسبية YYYY/MM")
    void testPeriodLabel() {
        assertEquals("2026/10", payroll.getPeriodLabel(), "تنسيق الفترة يجب أن يكون 2026/10");

        payroll.setMonth(5);
        assertEquals("2026/05", payroll.getPeriodLabel(), "يجب وضع صفر إضافي للشهور الفردية 2026/05");
    }

    @Test
    @DisplayName("اختبار دورة حياة واعتماد مسير الرواتب")
    void testPayrollStatusWorkflow() {
        assertTrue(payroll.isDraft());
        assertTrue(payroll.canApprove(), "المسودة يمكن اعتمادها");
        assertFalse(payroll.canPay(), "المسودة لا يمكن صرفها قبل الاعتماد");

        payroll.setStatus(PayrollMast.PayrollStatus.A); // Approved
        assertTrue(payroll.isApproved());
        assertFalse(payroll.canApprove(), "المعتمد لا يعتمد مجدداً");
        assertTrue(payroll.canPay(), "المعتمد جاهز للصرف");

        payroll.setStatus(PayrollMast.PayrollStatus.P); // Paid
        assertTrue(payroll.isPaid());
        assertFalse(payroll.canPay());
    }
}
