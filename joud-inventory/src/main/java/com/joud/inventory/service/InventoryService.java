package com.joud.inventory.service;

import com.joud.inventory.entity.*;
import com.joud.security.Secured;

import jakarta.enterprise.context.ApplicationScoped;

import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

/**
 * InventoryService — ماكنة المخزون
 *
 * تتولى:
 * 1. إدارة الأصناف (CRUD + تحديث المتوسط)
 * 2. تسجيل حركات يدوية (تسوية / تحويل)
 * 3. إنشاء وترحيل جلسة الجرد
 * 4. حساب متوسط التكلفة (Moving Average)
 * 5. تنبيهات إعادة الطلب
 * 6. إحصاءات المخزون
 */
@ApplicationScoped

public class InventoryService {

    private static final Logger LOG = Logger.getLogger(InventoryService.class.getName());

    @PersistenceContext
    @Context
    private SecurityContext securityContext;
    private EntityManager em;

    @Secured({ "INVENTORY_MANAGER", "SALES_MAN" })
    @Produces(MediaType.APPLICATION_JSON)

    // ════════════════════════════════════════════════
    // 1. حركة يدوية — تسوية / تحويل
    // ════════════════════════════════════════════════

    /**
     * تسجيل حركة مخزون يدوية
     * النوع: ADJ=تسوية, TRF=تحويل بين المستودعات
     */
    @Transactional
    public void recordManualMovement(Long productId, Long agentId, Long warehouseId,
            String moveType, BigDecimal qty, BigDecimal unitCost,
            Long currencyId, String notes, Long userId) {
        String currentUser = securityContext.getUserPrincipal().getName();
        Product prod = em.find(Product.class, productId);
        if (prod == null)
            throw new InventoryException("الصنف غير موجود");
        if (!prod.isTracked())
            throw new InventoryException("هذا الصنف لا يتتبع المخزون");

        BigDecimal before = prod.getCurrentQty();
        BigDecimal after;

        if ("ADJ".equals(moveType)) {
            // التسوية: qty هي الكمية الجديدة المباشرة
            after = qty;
        } else if ("IN".equals(moveType)) {
            after = before.add(qty);
        } else if ("OUT".equals(moveType)) {
            after = before.subtract(qty);
            if (after.compareTo(BigDecimal.ZERO) < 0)
                throw new InventoryException("الكمية المطلوبة أكبر من المخزون المتاح: " + before);
        } else {
            after = before.add(qty); // TRF وغيرها
        }

        // تحديث متوسط التكلفة (عند الإضافة فقط)
        if ("IN".equals(moveType) && unitCost != null && unitCost.compareTo(BigDecimal.ZERO) > 0) {
            updateMovingAverage(prod, qty, unitCost);
        }

        // تسجيل الحركة
        em.createNativeQuery(
                "INSERT INTO STOCK_MOVEMENTS " +
                        "(MOVE_I_ID,AGENT_I_ID,PROD_I_ID,WARE_I_ID,MOVE_C_TYPE," +
                        " MOVE_I_QTY,MOVE_F_QTY_BEFORE,MOVE_F_QTY_AFTER," +
                        " MOVE_F_UNIT_COST,CURRENCY_I_ID,MOVE_D_DATE,MOVE_C_NOTES," +
                        " CREATED_BY,CREATED_AT) " +
                        "VALUES(:id,:a,:p,:w,:t,:q,:b,:af,:c,:cur,:d,:n,:u,CURRENT_TIMESTAMP)")
                .setParameter("id", generateId("STOCK_MOVEMENTS"))
                .setParameter("a", agentId)
                .setParameter("p", productId)
                .setParameter("w", warehouseId)
                .setParameter("t", moveType)
                .setParameter("q", qty.abs())
                .setParameter("b", before)
                .setParameter("af", after)
                .setParameter("c", unitCost != null ? unitCost : prod.getAvgCost())
                .setParameter("cur", currencyId)
                .setParameter("d", LocalDate.now())
                .setParameter("n", notes)
                .setParameter("u", userId)
                .executeUpdate();

        // تحديث كمية الصنف
        prod.setCurrentQty(after);
        em.merge(prod);

        // تحقق من تنبيه إعادة الطلب
        checkReorderAlert(prod, agentId, warehouseId);

        LOG.info("Manual movement: " + moveType + " | " + prod.getCode() +
                " | " + before + " → " + after + " |currentUser:" + currentUser);

    }

    // ════════════════════════════════════════════════
    // 2. جلسة الجرد
    // ════════════════════════════════════════════════

    /**
     * إنشاء جلسة جرد جديدة وتعبئة الأصناف من قاعدة البيانات
     */
    @Transactional
    public StockCountMast createStockCount(Long agentId, Long warehouseId,
            String description, String scope,
            Long categoryId, Long userId) {
        String currentUser = securityContext.getUserPrincipal().getName();
        StockCountMast count = new StockCountMast();
        count.setId(generateId("STOCK_COUNT_MAST"));
        count.setAgentId(agentId);
        count.setWarehouseId(warehouseId);
        count.setDescription(description);
        count.setScope(scope);
        count.setCategoryId(categoryId);
        count.setStatus(StockCountMast.CountStatus.I);
        count.setCreatedBy(userId);
        em.persist(count);

        // جلب الأصناف حسب النطاق
        List<Product> products = switch (scope) {
            case "C" -> em.createNamedQuery("Product.findByCategory", Product.class)
                    .setParameter("catId", categoryId).getResultList();
            default -> em.createNamedQuery("Product.findActive", Product.class).getResultList();
        };

        // إنشاء سطر لكل صنف بكميته النظامية الحالية
        int lineNum = 0;
        for (Product p : products) {
            if (!p.isTracked())
                continue;
            StockCountLine line = new StockCountLine();
            line.setId(generateId("STOCK_COUNT_LINE"));
            line.setCountId(count.getId());
            line.setProductId(p.getId());
            line.setWarehouseId(warehouseId);
            line.setSysQty(p.getCurrentQty());
            line.setActualQty(p.getCurrentQty()); // الافتراضي = النظامي
            line.setUnitCost(p.getAvgCost());
            line.setCountedAt(LocalDateTime.now());
            line.setCountedBy(userId);
            line.recalculate();
            em.persist(line);
            lineNum++;
        }

        count.setItemCount(lineNum);
        em.merge(count);

        LOG.info("Stock count created: " + description + " | " + lineNum + " items" + " |currentUser:" + currentUser);
        return count;
    }

    /**
     * ترحيل الجرد: تطبيق الفروق على المخزون الفعلي
     */
    @Transactional
    public void postStockCount(Long countId, Long userId) {
        String currentUser = securityContext.getUserPrincipal().getName();
        StockCountMast count = em.find(StockCountMast.class, countId);
        if (count == null)
            throw new InventoryException("جلسة الجرد غير موجودة");
        if (count.isPosted())
            throw new InventoryException("تم ترحيل هذه الجلسة مسبقاً");

        BigDecimal totalDiff = BigDecimal.ZERO;
        int diffCount = 0;

        for (StockCountLine line : count.getLines()) {
            line.recalculate();
            if (line.hasDifference()) {
                // سجّل حركة تسوية للفرق
                BigDecimal diff = line.getDiffQty();
                em.createNativeQuery(
                        "INSERT INTO STOCK_MOVEMENTS " +
                                "(MOVE_I_ID,AGENT_I_ID,PROD_I_ID,WARE_I_ID,MOVE_C_TYPE," +
                                " MOVE_I_QTY,MOVE_F_QTY_BEFORE,MOVE_F_QTY_AFTER," +
                                " MOVE_F_UNIT_COST,MOVE_D_DATE,MOVE_C_NOTES,CREATED_BY,CREATED_AT) " +
                                "VALUES(:id,:a,:p,:w,:t,:q,:b,:af,:c,:d,:n,:u,CURRENT_TIMESTAMP)")
                        .setParameter("id", generateId("STOCK_MOVEMENTS"))
                        .setParameter("a", count.getAgentId())
                        .setParameter("p", line.getProductId())
                        .setParameter("w", line.getWarehouseId())
                        .setParameter("t", "ADJ")
                        .setParameter("q", diff.abs())
                        .setParameter("b", line.getSysQty())
                        .setParameter("af", line.getActualQty())
                        .setParameter("c", line.getUnitCost())
                        .setParameter("d", count.getCountDate())
                        .setParameter("n", "جرد: " + count.getDescription())
                        .setParameter("u", userId)
                        .executeUpdate();

                // تحديث كمية الصنف
                em.createNativeQuery(
                        "UPDATE products SET current_quantity=:q WHERE prod_i_id=:p")
                        .setParameter("q", line.getActualQty())
                        .setParameter("p", line.getProductId())
                        .executeUpdate();

                totalDiff = totalDiff.add(line.getDiffValue().abs());
                diffCount++;
            }
        }

        count.setStatus(StockCountMast.CountStatus.P);
        count.setApprovedBy(userId);
        count.setApprovedAt(LocalDate.now());
        count.setDiffValue(totalDiff);
        em.merge(count);

        LOG.info("Stock count posted: " + count.getDescription() +
                " | Differences: " + diffCount + " | Total diff value: " + totalDiff + " |currentUser:" + currentUser);
    }

    // ════════════════════════════════════════════════
    // 3. متوسط التكلفة (Moving Average)
    // ════════════════════════════════════════════════

    private void updateMovingAverage(Product prod, BigDecimal inQty, BigDecimal inCost) {
        BigDecimal curQty = prod.getCurrentQty() != null ? prod.getCurrentQty() : BigDecimal.ZERO;
        BigDecimal curCost = prod.getAvgCost() != null ? prod.getAvgCost() : BigDecimal.ZERO;

        BigDecimal totalQty = curQty.add(inQty);
        BigDecimal totalValue = curQty.multiply(curCost).add(inQty.multiply(inCost));

        if (totalQty.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal newAvg = totalValue.divide(totalQty, 5, RoundingMode.HALF_UP);
            prod.setAvgCost(newAvg);
        }
    }

    // ════════════════════════════════════════════════
    // 4. تنبيهات إعادة الطلب
    // ════════════════════════════════════════════════

    private void checkReorderAlert(Product prod, Long agentId, Long warehouseId) {
        String currentUser = securityContext.getUserPrincipal().getName();
        if (!prod.isBelowMin())
            return;

        // تحقق: هل يوجد تنبيه مفتوح مسبقاً؟
        long existing = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM REORDER_ALERT " +
                        "WHERE PROD_I_ID=:p AND AGENT_I_ID=:a AND ALERT_C_STS IN('N','R')")
                .setParameter("p", prod.getId())
                .setParameter("a", agentId)
                .getSingleResult()).longValue();

        if (existing == 0) {
            em.createNativeQuery(
                    "INSERT INTO REORDER_ALERT " +
                            "(ALERT_I_ID,PROD_I_ID,WARE_I_ID,AGENT_I_ID,ALERT_F_QTY,ALERT_F_MIN,ALERT_C_STS,CREATED_AT) "
                            +
                            "VALUES(:id,:p,:w,:a,:q,:m,'N',CURRENT_TIMESTAMP)")
                    .setParameter("id", generateId("REORDER_ALERT"))
                    .setParameter("p", prod.getId())
                    .setParameter("w", warehouseId)
                    .setParameter("a", agentId)
                    .setParameter("q", prod.getCurrentQty())
                    .setParameter("m", prod.getMinQty())
                    .executeUpdate();
            LOG.warning("Reorder alert created for: " + prod.getCode() + "currentUser:" + currentUser);
        }
    }

    // ════════════════════════════════════════════════
    // 5. إحصاءات
    // ════════════════════════════════════════════════

    public long countActiveProducts(Long agentId) {
        try {
            return ((Number) em.createNativeQuery(
                    "SELECT COUNT(*) FROM products WHERE AGENT_I_ID=:a AND prod_c_status='A'")
                    .setParameter("a", agentId).getSingleResult()).longValue();
        } catch (Exception e) {
            return 0;
        }
    }

    public long countBelowMin() {
        try {
            return ((Number) em.createNativeQuery(
                    "SELECT COUNT(*) FROM products " +
                            "WHERE current_quantity<=min_quantity AND prod_c_status='A' AND prod_c_track_stock='Y'")
                    .getSingleResult()).longValue();
        } catch (Exception e) {
            return 0;
        }
    }

    public BigDecimal totalStockValue(Long agentId) {
        try {
            Object r = em.createNativeQuery(
                    "SELECT NVL(SUM(current_quantity*prod_f_avg_cost),0) " +
                            "FROM products WHERE AGENT_I_ID=:a AND prod_c_status='A'")
                    .setParameter("a", agentId).getSingleResult();
            return r != null ? new BigDecimal(r.toString()) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    public long countPendingAlerts(Long agentId) {
        try {
            return ((Number) em.createNativeQuery(
                    "SELECT COUNT(*) FROM REORDER_ALERT " +
                            "WHERE AGENT_I_ID=:a AND ALERT_C_STS='N'")
                    .setParameter("a", agentId).getSingleResult()).longValue();
        } catch (Exception e) {
            return 0;
        }
    }

    // ════════════════════════════════════════════════
    // Helper
    // ════════════════════════════════════════════════

    private Long generateId(String table) {
        try {
            return ((Number) em.createNativeQuery(
                    "SELECT ID_I_GEN FROM KEY_GENERATION WHERE TABLE_C_NAME=:t")
                    .setParameter("t", table).getSingleResult()).longValue();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    public static class InventoryException extends RuntimeException {
        public InventoryException(String msg) {
            super(msg);
        }
    }
}
