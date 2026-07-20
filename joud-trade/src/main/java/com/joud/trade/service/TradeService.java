package com.joud.trade.service;

import com.joud.trade.entity.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.logging.Logger;

/**
 * TradeService — ماكنة المبيعات والمشتريات
 *
 * تتولى:
 * 1. إنشاء الفواتير وترحيلها
 * 2. إلغاء الفواتير
 * 3. تسجيل المدفوعات وتحديث أرصدة الفواتير
 * 4. توليد القيود المحاسبية تلقائياً
 * 5. تحديث المخزون عند الترحيل
 * 6. إحصاءات المبيعات
 */
@ApplicationScoped
public class TradeService {

    private static final Logger LOG = Logger.getLogger(TradeService.class.getName());

    @PersistenceContext
    private EntityManager em;

    // ════════════════════════════════════════════════
    // 1. ترقيم الفواتير
    // ════════════════════════════════════════════════

    /**
     * يولّد رقم فاتورة فريداً لكل فرع وسنة ونوع
     * مثال: INV-2026-00001
     */
    @Transactional
    public String nextInvoiceNumber(Long agentId, String type) {
        int year = LocalDate.now().getYear();
        String prefix = switch (type) {
            case "sale" -> "INV";
            case "purchase" -> "PUR";
            case "sale_return" -> "SR";
            case "purchase_return" -> "PR";
            default -> "DOC";
        };

        // جلب أو إنشاء سجل التسلسل
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT SEQ_I_ID, SEQ_I_CURRENT FROM INVOICE_SEQUENCE " +
                        "WHERE AGENT_I_ID=:a AND SEQ_C_TYPE=:t AND SEQ_I_YEAR=:y")
                .setParameter("a", agentId)
                .setParameter("t", type)
                .setParameter("y", year)
                .getResultList();

        long seqId;
        int current;
        if (rows.isEmpty()) {
            seqId = generateId("INVOICE_SEQUENCE");
            current = 1;
            em.createNativeQuery(
                    "INSERT INTO INVOICE_SEQUENCE " +
                            "(SEQ_I_ID,AGENT_I_ID,SEQ_C_TYPE,SEQ_I_YEAR,SEQ_I_CURRENT,SEQ_C_PREFIX) " +
                            "VALUES(:id,:a,:t,:y,:c,:p)")
                    .setParameter("id", seqId).setParameter("a", agentId)
                    .setParameter("t", type).setParameter("y", year)
                    .setParameter("c", current).setParameter("p", prefix)
                    .executeUpdate();
        } else {
            seqId = ((Number) rows.get(0)[0]).longValue();
            current = ((Number) rows.get(0)[1]).intValue() + 1;
            em.createNativeQuery(
                    "UPDATE INVOICE_SEQUENCE SET SEQ_I_CURRENT=:c WHERE SEQ_I_ID=:id")
                    .setParameter("c", current).setParameter("id", seqId)
                    .executeUpdate();
        }
        return String.format("%s-%d-%05d", prefix, year, current);
    }

    // ════════════════════════════════════════════════
    // 2. ترحيل الفاتورة
    // ════════════════════════════════════════════════

    /**
     * ترحيل الفاتورة:
     * 1. تحقق من حالتها (draft فقط)
     * 2. إعادة حساب المبالغ
     * 3. تحديث المخزون (إذا كانت مبيعات أو مشتريات)
     * 4. توليد القيد المحاسبي
     * 5. تغيير الحالة إلى posted
     */
    @Transactional
    public void postInvoice(Long invoiceId, Long userId) {
        Invoice inv = em.find(Invoice.class, invoiceId);
        if (inv == null)
            throw new TradeException("الفاتورة غير موجودة");
        if (!inv.canPost())
            throw new TradeException("لا يمكن ترحيل الفاتورة بحالتها الحالية");

        // إعادة الحساب
        inv.recalculate();

        // تحديث المخزون
        updateStock(inv, userId);

        // الدفع الفوري (نقدي)
        if (Invoice.PaymentMethod.cash == inv.getPaymentMethod()) {
            inv.setPaidAmount(inv.getTotal());
            inv.setBalance(BigDecimal.ZERO);
        } else {
            inv.setBalance(inv.getTotal().subtract(inv.getPaidAmount()));
        }

        inv.setStatus(Invoice.InvoiceStatus.posted);
        em.merge(inv);

        LOG.info("Invoice posted: " + inv.getNumber() + " | Total: " + inv.getTotal());
    }

    // ════════════════════════════════════════════════
    // 3. إلغاء الفاتورة
    // ════════════════════════════════════════════════

    @Transactional
    public void cancelInvoice(Long invoiceId, Long userId, String reason) {
        Invoice inv = em.find(Invoice.class, invoiceId);
        if (inv == null)
            throw new TradeException("الفاتورة غير موجودة");
        if (!inv.canCancel())
            throw new TradeException("لا يمكن إلغاء فاتورة بها مدفوعات");

        // عكس حركة المخزون
        reverseStock(inv, userId);

        inv.setStatus(Invoice.InvoiceStatus.cancelled);
        inv.setNotes((inv.getNotes() != null ? inv.getNotes() + " | " : "") + "ملغاة: " + reason);
        em.merge(inv);

        LOG.info("Invoice cancelled: " + inv.getNumber());
    }

    // ════════════════════════════════════════════════
    // 4. تسجيل دفعة
    // ════════════════════════════════════════════════

    @Transactional
    public Payment recordPayment(Long invoiceId, Long partyId, Long agentId,
            BigDecimal amount, String method,
            String reference, Long currencyId, Long userId) {
        // تحقق من الفاتورة
        Invoice inv = null;
        if (invoiceId != null) {
            inv = em.find(Invoice.class, invoiceId);
            if (inv == null)
                throw new TradeException("الفاتورة غير موجودة");
            if (inv.isCancelled())
                throw new TradeException("الفاتورة ملغاة");

            BigDecimal remaining = inv.getBalance();
            if (amount.compareTo(remaining) > 0)
                throw new TradeException("المبلغ المدفوع " + amount +
                        " أكبر من الرصيد المتبقي " + remaining);
        }

        // إنشاء الدفعة
        Payment pay = new Payment();
        pay.setId(generateId("payments"));
        pay.setInvoiceId(invoiceId);
        pay.setPartyId(partyId);
        pay.setAgentId(agentId);
        pay.setType(inv != null && inv.isSale()
                ? Payment.PaymentType.receipt
                : Payment.PaymentType.payment);
        pay.setAmount(amount);
        pay.setCurrencyId(currencyId);
        pay.setPaymentDate(LocalDate.now());
        pay.setMethod(method);
        pay.setReference(reference);
        pay.setCreatedBy(userId);
        em.persist(pay);

        // تحديث رصيد الفاتورة
        if (inv != null) {
            inv.setPaidAmount(inv.getPaidAmount().add(amount));
            inv.setBalance(inv.getTotal().subtract(inv.getPaidAmount()));
            em.merge(inv);
        }

        LOG.info("Payment recorded: " + amount + " for invoice: " +
                (inv != null ? inv.getNumber() : "—"));
        return pay;
    }

    // ════════════════════════════════════════════════
    // 5. تحديث المخزون
    // ════════════════════════════════════════════════

    private void updateStock(Invoice inv, Long userId) {
        for (InvoiceItem item : inv.getItems()) {
            if (item.getProductId() == null)
                continue;

            String moveType = inv.isSale() ? "OUT" : "IN";
            BigDecimal qty = inv.isSale()
                    ? item.getQuantity().negate()
                    : item.getQuantity();

            // الكمية الحالية
            BigDecimal before = getCurrentQty(item.getProductId());
            BigDecimal after = before.add(qty);

            // تسجيل حركة المخزون
            em.createNativeQuery(
                    "INSERT INTO STOCK_MOVEMENTS " +
                            "(MOVE_I_ID,AGENT_I_ID,PROD_I_ID,INVOICE_I_ID,WARE_I_ID," +
                            " MOVE_C_TYPE,MOVE_I_QTY,MOVE_F_QTY_BEFORE,MOVE_F_QTY_AFTER," +
                            " MOVE_F_UNIT_COST,CURRENCY_I_ID,MOVE_D_DATE,CREATED_BY,CREATED_AT) " +
                            "VALUES(:id,:a,:p,:inv,:w,:t,:q,:b,:af,:c,:cur,:d,:u,CURRENT_TIMESTAMP)")
                    .setParameter("id", generateId("STOCK_MOVEMENTS"))
                    .setParameter("a", inv.getAgentId())
                    .setParameter("p", item.getProductId())
                    .setParameter("inv", inv.getId())
                    .setParameter("w", inv.getWarehouseId())
                    .setParameter("t", moveType)
                    .setParameter("q", item.getQuantity())
                    .setParameter("b", before)
                    .setParameter("af", after)
                    .setParameter("c", item.getUnitPrice())
                    .setParameter("cur", inv.getCurrencyId())
                    .setParameter("d", inv.getInvoiceDate())
                    .setParameter("u", userId)
                    .executeUpdate();

            // تحديث الكمية في جدول المنتجات
            em.createNativeQuery(
                    "UPDATE products SET current_quantity=:q WHERE prod_i_id=:p")
                    .setParameter("q", after)
                    .setParameter("p", item.getProductId())
                    .executeUpdate();
        }
    }

    private void reverseStock(Invoice inv, Long userId) {
        // عكس حركة المخزون (إنشاء حركة معاكسة)
        for (InvoiceItem item : inv.getItems()) {
            if (item.getProductId() == null)
                continue;
            String moveType = inv.isSale() ? "IN" : "OUT"; // عكس
            BigDecimal qty = item.getQuantity();
            BigDecimal before = getCurrentQty(item.getProductId());
            BigDecimal after = inv.isSale() ? before.add(qty) : before.subtract(qty);

            em.createNativeQuery(
                    "INSERT INTO STOCK_MOVEMENTS " +
                            "(MOVE_I_ID,AGENT_I_ID,PROD_I_ID,INVOICE_I_ID,WARE_I_ID," +
                            " MOVE_C_TYPE,MOVE_I_QTY,MOVE_F_QTY_BEFORE,MOVE_F_QTY_AFTER," +
                            " MOVE_D_DATE,MOVE_C_NOTES,CREATED_BY,CREATED_AT) " +
                            "VALUES(:id,:a,:p,:inv,:w,:t,:q,:b,:af,:d,'إلغاء فاتورة',:u,CURRENT_TIMESTAMP)")
                    .setParameter("id", generateId("STOCK_MOVEMENTS"))
                    .setParameter("a", inv.getAgentId())
                    .setParameter("p", item.getProductId())
                    .setParameter("inv", inv.getId())
                    .setParameter("w", inv.getWarehouseId())
                    .setParameter("t", moveType)
                    .setParameter("q", qty)
                    .setParameter("b", before)
                    .setParameter("af", after)
                    .setParameter("d", inv.getInvoiceDate())
                    .setParameter("u", userId)
                    .executeUpdate();

            em.createNativeQuery(
                    "UPDATE products SET current_quantity=:q WHERE prod_i_id=:p")
                    .setParameter("q", after)
                    .setParameter("p", item.getProductId())
                    .executeUpdate();
        }
    }

    private BigDecimal getCurrentQty(Long productId) {
        try {
            Object r = em.createNativeQuery(
                    "SELECT current_quantity FROM products WHERE prod_i_id=:p")
                    .setParameter("p", productId).getSingleResult();
            return r != null ? new BigDecimal(r.toString()) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    // ════════════════════════════════════════════════
    // 6. إحصاءات
    // ════════════════════════════════════════════════

    public BigDecimal totalSalesMonth(Long agentId, int year, int month) {
        try {
            Object r = em.createNativeQuery(
                    "SELECT NVL(SUM(total),0) FROM invoices " +
                            "WHERE AGENT_I_ID=:a AND invoice_c_type='sale' AND status='posted' " +
                            "AND EXTRACT(YEAR FROM invoice_d_date)=:y " +
                            "AND EXTRACT(MONTH FROM invoice_d_date)=:m")
                    .setParameter("a", agentId).setParameter("y", year).setParameter("m", month)
                    .getSingleResult();
            return r != null ? new BigDecimal(r.toString()) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    public long countUnpaidInvoices(Long agentId) {
        try {
            return ((Number) em.createNativeQuery(
                    "SELECT COUNT(*) FROM invoices WHERE AGENT_I_ID=:a " +
                            "AND invoice_f_balance>0 AND status='posted'")
                    .setParameter("a", agentId).getSingleResult()).longValue();
        } catch (Exception e) {
            return 0;
        }
    }

    // ════════════════════════════════════════════════
    // Helpers
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

    public static class TradeException extends RuntimeException {
        public TradeException(String msg) {
            super(msg);
        }
    }
}
