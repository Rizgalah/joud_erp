package com.joud.accounting.bean;

import com.joud.accounting.entity.AccountingPeriod;
import com.joud.accounting.service.GeneralLedgerService;
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
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * PeriodManagementBean — إدارة الفترات المحاسبية
 *
 * يعرض الفترات على شكل تقويم سنوي (12 شهر).
 * يتيح فتح، إغلاق، وقفل الفترات.
 * يمنع إغلاق فترة بها قيود معلقة.
 */
@Named("periodBean")
@ViewScoped
public class PeriodManagementBean implements Serializable {

    private int  selectedYear = LocalDate.now().getYear();
    private Long agentId;

    // الفترات الـ 12 للسنة المختارة
    private List<PeriodCard> periodCards = new ArrayList<>();

    private String           confirmNote;

    // إحصاءات
    private long openCount;
    private long closedCount;
    private long lockedCount;

    private List<Object[]> agents;

    @Inject  private GeneralLedgerService glService;
    @PersistenceContext private EntityManager em;

    @PostConstruct
    public void init() {
        loadAgents();
        UserSession user = getCurrentUser();
        if (user != null) agentId = user.getAgentId();
        loadPeriods();
    }

    // ════════════════════════════════════════════════
    // تحميل الفترات
    // ════════════════════════════════════════════════

    public void loadPeriods() {
        periodCards.clear();
        openCount = closedCount = lockedCount = 0;

        for (int m = 1; m <= 12; m++) {
            AccountingPeriod period = glService
                .findPeriod(agentId, selectedYear, m)
                .orElse(null);

            long trnCount = countTransactions(m);
            long pendingCount = countPending(m);

            PeriodCard card = new PeriodCard(m, period, trnCount, pendingCount);
            periodCards.add(card);

            if (period == null || period.isOpen())   openCount++;
            else if (period.isClosed())              closedCount++;
            else if (period.isLocked())              lockedCount++;
        }
    }

    // ════════════════════════════════════════════════
    // إجراءات الفترة
    // ════════════════════════════════════════════════

    /** إنشاء فترة إذا لم تكن موجودة */
    @Transactional
    public void createPeriod(int month) {
        if (glService.findPeriod(agentId, selectedYear, month).isPresent()) {
            addWarn("الفترة موجودة مسبقاً");
            return;
        }
        AccountingPeriod p = new AccountingPeriod();
        p.setId(generateId());
        p.setAgentId(agentId);
        p.setYear(selectedYear);
        p.setMonth(month);
        p.setDateFrom(LocalDate.of(selectedYear, month, 1));
        p.setDateTo(LocalDate.of(selectedYear, month, 1).withDayOfMonth(
            LocalDate.of(selectedYear, month, 1).lengthOfMonth()));
        p.setStatus(AccountingPeriod.PeriodStatus.O);
        em.persist(p);
        addInfo("تم إنشاء الفترة: " + getMonthName(month) + " " + selectedYear);
        loadPeriods();
    }

    /** إغلاق فترة */
    public void closePeriod(int month) {
        try {
            // تحقق: لا توجد قيود معلقة
            long pending = countPending(month);
            if (pending > 0) {
                addError("لا يمكن إغلاق الفترة — يوجد " + pending + " قيد معلق للاعتماد");
                return;
            }
            UserSession user = getCurrentUser();
            glService.closePeriod(agentId, selectedYear, month,
                user != null ? user.getUserId() : 0L);
            addInfo("تم إغلاق فترة: " + getMonthName(month) + " " + selectedYear);
            loadPeriods();
        } catch (GeneralLedgerService.AccountingException e) {
            addError(e.getMessage());
        }
    }

    /** إعادة فتح فترة مغلقة (تتطلب صلاحية خاصة) */
    @Transactional
    public void reopenPeriod(int month) {
        AccountingPeriod p = glService.findPeriod(agentId, selectedYear, month).orElse(null);
        if (p == null || !p.isClosed()) { addWarn("الفترة ليست مغلقة"); return; }
        p.setStatus(AccountingPeriod.PeriodStatus.O);
        p.setDateClosed(null);
        em.merge(p);
        addInfo("تم إعادة فتح الفترة: " + getMonthName(month));
        loadPeriods();
    }

    /** قفل فترة نهائياً (بعد الإغلاق السنوي) */
    @Transactional
    public void lockPeriod(int month) {
        AccountingPeriod p = glService.findPeriod(agentId, selectedYear, month).orElse(null);
        if (p == null || !p.isClosed()) { addWarn("يجب إغلاق الفترة أولاً"); return; }
        p.setStatus(AccountingPeriod.PeriodStatus.L);
        em.merge(p);
        addInfo("تم قفل الفترة نهائياً: " + getMonthName(month));
        loadPeriods();
    }

    /** إغلاق السنة كاملة دفعة واحدة */
    public void closeYear() {
        int closed = 0;
        for (int m = 1; m <= 12; m++) {
            AccountingPeriod p = glService.findPeriod(agentId, selectedYear, m).orElse(null);
            if (p != null && p.isOpen() && countPending(m) == 0) {
                try {
                    UserSession user = getCurrentUser();
                    glService.closePeriod(agentId, selectedYear, m,
                        user != null ? user.getUserId() : 0L);
                    closed++;
                } catch (Exception ignored) {}
            }
        }
        addInfo("تم إغلاق " + closed + " فترة من أصل 12");
        loadPeriods();
    }

    // ════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════

    private long countTransactions(int month) {
        try {
            return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM DAY_LEDG " +
                "WHERE AGENT_I_ID=:a " +
                "AND EXTRACT(YEAR FROM TRN_D_DATE)=:y " +
                "AND EXTRACT(MONTH FROM TRN_D_DATE)=:m " +
                "AND TRN_C_STS<>'V'")
                .setParameter("a", agentId)
                .setParameter("y", selectedYear)
                .setParameter("m", month)
                .getSingleResult()).longValue();
        } catch (Exception e) { return 0; }
    }

    private long countPending(int month) {
        try {
            return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM JOURNAL_ENTRY " +
                "WHERE AGENT_I_ID=:a " +
                "AND EXTRACT(YEAR FROM JV_D_DATE)=:y " +
                "AND EXTRACT(MONTH FROM JV_D_DATE)=:m " +
                "AND JV_C_STS='P'")
                .setParameter("a", agentId)
                .setParameter("y", selectedYear)
                .setParameter("m", month)
                .getSingleResult()).longValue();
        } catch (Exception e) { return 0; }
    }

    @SuppressWarnings("unchecked")
    private void loadAgents() {
        try {
            agents = em.createNativeQuery(
                "SELECT AGENT_I_ID,AGENT_C_NAME FROM AGENT_MAST ORDER BY AGENT_C_NAME")
                .getResultList();
        } catch (Exception e) { agents = List.of(); }
    }

    private Long generateId() {
        try {
            return ((Number) em.createNativeQuery(
                "SELECT ID_I_GEN FROM KEY_GENERATION " +
                "WHERE TABLE_C_NAME='ACCOUNTING_PERIOD'")
                .getSingleResult()).longValue();
        } catch (Exception e) { return System.currentTimeMillis(); }
    }

    private UserSession getCurrentUser() {
        return (UserSession) FacesContext.getCurrentInstance()
            .getExternalContext().getRequestMap().get(AuthFilter.SESSION_ATTR);
    }

    public String getMonthName(int m) {
        return LocalDate.of(2000, m, 1)
            .getMonth().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("ar"));
    }

    private void addInfo(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(FacesMessage.SEVERITY_INFO, msg, null));
    }
    private void addWarn(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(FacesMessage.SEVERITY_WARN, msg, null));
    }
    private void addError(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(FacesMessage.SEVERITY_ERROR, msg, null));
    }

    // ─── Inner Record ───────────────────────────────
    public record PeriodCard(
        int             month,
        AccountingPeriod period,
        long            trnCount,
        long            pendingCount
    ) {
        public boolean exists()   { return period != null; }
        public boolean isOpen()   { return period == null || period.isOpen(); }
        public boolean isClosed() { return period != null && period.isClosed(); }
        public boolean isLocked() { return period != null && period.isLocked(); }
        public boolean hasPending(){ return pendingCount > 0; }
        public String statusClass() {
            if (isLocked()) return "locked";
            if (isClosed()) return "closed";
            return "open";
        }
        public String statusLabel() {
            if (isLocked()) return "مقفلة";
            if (isClosed()) return "مغلقة";
            return "مفتوحة";
        }
        public String statusIcon() {
            if (isLocked()) return "pi-lock";
            if (isClosed()) return "pi-lock-open";
            return "pi-unlock";
        }
    }

    // ─── Getters & Setters ─────────────────────────
    public int getSelectedYear()              { return selectedYear; }
    public void setSelectedYear(int v)        { this.selectedYear = v; }
    public Long getAgentId()                  { return agentId; }
    public void setAgentId(Long v)            { this.agentId = v; }
    public List<PeriodCard> getPeriodCards()  { return periodCards; }
    public long getOpenCount()                { return openCount; }
    public long getClosedCount()              { return closedCount; }
    public long getLockedCount()              { return lockedCount; }
    public List<Object[]> getAgents()         { return agents; }
    public String getConfirmNote()            { return confirmNote; }
    public void setConfirmNote(String v)      { this.confirmNote = v; }
}
