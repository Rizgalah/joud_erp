package com.joud.inventory.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("اختبارات صحة الأصناف والمخزون — Product")
public class ProductTest {

    private Product product;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setId(10L);
        product.setCode("PRD-001");
        product.setName("لابتوب ديل");
        product.setTrackStock("Y");
        product.setCurrentQty(new BigDecimal("20"));
        product.setMinQty(new BigDecimal("5"));
        product.setPurchasePrice(new BigDecimal("1000.00"));
        product.setSalePrice(new BigDecimal("1250.00"));
        product.setAvgCost(new BigDecimal("950.00"));
    }

    @Test
    @DisplayName("اختبار متابعة المخزون للأصناف المتتبعة والخدمية")
    void testTrackStock() {
        assertTrue(product.isTracked(), "الصنف يجب أن يكون متتبعاً للمخزون");

        product.setTrackStock("N");
        assertFalse(product.isTracked(), "الصنف الخدمي لا يتتبع المخزون");
    }

    @Test
    @DisplayName("اختبار شرط إعادة الطلب عندما يصل الرصيد للحد الأدنى أو أقل منه")
    void testIsBelowMin() {
        // الكمية الحالية 20 والحد الأدنى 5 -> لا يحتاج إعادة طلب
        assertFalse(product.isBelowMin());

        // الكمية الحالية نزلت إلى 5 -> يجب تفعيل تنبيه إعادة الطلب
        product.setCurrentQty(new BigDecimal("5"));
        assertTrue(product.isBelowMin(), "عند التساوي مع الحد الأدنى يجب تفعيل التنبيه");

        // الكمية الحالية نزلت إلى 2 -> تحت الحد الأدنى
        product.setCurrentQty(new BigDecimal("2"));
        assertTrue(product.isBelowMin());
    }

    @Test
    @DisplayName("اختبار حساب القيمة الإجمالية للمخزون (الكمية × متوسط التكلفة)")
    void testGetStockValue() {
        // 20 * 950 = 19000
        BigDecimal expectedValue = new BigDecimal("19000.00");
        assertEquals(0, expectedValue.compareTo(product.getStockValue()),
                "قيمة المخزون يجب أن تكون 19000");
    }

    @Test
    @DisplayName("اختبار حساب هامش الربح المئوي")
    void testGetProfitMargin() {
        // سعر البيع: 1250, سعر الشراء: 1000 -> الربح: 250 -> الهامش: 25%
        BigDecimal margin = product.getProfitMargin();
        assertEquals(0, new BigDecimal("25.0000").compareTo(margin),
                "هامش الربح يجب أن يكون 25%");
    }
}
