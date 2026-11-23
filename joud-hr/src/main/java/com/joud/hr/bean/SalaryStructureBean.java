package com.joud.hr.bean;

import com.joud.hr.entity.SalaryStruct;
import com.joud.hr.entity.SalaryComponent;
import com.joud.core.security.filter.AuthFilter;
import com.joud.core.security.session.UserSession;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * SalaryStructureBean — هيكل راتب الموظف
 *
 * يعرض هيكل راتب موظف مختار (مكوّناته السارية حالياً + التاريخية)،
 * يتيح إضافة مكوّن جديد للهيكل (بإنهاء المكوّن القديم تلقائياً إن
 * تكرَّر نفس المكوّن)، ويحسب صافي الراتب الحالي من مجموع المكوّنات.
 */
@Named("structBean")
@ViewScoped
public class SalaryStructureBean implements Serializable {

    // ─── الموظف المختار ─────────────────────────────
    private Long employeeId;
    private List<SalaryStruct> currentStructure; // المكوّنات السارية الآن
    private List<SalaryStruct> historyStructure; // كل السجل (شامل المنتهي)

    // ─── إجماليات الراتب الحالي ─────────────────────
    private BigDecimal totalEarnings = BigDecimal.ZERO;
    private BigDecimal totalDeductions = BigDecimal.ZERO;
    private BigDecimal netSalary = BigDecimal.ZERO;

    // ─── نموذج إضافة مكوّن ──────────────────────────
    private Long newComponentId;
    private BigDecimal newAmount;
    private Long newCurrencyId;
    private LocalDate newDateFrom = LocalDate.now();
    private boolean showAddDialog = false;

    // ─── قوائم الاختيار ─────────────────────────────
    private List<Object[]> employees;
    private List<SalaryComponent> components;
    private List<Object[]> currencies;

    private Long agentId;

    @PersistenceContext
    private EntityManager em;

    @PostConstruct
    public void init() {
        UserSession user = getCurrentUser();
        if (user != null)
            agentId = user.getAgentId();
        loadLookups();
    }

    // ════════════════════════════════════════════════
    // اختيار موظف وتحميل هيكله
    // ════════════════════════════════════════════════

    public void onEmployeeChange() {
        loadStructure();
    }

    private void loadStructure() {
        if (employeeId == null) {
            currentStructure = List.of();
            historyStructure = List.of();
            resetTotals();
            return;
        }

        try {
            historyStructure = em.createQuery(
                    "SELECT s FROM SalaryStruct s WHERE s.employeeId=:e " +
                            "ORDER BY s.dateFrom DESC",
                    SalaryStruct.class)
                    .setParameter("e", employeeId).getResultList();

            currentStructure = historyStructure.stream()
                    .filter(SalaryStruct::isCurrent).toList();

            // تعبئة أسماء المكوّنات للعرض + حساب الإجماليات
            totalEarnings = BigDecimal.ZERO;
            totalDeductions = BigDecimal.ZERO;
            for (SalaryStruct s : historyStructure) {
                enrichComponentInfo(s);
                if (s.isCurrent()) {
                    if ("E".equals(s.getComponentType()))
                        totalEarnings = totalEarnings.add(s.getAmount());
                    else if ("D".equals(s.getComponentType()))
                        totalDeductions = totalDeductions.add(s.getAmount());
                }
            }
            netSalary = totalEarnings.subtract(totalDeductions);
        } catch (Exception e) {
            currentStructure = List.of();
            historyStructure = List.of();
            resetTotals();
        }
    }

    private void enrichComponentInfo(SalaryStruct s) {
        components.stream()
                .filter(c -> c.getId().equals(s.getComponentId()))
                .findFirst()
                .ifPresent(c -> {
                    s.setComponentName(c.getName());
                    s.setComponentType(c.getType().toString());
                });
        currencies.stream()
                .filter(c -> ((Number) c[0]).longValue() == s.getCurrencyId())
                .findFirst()
                .ifPresent(c -> s.setCurrencySymbol(c[2] != null ? c[2].toString() : ""));
    }

    private void resetTotals() {
        totalEarnings = BigDecimal.ZERO;
        totalDeductions = BigDecimal.ZERO;
        netSalary = BigDecimal.ZERO;
    }

    // ════════════════════════════════════════════════
    // إضافة / تحديث مكوّن في الهيكل
    // ════════════════════════════════════════════════

    public void prepareAdd() {
        newComponentId = null;
        newAmount = null;
        newCurrencyId = null;
        newDateFrom = LocalDate.now();
        showAddDialog = true;
    }

    @Transactional
    public void addComponent() {
        if (employeeId == null) {
            addError("يجب اختيار الموظف أولاً");
            return;
        }
        if (newComponentId == null) {
            addError("يجب اختيار المكوّن");
            return;
        }
        if (newAmount == null || newAmount.compareTo(BigDecimal.ZERO) < 0) {
            addError("يجب إدخال قيمة صحيحة");
            return;
        }

        try {
            // أنهِ أي مكوّن سارٍ مطابق لنفس النوع (تاريخ نهاية = يوم قبل البداية الجديدة)
            List<SalaryStruct> existing = em.createQuery(
                    "SELECT s FROM SalaryStruct s WHERE s.employeeId=:e AND s.componentId=:c " +
                            "AND s.status='A' AND s.dateTo IS NULL",
                    SalaryStruct.class)
                    .setParameter("e", employeeId).setParameter("c", newComponentId)
                    .getResultList();

            for (SalaryStruct old : existing) {
                old.setDateTo(newDateFrom.minusDays(1));
                em.merge(old);
            }

            SalaryStruct s = new SalaryStruct();
            s.setId(generateId());
            s.setEmployeeId(employeeId);
            s.setAgentId(agentId);
            s.setComponentId(newComponentId);
            s.setCurrencyId(newCurrencyId);
            s.setAmount(newAmount);
            s.setDateFrom(newDateFrom);
            s.setStatus("A");
            s.setUserId(getCurrentUserId());
            em.persist(s);

            addInfo("تمت إضافة المكوّن لهيكل الراتب");
            showAddDialog = false;
            loadStructure();
        } catch (Exception e) {
            addError("خطأ في الحفظ: " + e.getMessage());
        }
    }

    @Transactional
    public void endComponent(SalaryStruct s) {
        try {
            SalaryStruct e = em.find(SalaryStruct.class, s.getId());
            if (e != null) {
                e.setDateTo(LocalDate.now());
                em.merge(e);
                addInfo("تم إنهاء سريان المكوّن");
                loadStructure();
            }
        } catch (Exception ex) {
            addError("خطأ: " + ex.getMessage());
        }
    }

    public void closeDialog() {
        showAddDialog = false;
    }

    // ════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private void loadLookups() {
        try {
            employees = em.createNativeQuery(
                    "SELECT EMP_i_id, EMP_C_NAME FROM EMPLOYEE_MAST " +
                            "WHERE AGENT_I_ID=:a AND EMP_C_STATUS='A' ORDER BY EMP_C_NAME")
                    .setParameter("a", agentId).getResultList();
        } catch (Exception e) {
            employees = List.of();
        }

        try {
            components = em.createQuery(
                    "SELECT c FROM SalaryComponent c WHERE c.agentId=:a AND c.status='A' " +
                            "ORDER BY c.displayOrder, c.name",
                    SalaryComponent.class)
                    .setParameter("a", agentId).getResultList();
        } catch (Exception e) {
            components = List.of();
        }

        try {
            currencies = em.createNativeQuery(
                    "SELECT CURRENCY_I_ID, CURRENCY_C_DESC, CURRENCY_C_SYMBOL " +
                            "FROM CURRENCY_MAST ORDER BY CURRENCY_C_DESC")
                    .getResultList();
        } catch (Exception e) {
            currencies = List.of();
        }
    }

    public String getComponentTypeLabel(String type) {
        if (type == null)
            return "—";
        return switch (type) {
            case "E" -> "إضافة";
            case "D" -> "خصم";
            default -> "معلومة";
        };
    }

    private Long generateId() {
        try {
            return ((Number) em.createNativeQuery(
                    "SELECT ID_I_GEN FROM KEY_GENERATION WHERE TABLE_C_NAME='SALARY_STRUCT'")
                    .getSingleResult()).longValue();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private UserSession getCurrentUser() {
        return (UserSession) FacesContext.getCurrentInstance()
                .getExternalContext().getRequestMap().get(AuthFilter.SESSION_ATTR);
    }

    private Long getCurrentUserId() {
        UserSession u = getCurrentUser();
        return u != null ? u.getUserId() : null;
    }

    private void addInfo(String m) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO, m, null));
    }

    private void addError(String m) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, m, null));
    }

    // ─── Getters & Setters ─────────────────────────
    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long v) {
        this.employeeId = v;
    }

    public List<SalaryStruct> getCurrentStructure() {
        return currentStructure;
    }

    public List<SalaryStruct> getHistoryStructure() {
        return historyStructure;
    }

    public BigDecimal getTotalEarnings() {
        return totalEarnings;
    }

    public BigDecimal getTotalDeductions() {
        return totalDeductions;
    }

    public BigDecimal getNetSalary() {
        return netSalary;
    }

    public Long getNewComponentId() {
        return newComponentId;
    }

    public void setNewComponentId(Long v) {
        this.newComponentId = v;
    }

    public BigDecimal getNewAmount() {
        return newAmount;
    }

    public void setNewAmount(BigDecimal v) {
        this.newAmount = v;
    }

    public Long getNewCurrencyId() {
        return newCurrencyId;
    }

    public void setNewCurrencyId(Long v) {
        this.newCurrencyId = v;
    }

    public LocalDate getNewDateFrom() {
        return newDateFrom;
    }

    public void setNewDateFrom(LocalDate v) {
        this.newDateFrom = v;
    }

    public boolean isShowAddDialog() {
        return showAddDialog;
    }

    public List<Object[]> getEmployees() {
        return employees;
    }

    public List<SalaryComponent> getComponents() {
        return components;
    }

    public List<Object[]> getCurrencies() {
        return currencies;
    }
}
