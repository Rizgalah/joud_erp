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
import java.util.List;

// ══════════════════════════════════════════════════════════════
// PartiesBean — إدارة العملاء والموردين
// ══════════════════════════════════════════════════════════════
@Named("partiesBean")
@ViewScoped
class PartiesBean implements Serializable {

    private List<Party> parties;
    private List<Party> filtered;
    private Party selected = new Party();
    private boolean editMode = false;
    private boolean showDialog = false;
    private String filterType = "all"; // all | customer | supplier
    private String searchQ;

    // كشف حساب الطرف
    private Party stmtParty;
    private List<Object[]> stmtInvoices;
    private BigDecimal stmtTotalDebit = BigDecimal.ZERO;
    private BigDecimal stmtTotalCredit = BigDecimal.ZERO;
    private BigDecimal stmtBalance = BigDecimal.ZERO;
    private boolean showStmt = false;

    private Long agentId;

    @PersistenceContext
    private EntityManager em;

    @PostConstruct
    public void init() {
        UserSession u = getCurrentUser();
        if (u != null)
            agentId = u.getAgentId();
        loadParties();
    }

    // ─── تحميل ─────────────────────────────────────
    public void loadParties() {
        try {
            String jpql = "SELECT p FROM Party p WHERE p.agentId=:a AND p.active=1 ";
            if ("customer".equals(filterType))
                jpql += "AND p.type IN ('customer','both') ";
            else if ("supplier".equals(filterType))
                jpql += "AND p.type IN ('supplier','both') ";
            if (searchQ != null && !searchQ.isBlank())
                jpql += "AND (LOWER(p.name) LIKE LOWER(CONCAT('%',:q,'%')) " +
                        " OR p.phone LIKE CONCAT('%',:q,'%')) ";
            jpql += "ORDER BY p.name";

            var q = em.createQuery(jpql, Party.class).setParameter("a", agentId);
            if (searchQ != null && !searchQ.isBlank())
                q.setParameter("q", searchQ);
            parties = q.getResultList();
        } catch (Exception e) {
            parties = List.of();
        }
    }

    // ─── CRUD ───────────────────────────────────────
    public void prepareNew() {
        selected = new Party();
        selected.setAgentId(agentId);
        selected.setActive(1);
        selected.setCreditStatus(Party.CreditStatus.A);
        editMode = false;
        showDialog = true;
    }

    public void prepareEdit(Party p) {
        selected = em.find(Party.class, p.getId());
        editMode = true;
        showDialog = true;
    }

    @Transactional
    public void save() {
        try {
            if (editMode) {
                em.merge(selected);
                addInfo("تم تحديث: " + selected.getName());
            } else {
                selected.setId(generateId());
                selected.setCode(nextCode());
                em.persist(selected);
                addInfo("تمت الإضافة: " + selected.getName());
            }
            showDialog = false;
            loadParties();
        } catch (Exception e) {
            addError("خطأ: " + e.getMessage());
        }
    }

    @Transactional
    public void deactivate(Party p) {
        Party e = em.find(Party.class, p.getId());
        if (e != null) {
            e.setActive(0);
            em.merge(e);
        }
        addInfo("تم الحذف: " + p.getName());
        loadParties();
    }

    // ─── كشف الحساب ────────────────────────────────
    @SuppressWarnings("unchecked")
    public void viewStatement(Party p) {
        stmtParty = p;
        stmtInvoices = em.createNativeQuery(
                "SELECT invoice_number, invoice_c_type, invoice_d_date, " +
                        "       total, invoice_f_paid, invoice_f_balance, status " +
                        "FROM invoices WHERE party_id=:pid AND status<>'cancelled' " +
                        "ORDER BY invoice_d_date ASC")
                .setParameter("pid", p.getId()).getResultList();

        stmtTotalDebit = BigDecimal.ZERO;
        stmtTotalCredit = BigDecimal.ZERO;
        for (Object[] row : stmtInvoices) {
            BigDecimal total = new BigDecimal(row[3].toString());
            if ("sale".equals(row[1].toString()))
                stmtTotalDebit = stmtTotalDebit.add(total);
            else
                stmtTotalCredit = stmtTotalCredit.add(total);
        }
        stmtBalance = stmtTotalDebit.subtract(stmtTotalCredit);
        showStmt = true;
    }

    public void closeStatement() {
        showStmt = false;
    }

    public void search() {
        loadParties();
    }

    public void resetSearch() {
        searchQ = null;
        filterType = "all";
        loadParties();
    }

    // ─── Helpers ───────────────────────────────────
    private String nextCode() {
        try {
            Object r = em.createNativeQuery(
                    "SELECT COUNT(*)+1 FROM parties WHERE AGENT_I_ID=:a")
                    .setParameter("a", agentId).getSingleResult();
            return String.format("PRT-%05d", ((Number) r).longValue());
        } catch (Exception e) {
            return "PRT-00001";
        }
    }

    private Long generateId() {
        try {
            return ((Number) em.createNativeQuery(
                    "SELECT ID_I_GEN FROM KEY_GENERATION WHERE TABLE_C_NAME='parties'")
                    .getSingleResult()).longValue();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private UserSession getCurrentUser() {
        return (UserSession) FacesContext.getCurrentInstance()
                .getExternalContext().getRequestMap().get(AuthFilter.SESSION_ATTR);
    }

    private void addInfo(String m) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, m, null));
    }

    private void addError(String m) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, m, null));
    }

    // Getters & Setters
    public List<Party> getParties() {
        return parties;
    }

    public List<Party> getFiltered() {
        return filtered;
    }

    public void setFiltered(List<Party> v) {
        this.filtered = v;
    }

    public Party getSelected() {
        return selected;
    }

    public void setSelected(Party v) {
        this.selected = v;
    }

    public boolean isEditMode() {
        return editMode;
    }

    public boolean isShowDialog() {
        return showDialog;
    }

    public void setShowDialog(boolean v) {
        this.showDialog = v;
    }

    public String getFilterType() {
        return filterType;
    }

    public void setFilterType(String v) {
        this.filterType = v;
    }

    public String getSearchQ() {
        return searchQ;
    }

    public void setSearchQ(String v) {
        this.searchQ = v;
    }

    public Party getStmtParty() {
        return stmtParty;
    }

    public List<Object[]> getStmtInvoices() {
        return stmtInvoices;
    }

    public BigDecimal getStmtTotalDebit() {
        return stmtTotalDebit;
    }

    public BigDecimal getStmtTotalCredit() {
        return stmtTotalCredit;
    }

    public BigDecimal getStmtBalance() {
        return stmtBalance;
    }

    public boolean isShowStmt() {
        return showStmt;
    }
}

// ══════════════════════════════════════════════════════════════
// PaymentBean — تسجيل المدفوعات والمقبوضات
// ══════════════════════════════════════════════════════════════
@Named("payBean")
@ViewScoped
class PaymentBean implements Serializable {

    // ─── فلاتر ──────────────────────────────────────
    private Long agentId;
    private Long partyId;
    private Long invoiceId;
    private String payType = "receipt"; // receipt | payment
    private LocalDate dateFrom = LocalDate.now().withDayOfMonth(1);
    private LocalDate dateTo = LocalDate.now();

    // ─── نموذج الدفعة ───────────────────────────────
    private BigDecimal amount = BigDecimal.ZERO;
    private String method = "cash";
    private String reference;
    private String notes;
    private Long currencyId;

    // ─── البيانات ────────────────────────────────────
    private List<Object[]> unpaidInvoices;
    private List<Payment> paymentHistory;
    private BigDecimal invoiceBalance = BigDecimal.ZERO;

    // ─── قوائم ──────────────────────────────────────
    private List<Object[]> parties;
    private List<Object[]> currencies;

    @Inject
    private TradeService tradeService;
    @PersistenceContext
    private EntityManager em;

    @PostConstruct
    public void init() {
        UserSession u = (UserSession) FacesContext.getCurrentInstance()
                .getExternalContext().getRequestMap().get(AuthFilter.SESSION_ATTR);
        if (u != null)
            agentId = u.getAgentId();
        loadLookups();
        loadUnpaid();
    }

    // ─── تحميل الفواتير غير المسددة ─────────────────
    @SuppressWarnings("unchecked")
    public void loadUnpaid() {
        try {
            String sql = "SELECT i.invoice_i_id, i.invoice_number, " +
                    "p.parties_c_name, i.invoice_d_date, " +
                    "i.total, i.invoice_f_paid, i.invoice_f_balance, " +
                    "i.invoice_c_type " +
                    "FROM invoices i " +
                    "JOIN parties p ON i.party_id = p.parties_i_id " +
                    "WHERE i.AGENT_I_ID=:a AND i.invoice_f_balance>0 " +
                    "AND i.status='posted' ";
            if (partyId != null)
                sql += "AND i.party_id=:pid ";
            sql += "ORDER BY i.invoice_d_date ASC";

            var q = em.createNativeQuery(sql).setParameter("a", agentId);
            if (partyId != null)
                q.setParameter("pid", partyId);
            unpaidInvoices = q.getResultList();
        } catch (Exception e) {
            unpaidInvoices = List.of();
        }
    }

    // ─── عند اختيار فاتورة ──────────────────────────
    public void onInvoiceSelect() {
        if (invoiceId == null) {
            invoiceBalance = BigDecimal.ZERO;
            return;
        }
        try {
            Object r = em.createNativeQuery(
                    "SELECT invoice_f_balance, party_id, CURRENCY_I_ID " +
                            "FROM invoices WHERE invoice_i_id=:id")
                    .setParameter("id", invoiceId).getSingleResult();
            Object[] row = (Object[]) r;
            invoiceBalance = new BigDecimal(row[0].toString());
            partyId = ((Number) row[1]).longValue();
            currencyId = ((Number) row[2]).longValue();
            amount = invoiceBalance; // تعبئة تلقائية
        } catch (Exception e) {
            invoiceBalance = BigDecimal.ZERO;
        }
    }

    // ─── تسجيل الدفعة ───────────────────────────────
    public void record() {
        if (partyId == null) {
            addError("يجب اختيار العميل / المورد");
            return;
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            addError("المبلغ يجب أن يكون أكبر من صفر");
            return;
        }
        try {
            UserSession u = (UserSession) FacesContext.getCurrentInstance()
                    .getExternalContext().getRequestMap().get(AuthFilter.SESSION_ATTR);
            Long userId = u != null ? u.getUserId() : 0L;

            tradeService.recordPayment(invoiceId, partyId, agentId,
                    amount, method, reference, currencyId, userId);

            addInfo("تم تسجيل الدفعة: " + String.format("%,.2f", amount));
            reset();
            loadUnpaid();
            loadHistory();
        } catch (TradeService.TradeException e) {
            addError(e.getMessage());
        }
    }

    // ─── سجل الدفعات ────────────────────────────────
    public void loadHistory() {
        try {
            paymentHistory = em.createQuery(
                    "SELECT p FROM Payment p WHERE p.agentId=:a " +
                            "AND p.paymentDate BETWEEN :f AND :t ORDER BY p.paymentDate DESC",
                    Payment.class)
                    .setParameter("a", agentId)
                    .setParameter("f", dateFrom)
                    .setParameter("t", dateTo)
                    .getResultList();
        } catch (Exception e) {
            paymentHistory = List.of();
        }
    }

    private void reset() {
        invoiceId = null;
        amount = BigDecimal.ZERO;
        reference = null;
        notes = null;
        invoiceBalance = BigDecimal.ZERO;
    }

    @SuppressWarnings("unchecked")
    private void loadLookups() {
        try {
            parties = em.createNativeQuery(
                    "SELECT parties_i_id, parties_c_name, parties_c_type " +
                            "FROM parties WHERE AGENT_I_ID=:a AND is_active=1 ORDER BY parties_c_name")
                    .setParameter("a", agentId).getResultList();
            currencies = em.createNativeQuery(
                    "SELECT CURRENCY_I_ID,CURRENCY_C_DESC,CURRENCY_C_SYMBOL " +
                            "FROM CURRENCY_MAST ORDER BY CURRENCY_C_DESC")
                    .getResultList();
        } catch (Exception e) {
            parties = currencies = List.of();
        }
    }

    private void addInfo(String m) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, m, null));
    }

    private void addError(String m) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, m, null));
    }

    // Getters & Setters
    public Long getAgentId() {
        return agentId;
    }

    public Long getPartyId() {
        return partyId;
    }

    public void setPartyId(Long v) {
        this.partyId = v;
    }

    public Long getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(Long v) {
        this.invoiceId = v;
    }

    public String getPayType() {
        return payType;
    }

    public void setPayType(String v) {
        this.payType = v;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public void setDateFrom(LocalDate v) {
        this.dateFrom = v;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public void setDateTo(LocalDate v) {
        this.dateTo = v;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal v) {
        this.amount = v;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String v) {
        this.method = v;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String v) {
        this.reference = v;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String v) {
        this.notes = v;
    }

    public Long getCurrencyId() {
        return currencyId;
    }

    public void setCurrencyId(Long v) {
        this.currencyId = v;
    }

    public List<Object[]> getUnpaidInvoices() {
        return unpaidInvoices;
    }

    public List<Payment> getPaymentHistory() {
        return paymentHistory;
    }

    public BigDecimal getInvoiceBalance() {
        return invoiceBalance;
    }

    public List<Object[]> getParties() {
        return parties;
    }

    public List<Object[]> getCurrencies() {
        return currencies;
    }
}
