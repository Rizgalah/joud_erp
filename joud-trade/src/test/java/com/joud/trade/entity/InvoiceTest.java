package com.joud.trade.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("اختبارات صحة حسابات الفواتير التجارية — Invoice")
public class InvoiceTest {

    private Invoice invoice;

    @BeforeEach
    void setUp() {
        invoice = new Invoice();
        invoice.setId(1L);
        invoice.setNumber("INV-2026-00001");
        invoice.setType(Invoice.InvoiceType.sale);
        invoice.setStatus(Invoice.InvoiceStatus.draft);
        invoice.setTaxRate(new BigDecimal("15.00")); // 15% VAT
        invoice.setDiscountAmount(BigDecimal.ZERO);
        invoice.setPaidAmount(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("اختبار حساب سطر الفاتورة (الكمية × السعر - الخصم)")
    void testInvoiceItemCalculation() {
        InvoiceItem item = new InvoiceItem();
        item.setQuantity(new BigDecimal("5"));
        item.setUnitPrice(new BigDecimal("200.00"));
        item.setDiscountAmount(new BigDecimal("50.00"));

        item.recalculate();

        // 5 * 200 - 50 = 950.00000
        assertEquals(0, new BigDecimal("950.00000").compareTo(item.getTotal()),
                "يجب أن يكون إجمالي السطر 950");
    }

    @Test
    @DisplayName("اختبار إعادة حساب الفاتورة (المجموع الفرعي + الضريبة - الخصم = الإجمالي)")
    void testInvoiceRecalculate() {
        InvoiceItem item1 = new InvoiceItem();
        item1.setQuantity(new BigDecimal("2"));
        item1.setUnitPrice(new BigDecimal("500.00")); // 1000
        item1.setDiscountAmount(BigDecimal.ZERO);
        item1.recalculate();

        InvoiceItem item2 = new InvoiceItem();
        item2.setQuantity(new BigDecimal("1"));
        item2.setUnitPrice(new BigDecimal("1000.00")); // 1000
        item2.setDiscountAmount(BigDecimal.ZERO);
        item2.recalculate();

        invoice.getItems().add(item1);
        invoice.getItems().add(item2);

        // إجمالي البنود: 2000
        // خصم الفاتورة: 200
        invoice.setDiscountAmount(new BigDecimal("200.00"));
        // الضريبة: 15% من (2000 - 200 = 1800) = 270
        // الإجمالي: 1800 + 270 = 2070
        invoice.recalculate();

        assertEquals(0, new BigDecimal("2000.00").compareTo(invoice.getSubtotal()), "المجموع الفرعي غير صحيح");
        assertEquals(0, new BigDecimal("270.00000").compareTo(invoice.getTaxAmount()), "حساب الضريبة غير صحيح");
        assertEquals(0, new BigDecimal("2070.00000").compareTo(invoice.getTotal()), "إجمالي الفاتورة غير صحيح");
        assertEquals(0, new BigDecimal("2070.00000").compareTo(invoice.getBalance()), "المتبقي غير صحيح");
        assertFalse(invoice.isFullyPaid());
    }

    @Test
    @DisplayName("اختبار سداد الفاتورة بالكامل وتحديث المتبقي")
    void testInvoicePaymentAndBalance() {
        InvoiceItem item = new InvoiceItem();
        item.setQuantity(new BigDecimal("1"));
        item.setUnitPrice(new BigDecimal("1000.00"));
        item.setDiscountAmount(BigDecimal.ZERO);
        item.recalculate();

        invoice.setTaxRate(BigDecimal.ZERO);
        invoice.setDiscountAmount(BigDecimal.ZERO);
        invoice.getItems().add(item);
        invoice.recalculate();

        assertEquals(0, new BigDecimal("1000.00").compareTo(invoice.getTotal()));

        // تسديد جزء
        invoice.setPaidAmount(new BigDecimal("400.00"));
        invoice.recalculate();
        assertEquals(0, new BigDecimal("600.00").compareTo(invoice.getBalance()));
        assertFalse(invoice.isFullyPaid());

        // تسديد الباقي بالكامل
        invoice.setPaidAmount(new BigDecimal("1000.00"));
        invoice.recalculate();
        assertEquals(0, BigDecimal.ZERO.compareTo(invoice.getBalance()));
        assertTrue(invoice.isFullyPaid());
    }

    @Test
    @DisplayName("اختبار قواعد إمكانية الترحيل والإلغاء للفواتير")
    void testInvoicePostAndCancelRules() {
        // فاتورة مسودة بدون بنود لا يمكن ترحيلها
        assertFalse(invoice.canPost());

        InvoiceItem item = new InvoiceItem();
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(new BigDecimal("100.00"));
        item.recalculate();
        invoice.getItems().add(item);

        // أصبحت تحتوي بنود ومسودة -> يمكن ترحيلها
        assertTrue(invoice.canPost());
        assertTrue(invoice.canCancel());

        // بعد السداد لا يمكن الإلغاء
        invoice.setPaidAmount(new BigDecimal("50.00"));
        assertFalse(invoice.canCancel());
    }
}
