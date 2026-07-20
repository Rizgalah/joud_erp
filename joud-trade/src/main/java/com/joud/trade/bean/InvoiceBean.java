package com.joud.trade.bean;

import com.joud.trade.entity.*;
import com.joud.trade.service.TradeService;
import com.joud.core.security.filter.AuthFilter;
import com.joud.core.security.session.UserSession;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * InvoiceBean — الفواتير (مبيعات + مشتريات)
 */
@Named("invBean")
@ViewScoped
public class InvoiceBean implements Serializable {

    // ─── الفاتورة الحالية ────────────────────────────
    private Invoice        invoice     = new Invoice();
    private List<InvoiceItem> items   = new ArrayList<>();

    // ─── سطر الإدخال ────────────────────────────────
    private Long           lineProductId;
    private String         lineProductName;
    private String         lineProductCode;
    private String         lineUnitName;
    private BigDecimal     lineQty      = BigDecimal.ONE;
    private BigDecimal     linePrice    = BigDecimal.ZERO;
    private BigDecimal     lineDisc     = BigDecimal.ZERO;

    // ─── وضع الشاشة ─────────────────────────────────
    private String         invoiceType  = "sale"; // sale | purchase
    private boolean        editMode     = false;
    private List<Invoice>  invoiceList;

    // ─── قوائم الاختيار ─────────────────────────────
    private List<Object[]> customers;
    private List<Object[]> suppliers;
    private List<Object[]> products;
    private List<Object[]> warehouses;
    private List<Object[]> currencies;
    private List<Object[]> employees;

    // ─── إحصاءات ────────────────────────────────────
    private BigDecimal monthSales;
    private long       unpaidCount;

    private Long agentId;

    @Inject private TradeService tradeService;
    @PersistenceContext private EntityManager em;

    @PostConstruct
    public void init() {
        UserSession user = getCurrentUser();
        if (user != null) agentId = user.getAgentId();
        initNewInvoice();
        loadLookups();
        loadInvoiceList();
        loadStats();
    }

    // ════════════════════════════════════════════════
    // تهيئة
    // ════════════════════════════════════════════════

    public void initNewInvoice() {
        invoice = new Invoice();
        invoice.setAgentId(agentId);
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setStatus(Invoice.InvoiceStatus.draft);
        invoice.setType(Invoice.InvoiceType.valueOf(invoiceType));
        invoice.setPaymentMethod(Invoice.PaymentMethod.cash);

        // توليد الرقم مسبقاً (مؤقت حتى الحفظ)
        items.clear();
        clearLineInputs();
        editMode = false;
    }

    public void switchType(String type) {
        this.invoiceType = type;
        initNewInvoice();
    }

    // ════════════════════════════════════════════════
    // إدارة الأسطر
    // ════════════════════════════════════════════════

    public void onProductSelect() {
        if (lineProductId == null) return;
        try {
            Object[] prod = (Object[]) em.createNativeQuery(
                "SELECT prod_c_name, prod_c_code, u.unit_short_name, " +
                "prod.sale_price, prod.purchase_price " +
                "FROM products prod " +
                "LEFT JOIN units u ON prod.unit_i_id = u.unit_i_id " +
                "WHERE prod.prod_i_id=:id")
                .setParameter("id", lineProductId).getSingleResult();

            lineProductName = prod[0] != null ? prod[0].toString() : "";
            lineProductCode = prod[1] != null ? prod[1].toString() : "";
            lineUnitName    = prod[2] != null ? prod[2].toString() : "";
            linePrice = new BigDecimal("sale".equals(invoiceType)
                ? prod[3].toString() : prod[4].toString());
        } catch (Exception ignored) {}
    }

    public void addLine() {
        if (lineProductName == null || lineProductName.isBlank()) {
            addError("يجب تحديد الصنف"); return;
        }
        if (lineQty == null || lineQty.compareTo(BigDecimal.ZERO) <= 0) {
            addError("الكمية يجب أن تكون أكبر من صفر"); return;
        }

        InvoiceItem item = new InvoiceItem();
        item.setId(System.nanoTime());
        item.setProductId(lineProductId);
        item.setProductName(lineProductName);
        item.setProductCode(lineProductCode);
        item.setUnitName(lineUnitName);
        item.setQuantity(lineQty);
        item.setUnitPrice(linePrice);
        item.setDiscountAmount(lineDisc != null ? lineDisc : BigDecimal.ZERO);
        item.setCurrencyId(invoice.getCurrencyId());
        item.setAgentId(agentId);
        item.recalculate();
        items.add(item);

        invoice.setItems(items);
        invoice.recalculate();
        clearLineInputs();
    }

    public void removeLine(int idx) {
        if (idx >= 0 && idx < items.size()) {
            items.remove(idx);
            invoice.setItems(items);
            invoice.recalculate();
        }
    }

    public void onDiscountChange() { invoice.recalculate(); }
    public void onTaxChange()      { invoice.recalculate(); }

    // ════════════════════════════════════════════════
    // حفظ وترحيل
    // ════════════════════════════════════════════════

    @Transactional
    public void saveDraft() {
        if (items.isEmpty()) { addError("لا توجد أصناف في الفاتورة"); return; }
        try {
            invoice.setNumber(tradeService.nextInvoiceNumber(agentId, invoiceType));
            invoice.setItems(items);
            invoice.recalculate();
            invoice.setCreatedBy(getCurrentUserId());
            em.persist(invoice);
            for (InvoiceItem item : items) {
                item.setId(generateId("invoice_items"));
                item.setInvoiceId(invoice.getId());
                item.setCreatedBy(getCurrentUserId());
                em.persist(item);
            }
            addInfo("تم حفظ الفاتورة: " + invoice.getNumber());
            loadInvoiceList();
            initNewInvoice();
        } catch (Exception e) { addError("خطأ في الحفظ: " + e.getMessage()); }
    }

    public void post(Long invoiceId) {
        try {
            tradeService.postInvoice(invoiceId, getCurrentUserId());
            addInfo("تم ترحيل الفاتورة");
            loadInvoiceList();
        } catch (TradeService.TradeException e) { addError(e.getMessage()); }
    }

    public void cancel(Long invoiceId) {
        try {
            tradeService.cancelInvoice(invoiceId, getCurrentUserId(), "إلغاء يدوي");
            addInfo("تم إلغاء الفاتورة");
            loadInvoiceList();
        } catch (TradeService.TradeException e) { addError(e.getMessage()); }
    }

    // ════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════

    private void clearLineInputs() {
        lineProductId = null; lineProductName = null;
        lineProductCode = null; lineUnitName = null;
        lineQty = BigDecimal.ONE; linePrice = BigDecimal.ZERO; lineDisc = BigDecimal.ZERO;
    }

    private void loadInvoiceList() {
        try {
            invoiceList = em.createNamedQuery("Invoice.findByAgent", Invoice.class)
                .setParameter("agentId", agentId)
                .setParameter("type", Invoice.InvoiceType.valueOf(invoiceType))
                .setMaxResults(50).getResultList();
        } catch (Exception e) { invoiceList = List.of(); }
    }

    private void loadStats() {
        LocalDate now = LocalDate.now();
        monthSales  = tradeService.totalSalesMonth(agentId, now.getYear(), now.getMonthValue());
        unpaidCount = tradeService.countUnpaidInvoices(agentId);
    }

    @SuppressWarnings("unchecked")
    private void loadLookups() {
        try {
            customers  = em.createNativeQuery(
                "SELECT parties_i_id,parties_c_name FROM parties " +
                "WHERE AGENT_I_ID=:a AND parties_c_type IN('customer','both') AND is_active=1 ORDER BY parties_c_name")
                .setParameter("a",agentId).getResultList();
            suppliers  = em.createNativeQuery(
                "SELECT parties_i_id,parties_c_name FROM parties " +
                "WHERE AGENT_I_ID=:a AND parties_c_type IN('supplier','both') AND is_active=1 ORDER BY parties_c_name")
                .setParameter("a",agentId).getResultList();
            products   = em.createNativeQuery(
                "SELECT prod_i_id,prod_c_name,prod_c_code,sale_price,purchase_price,current_quantity " +
                "FROM products ORDER BY prod_c_name")
                .getResultList();
            warehouses = em.createNativeQuery(
                "SELECT ware_i_id,ware_c_name FROM warehouses WHERE is_active=1 ORDER BY ware_c_name")
                .getResultList();
            currencies = em.createNativeQuery(
                "SELECT CURRENCY_I_ID,CURRENCY_C_DESC,CURRENCY_C_SYMBOL FROM CURRENCY_MAST ORDER BY CURRENCY_C_DESC")
                .getResultList();
            employees  = em.createNativeQuery(
                "SELECT EMP_i_id,EMP_C_NAME FROM EMPLOYEE_MAST WHERE AGENT_I_ID=:a AND EMP_C_STATUS='A' ORDER BY EMP_C_NAME")
                .setParameter("a",agentId).getResultList();
        } catch (Exception e) {
            customers=suppliers=products=warehouses=currencies=employees=List.of();
        }
    }

    public String getPartyName(Long partyId) {
        List<Object[]> src = "sale".equals(invoiceType) ? customers : suppliers;
        if (partyId==null||src==null) return "—";
        return src.stream().filter(r->partyId.equals(((Number)r[0]).longValue()))
            .map(r->r[1].toString()).findFirst().orElse("—");
    }

    private UserSession getCurrentUser() {
        return (UserSession) FacesContext.getCurrentInstance()
            .getExternalContext().getRequestMap().get(AuthFilter.SESSION_ATTR);
    }
    private Long getCurrentUserId() {
        UserSession u = getCurrentUser();
        return u != null ? u.getUserId() : 0L;
    }
    private Long generateId(String t) {
        try { return ((Number) em.createNativeQuery(
            "SELECT ID_I_GEN FROM KEY_GENERATION WHERE TABLE_C_NAME=:t")
            .setParameter("t",t).getSingleResult()).longValue();
        } catch (Exception e) { return System.currentTimeMillis(); }
    }
    private void addInfo(String m)  {
        FacesContext.getCurrentInstance().addMessage(null,new FacesMessage(FacesMessage.SEVERITY_INFO,m,null));
    }
    private void addError(String m) {
        FacesContext.getCurrentInstance().addMessage(null,new FacesMessage(FacesMessage.SEVERITY_ERROR,m,null));
    }

    // ─── Computed ───────────────────────────────────
    public BigDecimal getGrandTotal()   { return invoice.getTotal(); }
    public BigDecimal getSubtotal()     { return invoice.getSubtotal(); }
    public boolean    isSaleMode()      { return "sale".equals(invoiceType); }
    public List<Object[]> getParties()  { return isSaleMode() ? customers : suppliers; }

    // Getters & Setters
    public Invoice getInvoice()                      { return invoice; }
    public void setInvoice(Invoice v)                { this.invoice = v; }
    public List<InvoiceItem> getItems()              { return items; }
    public String getInvoiceType()                   { return invoiceType; }
    public void setInvoiceType(String v)             { this.invoiceType = v; }
    public boolean isEditMode()                      { return editMode; }
    public List<Invoice> getInvoiceList()            { return invoiceList; }
    public List<Object[]> getCustomers()             { return customers; }
    public List<Object[]> getSuppliers()             { return suppliers; }
    public List<Object[]> getProducts()              { return products; }
    public List<Object[]> getWarehouses()            { return warehouses; }
    public List<Object[]> getCurrencies()            { return currencies; }
    public List<Object[]> getEmployees()             { return employees; }
    public BigDecimal getMonthSales()                { return monthSales; }
    public long getUnpaidCount()                     { return unpaidCount; }
    public Long getLineProductId()                   { return lineProductId; }
    public void setLineProductId(Long v)             { this.lineProductId=v; }
    public String getLineProductName()               { return lineProductName; }
    public void setLineProductName(String v)         { this.lineProductName=v; }
    public String getLineProductCode()               { return lineProductCode; }
    public void setLineProductCode(String v)         { this.lineProductCode=v; }
    public String getLineUnitName()                  { return lineUnitName; }
    public void setLineUnitName(String v)            { this.lineUnitName=v; }
    public BigDecimal getLineQty()                   { return lineQty; }
    public void setLineQty(BigDecimal v)             { this.lineQty=v; }
    public BigDecimal getLinePrice()                 { return linePrice; }
    public void setLinePrice(BigDecimal v)           { this.linePrice=v; }
    public BigDecimal getLineDisc()                  { return lineDisc; }
    public void setLineDisc(BigDecimal v)            { this.lineDisc=v; }
}
