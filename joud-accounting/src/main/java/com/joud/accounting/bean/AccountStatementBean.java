package com.joud.accounting.bean;

import com.joud.accounting.entity.DayLedger;
import com.joud.core.report.engine.ReportDownloadUtil;
import com.joud.core.report.engine.ReportEngineService;
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

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * AccountStatementBean — كشف الحساب
 *
 * يعرض حركات حساب معين خلال فترة مع:
 *   - رصيد الافتتاح
 *   - الحركات مرتبة زمنياً مع الرصيد التراكمي
 *   - رصيد الإغلاق
 *   - تصفية حسب العملة أو النوع
 */
@Named("stmtBean")
@ViewScoped
public class AccountStatementBean implements Serializable {

    // ─── فلاتر البحث ────────────────────────────────
    private String    accountType;
    private Long      agentId;
    private LocalDate dateFrom  = LocalDate.now().withDayOfMonth(1);
    private LocalDate dateTo    = LocalDate.now();
    private Long      currencyId;
    private String    filterDrCr; // null=الكل, DR, CR

    // ─── نتائج الكشف ────────────────────────────────
    private List<StatementLine> statementLines = new ArrayList<>();
    private BigDecimal openingBalance  = BigDecimal.ZERO;
    private String     openingDrCr     = "D";
    private BigDecimal totalDebit      = BigDecimal.ZERO;
    private BigDecimal totalCredit     = BigDecimal.ZERO;
    private BigDecimal closingBalance  = BigDecimal.ZERO;
    private String     closingDrCr     = "D";
    private String     accountName;
    private boolean    searched        = false;

    // ─── قوائم الاختيار ─────────────────────────────
    private List<Object[]> accounts;
    private List<Object[]> agents;
    private List<Object[]> currencies;

    @Inject
    private ReportEngineService reportEngine;

    @PersistenceContext
    private EntityManager em;

    @PostConstruct
    public void init() {
        loadLookups();
        UserSession user = getCurrentUser();
        if (user != null) agentId = user.getAgentId();
    }

    // ════════════════════════════════════════════════
    // البحث والاستعلام
    // ════════════════════════════════════════════════

    public void search() {
        if (accountType == null || accountType.isBlank()) {
            addError("يجب اختيار الحساب");
            return;
        }

        statementLines.clear();
        totalDebit = totalCredit = BigDecimal.ZERO;

        // ─── رصيد الافتتاح من GENLDG_MAST ───────────
        loadOpeningBalance();

        // ─── الحركات خلال الفترة من DAY_LEDG ─────────
        List<DayLedger> lines = fetchLines();

        // ─── بناء الكشف مع الرصيد التراكمي ──────────
        BigDecimal running = openingDrCr.equals("D")
            ? openingBalance : openingBalance.negate();

        for (DayLedger dl : lines) {
            BigDecimal amt = dl.isDebit()
                ? dl.getAmount()
                : dl.getAmount().negate();
            running = running.add(amt);

            totalDebit  = totalDebit .add(dl.isDebit()  ? dl.getAmount() : BigDecimal.ZERO);
            totalCredit = totalCredit.add(dl.isCredit() ? dl.getAmount() : BigDecimal.ZERO);

            statementLines.add(new StatementLine(
                dl.getDate(),
                dl.getJournalEntryId() != null ? "JV-" + dl.getJournalEntryId() : "—",
                dl.getTransactionType(),
                dl.getNarration(),
                dl.isDebit()  ? dl.getAmount() : null,
                dl.isCredit() ? dl.getAmount() : null,
                running.abs(),
                running.compareTo(BigDecimal.ZERO) >= 0 ? "D" : "C"
            ));
        }

        // ─── رصيد الإغلاق ─────────────────────────────
        closingBalance = running.abs();
        closingDrCr    = running.compareTo(BigDecimal.ZERO) >= 0 ? "D" : "C";
        loadAccountName();
        searched = true;
    }

    public void reset() {
        accountType = null;
        statementLines.clear();
        searched = false;
        openingBalance = totalDebit = totalCredit = closingBalance = BigDecimal.ZERO;
    }

    // ════════════════════════════════════════════════
    // Private Helpers
    // ════════════════════════════════════════════════

    private void loadOpeningBalance() {
        try {
            Object[] row = (Object[]) em.createNativeQuery(
                "SELECT GEN_F_OP_BAL, GEN_C_OP_DRCR FROM GENLDG_MAST " +
                "WHERE ACT_C_TYPE=:act AND AGENT_I_ID=:agent " +
                "  AND GEN_I_YEAR=:y AND GEN_I_MONTH=:m " +
                (currencyId != null ? "AND CURRENCY_I_ID=:cur " : "") +
                "ORDER BY GEN_I_YEAR DESC, GEN_I_MONTH DESC")
                .setParameter("act",   accountType)
                .setParameter("agent", agentId)
                .setParameter("y",     dateFrom.getYear())
                .setParameter("m",     dateFrom.getMonthValue())
                .setMaxResults(1)
                .getSingleResult();

            openingBalance = new BigDecimal(row[0].toString());
            openingDrCr    = row[1].toString();
        } catch (Exception e) {
            openingBalance = BigDecimal.ZERO;
            openingDrCr    = "D";
        }
    }

    private List<DayLedger> fetchLines() {
        StringBuilder jpql = new StringBuilder(
            "SELECT d FROM DayLedger d " +
            "WHERE d.accountType = :act " +
            "  AND d.agentId     = :agent " +
            "  AND d.date BETWEEN :from AND :to " +
            "  AND d.status     <> 'V' "
        );
        if (currencyId != null) jpql.append("AND d.currencyId = :cur ");
        if (filterDrCr != null) jpql.append("AND d.drCr = :drcr ");
        jpql.append("ORDER BY d.date ASC, d.id ASC");

        var query = em.createQuery(jpql.toString(), DayLedger.class)
            .setParameter("act",   accountType)
            .setParameter("agent", agentId)
            .setParameter("from",  dateFrom)
            .setParameter("to",    dateTo);

        if (currencyId  != null) query.setParameter("cur",  currencyId);
        if (filterDrCr  != null) query.setParameter("drcr", filterDrCr);
        return query.getResultList();
    }

    private void loadAccountName() {
        try {
            accountName = (String) em.createNativeQuery(
                "SELECT ACT_C_NAME FROM ACCOUNT_MAST WHERE ACT_C_TYPE=:t")
                .setParameter("t", accountType).getSingleResult();
        } catch (Exception e) { accountName = accountType; }
    }

    @SuppressWarnings("unchecked")
    private void loadLookups() {
        try {
            accounts   = em.createNativeQuery(
                "SELECT ACT_C_TYPE,ACT_C_NAME FROM ACCOUNT_MAST " +
                "WHERE ACT_C_ACTSTS='A' ORDER BY ACT_C_TYPE").getResultList();
            agents     = em.createNativeQuery(
                "SELECT AGENT_I_ID,AGENT_C_NAME FROM AGENT_MAST " +
                "ORDER BY AGENT_C_NAME").getResultList();
            currencies = em.createNativeQuery(
                "SELECT CURRENCY_I_ID,CURRENCY_C_DESC,CURRENCY_C_SYMBOL " +
                "FROM CURRENCY_MAST ORDER BY CURRENCY_C_DESC").getResultList();
        } catch (Exception e) {
            accounts = agents = currencies = List.of();
        }
    }

    private UserSession getCurrentUser() {
        return (UserSession) FacesContext.getCurrentInstance()
            .getExternalContext().getRequestMap().get(AuthFilter.SESSION_ATTR);
    }

    private void addError(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
            new FacesMessage(FacesMessage.SEVERITY_ERROR, msg, null));
    }

    // ─── View Helpers ───────────────────────────────
    public String formatBalance(BigDecimal bal, String drCr) {
        if (bal == null || bal.compareTo(BigDecimal.ZERO) == 0) return "—";
        return String.format("%,.2f %s", bal, "D".equals(drCr) ? "م" : "د");
    }

    public String getNetDrCr() {
        BigDecimal net = totalDebit.subtract(totalCredit);
        return net.compareTo(BigDecimal.ZERO) >= 0 ? "مدين" : "دائن";
    }

    // ════════════════════════════════════════════════
    // التصدير — يُصدِّر بالضبط ما هو معروض على الشاشة
    // ════════════════════════════════════════════════

    public void exportPdf() {
        if (!searched || statementLines.isEmpty()) return;
        String[] headers = {"التاريخ", "المرجع", "النوع", "البيان", "مدين", "دائن", "الرصيد"};
        int[]    widths  = {12, 14, 10, 28, 12, 12, 12};
        byte[] bytes = reportEngine.exportPdf(
            "كشف حساب: " + (accountName != null ? accountName : accountType),
            "من " + dateFrom + " إلى " + dateTo,
            headers, widths, buildExportRows(), buildTotalsRow());
        ReportDownloadUtil.stream(bytes,
            ReportDownloadUtil.buildFilename("كشف_حساب_" + accountType, "pdf"),
            ReportDownloadUtil.PDF_MIME);
    }

    public void exportExcel() {
        if (!searched || statementLines.isEmpty()) return;
        String[] headers = {"التاريخ", "المرجع", "النوع", "البيان", "مدين", "دائن", "الرصيد"};
        byte[] bytes = reportEngine.exportExcel(
            "كشف حساب: " + (accountName != null ? accountName : accountType),
            "من " + dateFrom + " إلى " + dateTo,
            headers, buildExportRows(), buildTotalsRow());
        ReportDownloadUtil.stream(bytes,
            ReportDownloadUtil.buildFilename("كشف_حساب_" + accountType, "xlsx"),
            ReportDownloadUtil.XLSX_MIME);
    }

    private List<Object[]> buildExportRows() {
        List<Object[]> rows = new ArrayList<>();
        for (StatementLine l : statementLines) {
            rows.add(new Object[]{
                l.date(), l.reference(), l.type(), l.narration(),
                l.debit(), l.credit(), l.balance()
            });
        }
        return rows;
    }

    private Object[] buildTotalsRow() {
        return new Object[]{"الإجمالي", null, null, null, totalDebit, totalCredit, closingBalance};
    }

    // ─── Inner Record ───────────────────────────────
    public record StatementLine(
        LocalDate   date,
        String      reference,
        String      type,
        String      narration,
        BigDecimal  debit,
        BigDecimal  credit,
        BigDecimal  balance,
        String      balDrCr
    ) {}

    // ─── Getters & Setters ─────────────────────────
    public String getAccountType()               { return accountType; }
    public void setAccountType(String v)         { this.accountType = v; }
    public Long getAgentId()                     { return agentId; }
    public void setAgentId(Long v)               { this.agentId = v; }
    public LocalDate getDateFrom()               { return dateFrom; }
    public void setDateFrom(LocalDate v)         { this.dateFrom = v; }
    public LocalDate getDateTo()                 { return dateTo; }
    public void setDateTo(LocalDate v)           { this.dateTo = v; }
    public Long getCurrencyId()                  { return currencyId; }
    public void setCurrencyId(Long v)            { this.currencyId = v; }
    public String getFilterDrCr()                { return filterDrCr; }
    public void setFilterDrCr(String v)          { this.filterDrCr = v; }
    public List<StatementLine> getStatementLines(){ return statementLines; }
    public BigDecimal getOpeningBalance()        { return openingBalance; }
    public String getOpeningDrCr()               { return openingDrCr; }
    public BigDecimal getTotalDebit()            { return totalDebit; }
    public BigDecimal getTotalCredit()           { return totalCredit; }
    public BigDecimal getClosingBalance()        { return closingBalance; }
    public String getClosingDrCr()               { return closingDrCr; }
    public String getAccountName()               { return accountName; }
    public boolean isSearched()                  { return searched; }
    public List<Object[]> getAccounts()          { return accounts; }
    public List<Object[]> getAgents()            { return agents; }
    public List<Object[]> getCurrencies()        { return currencies; }
}
