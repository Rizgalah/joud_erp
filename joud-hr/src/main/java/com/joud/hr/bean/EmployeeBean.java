package com.joud.hr.bean;

import com.joud.hr.entity.Employee;
import com.joud.hr.service.PayrollService;
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
import java.util.List;

/**
 * EmployeeBean — قائمة وإدارة الموظفين
 */
@Named("empBean")
@ViewScoped
public class EmployeeBean implements Serializable {

    // ─── قائمة الموظفين ─────────────────────────────
    private List<Employee> employees;
    private List<Employee> filteredEmployees;
    private String searchQuery;
    private String filterStatus = "A";
    private Long filterDeptId;

    // ─── النموذج ────────────────────────────────────
    private Employee selected = new Employee();
    private boolean editMode = false;
    private boolean showDialog = false;

    // ─── قوائم الاختيار ─────────────────────────────
    private List<Object[]> departments;
    private List<Object[]> designations;
    private List<Object[]> currencies;
    private List<Object[]> managers;

    // ─── إحصاءات ────────────────────────────────────
    private long totalActive;
    private long expiringDocs;

    private Long agentId;

    @PersistenceContext
    private EntityManager em;
    @Inject
    private PayrollService payrollService;

    @PostConstruct
    public void init() {
        UserSession user = getCurrentUser();
        if (user != null)
            agentId = user.getAgentId();
        loadLookups();
        loadEmployees();
        loadStats();
    }

    // ════════════════════════════════════════════════
    // تحميل البيانات
    // ════════════════════════════════════════════════

    public void loadEmployees() {
        try {
            if (searchQuery != null && !searchQuery.isBlank()) {
                employees = em.createNamedQuery("Employee.search", Employee.class)
                        .setParameter("agentId", agentId)
                        .setParameter("q", searchQuery)
                        .getResultList();
            } else if (filterDeptId != null) {
                employees = em.createNamedQuery("Employee.findByDept", Employee.class)
                        .setParameter("deptId", filterDeptId)
                        .getResultList();
            } else {
                employees = em.createNamedQuery("Employee.findActive", Employee.class)
                        .setParameter("agentId", agentId)
                        .getResultList();
            }
        } catch (Exception e) {
            employees = List.of();
            addError("خطأ في تحميل الموظفين: " + e.getMessage());
        }
    }

    private void loadStats() {
        totalActive = payrollService.countActive(agentId);
        expiringDocs = payrollService.getExpiringDocuments(agentId, 30).size();
    }

    // ════════════════════════════════════════════════
    // إجراءات CRUD
    // ════════════════════════════════════════════════

    public void prepareNew() {
        selected = new Employee();
        selected.setAgentId(agentId);
        selected.setDateOfJoining(LocalDate.now());
        selected.setStatus("A");
        editMode = false;
        showDialog = true;
    }

    public void prepareEdit(Employee emp) {
        selected = em.find(Employee.class, emp.getId());
        editMode = true;
        showDialog = true;
    }

    @Transactional
    public void save() {
        try {
            if (editMode) {
                em.merge(selected);
                addInfo("تم تحديث بيانات الموظف: " + selected.getName());
            } else {
                selected.setId(generateId());
                selected.setSequenceNo(nextSequenceNo());
                em.persist(selected);
                addInfo("تم إضافة الموظف: " + selected.getName());
            }
            showDialog = false;
            loadEmployees();
            loadStats();
        } catch (Exception e) {
            addError("خطأ في الحفظ: " + e.getMessage());
        }
    }

    @Transactional
    public void deactivate(Employee emp) {
        Employee e = em.find(Employee.class, emp.getId());
        if (e != null) {
            e.setStatus("I");
            em.merge(e);
            addInfo("تم إيقاف الموظف: " + e.getName());
            loadEmployees();
            loadStats();
        }
    }

    public void search() {
        loadEmployees();
    }

    public void resetSearch() {
        searchQuery = null;
        filterDeptId = null;
        filterStatus = "A";
        loadEmployees();
    }

    // ════════════════════════════════════════════════
    // Lookups
    // ════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private void loadLookups() {
        try {
            departments = em.createNativeQuery(
                    "SELECT DEPT_I_ID,DEPT_C_NAME FROM DEPARTMENT_MAST " +
                            "WHERE AGENT_I_ID=:a AND DEPT_C_STATUS='A' ORDER BY DEPT_C_NAME")
                    .setParameter("a", agentId).getResultList();
            designations = em.createNativeQuery(
                    "SELECT DESIG_I_ID,DESIG_C_NAME FROM DESIGNATION_MAST " +
                            "WHERE AGENT_I_ID=:a AND DESIG_C_STATUS='A' ORDER BY DESIG_C_NAME")
                    .setParameter("a", agentId).getResultList();
            currencies = em.createNativeQuery(
                    "SELECT CURRENCY_I_ID,CURRENCY_C_DESC,CURRENCY_C_SYMBOL FROM CURRENCY_MAST " +
                            "ORDER BY CURRENCY_C_DESC")
                    .getResultList();
            managers = em.createNativeQuery(
                    "SELECT EMP_i_id,EMP_C_NAME FROM EMPLOYEE_MAST " +
                            "WHERE AGENT_I_ID=:a AND EMP_C_STATUS='A' ORDER BY EMP_C_NAME")
                    .setParameter("a", agentId).getResultList();
        } catch (Exception e) {
            departments = designations = currencies = managers = List.of();
        }
    }

    // ════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════

    private Long generateId() {
        try {
            return ((Number) em.createNativeQuery(
                    "SELECT ID_I_GEN FROM KEY_GENERATION WHERE TABLE_C_NAME='EMPLOYEE_MAST'")
                    .getSingleResult()).longValue();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private Integer nextSequenceNo() {
        try {
            Object r = em.createNativeQuery(
                    "SELECT NVL(MAX(EMP_I_SEQNO),0)+1 FROM EMPLOYEE_MAST WHERE AGENT_I_ID=:a")
                    .setParameter("a", agentId).getSingleResult();
            return ((Number) r).intValue();
        } catch (Exception e) {
            return 1;
        }
    }

    public String getDeptName(Long deptId) {
        if (deptId == null || departments == null)
            return "—";
        return departments.stream().filter(d -> deptId.equals(((Number) d[0]).longValue()))
                .map(d -> d[1].toString()).findFirst().orElse("—");
    }

    public String getDesigName(Long desigId) {
        if (desigId == null || designations == null)
            return "—";
        return designations.stream().filter(d -> desigId.equals(((Number) d[0]).longValue()))
                .map(d -> d[1].toString()).findFirst().orElse("—");
    }

    public boolean isDocExpiring(Employee emp) {
        LocalDate limit = LocalDate.now().plusDays(30);
        return (emp.getIdExpiry() != null && emp.getIdExpiry().isBefore(limit))
                || (emp.getPassportExpiry() != null && emp.getPassportExpiry().isBefore(limit));
    }

    private UserSession getCurrentUser() {
        return (UserSession) FacesContext.getCurrentInstance()
                .getExternalContext().getRequestMap().get(AuthFilter.SESSION_ATTR);
    }

    private void addInfo(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO, msg, null));
    }

    private void addError(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, msg, null));
    }

    // ─── Getters & Setters ─────────────────────────
    public List<Employee> getEmployees() {
        return employees;
    }

    public List<Employee> getFilteredEmployees() {
        return filteredEmployees;
    }

    public void setFilteredEmployees(List<Employee> v) {
        this.filteredEmployees = v;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(String v) {
        this.searchQuery = v;
    }

    public String getFilterStatus() {
        return filterStatus;
    }

    public void setFilterStatus(String v) {
        this.filterStatus = v;
    }

    public Long getFilterDeptId() {
        return filterDeptId;
    }

    public void setFilterDeptId(Long v) {
        this.filterDeptId = v;
    }

    public Employee getSelected() {
        return selected;
    }

    public void setSelected(Employee v) {
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

    public List<Object[]> getDepartments() {
        return departments;
    }

    public List<Object[]> getDesignations() {
        return designations;
    }

    public List<Object[]> getCurrencies() {
        return currencies;
    }

    public List<Object[]> getManagers() {
        return managers;
    }

    public long getTotalActive() {
        return totalActive;
    }

    public long getExpiringDocs() {
        return expiringDocs;
    }
}
