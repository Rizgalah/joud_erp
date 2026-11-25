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
public class PartiesBean implements Serializable {

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
