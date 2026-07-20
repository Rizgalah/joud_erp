package com.joud.hr.bean;

import com.joud.hr.entity.LeaveRequest;
import com.joud.hr.entity.LeaveRequest.LeaveStatus;
import com.joud.hr.entity.LeaveType;
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
 * LeaveBean — إدارة طلبات الإجازة
 *
 * يتيح تقديم طلب إجازة جديد (مع حساب الأيام تلقائياً)، اعتماد
 * أو رفض الطلبات المعلقة، وعرض رصيد كل موظف من كل نوع إجازة
 * (الرصيد السنوي مطروحاً منه الأيام المعتمدة هذا العام).
 */
@Named("leaveBean")
@ViewScoped
public class LeaveBean implements Serializable {

    // ─── نموذج الطلب الجديد ─────────────────────────
    private Long employeeId;
    private Long leaveTypeId;
    private LocalDate dateFrom = LocalDate.now();
    private LocalDate dateTo = LocalDate.now();
    private BigDecimal days;
    private String reason;

    // ─── فلاتر القائمة ──────────────────────────────
    private String filterStatus = "P"; // P=معلق (افتراضي), A, R, C, ALL

    // ─── البيانات ────────────────────────────────────
    private List<LeaveRequest> requests;
    private List<Object[]> balanceSummary; // رصيد كل موظف من كل نوع

    // ─── قوائم الاختيار ─────────────────────────────
    private List<Object[]> employees;
    private List<LeaveType> leaveTypes;

    private Long agentId;

    @PersistenceContext
    private EntityManager em;

    @PostConstruct
    public void init() {
        UserSession user = getCurrentUser();
        if (user != null)
            agentId = user.getAgentId();
        loadLookups();
        loadRequests();
    }

    // ════════════════════════════════════════════════
    // تقديم طلب إجازة جديد
    // ════════════════════════════════════════════════

    public void onDatesChange() {
        if (dateFrom != null && dateTo != null && !dateTo.isBefore(dateFrom)) {
            days = new BigDecimal(
                    java.time.temporal.ChronoUnit.DAYS.between(dateFrom, dateTo) + 1);
        }
    }

    @Transactional
    public void submit() {
        if (employeeId == null) {
            addError("يجب اختيار الموظف");
            return;
        }
        if (leaveTypeId == null) {
            addError("يجب اختيار نوع الإجازة");
            return;
        }
        if (dateTo.isBefore(dateFrom)) {
            addError("تاريخ النهاية يجب أن يكون بعد تاريخ البداية");
            return;
        }

        try {
            LeaveRequest req = new LeaveRequest();
            req.setId(generateId());
            req.setEmployeeId(employeeId);
            req.setAgentId(agentId);
            req.setLeaveTypeId(leaveTypeId);
            req.setDateFrom(dateFrom);
            req.setDateTo(dateTo);
            req.calculateDays();
            req.setReason(reason);
            req.setStatus(LeaveStatus.P);
            em.persist(req);

            addInfo("تم تقديم طلب الإجازة — بانتظار الاعتماد");
            clearForm();
            loadRequests();
        } catch (Exception e) {
            addError("خطأ في تقديم الطلب: " + e.getMessage());
        }
    }

    public void clearForm() {
        employeeId = null;
        leaveTypeId = null;
        dateFrom = LocalDate.now();
        dateTo = LocalDate.now();
        days = null;
        reason = null;
    }

    // ════════════════════════════════════════════════
    // اعتماد / رفض / إلغاء
    // ════════════════════════════════════════════════

    @Transactional
    public void approve(LeaveRequest req) {
        try {
            LeaveRequest r = em.find(LeaveRequest.class, req.getId());
            if (r == null || !r.isPending()) {
                addError("لا يمكن اعتماد هذا الطلب");
                return;
            }

            UserSession user = getCurrentUser();
            r.setStatus(LeaveStatus.A);
            r.setApprovedBy(user != null ? user.getUserId() : null);
            r.setApprovedAt(LocalDate.now());
            em.merge(r);

            // تسجيل أيام الإجازة في سجل الحضور تلقائياً
            markAttendanceAsLeave(r);

            addInfo("تم اعتماد طلب الإجازة");
            loadRequests();
        } catch (Exception e) {
            addError("خطأ: " + e.getMessage());
        }
    }

    @Transactional
    public void reject(LeaveRequest req) {
        try {
            LeaveRequest r = em.find(LeaveRequest.class, req.getId());
            if (r == null || !r.isPending()) {
                addError("لا يمكن رفض هذا الطلب");
                return;
            }
            r.setStatus(LeaveStatus.R);
            em.merge(r);
            addInfo("تم رفض طلب الإجازة");
            loadRequests();
        } catch (Exception e) {
            addError("خطأ: " + e.getMessage());
        }
    }

    @Transactional
    public void cancel(LeaveRequest req) {
        try {
            LeaveRequest r = em.find(LeaveRequest.class, req.getId());
            if (r == null || !r.canCancel()) {
                addError("لا يمكن إلغاء هذا الطلب");
                return;
            }
            r.setStatus(LeaveStatus.C);
            em.merge(r);
            addInfo("تم إلغاء طلب الإجازة");
            loadRequests();
        } catch (Exception e) {
            addError("خطأ: " + e.getMessage());
        }
    }

    /** عند اعتماد إجازة، يسجِّل ATTENDANCE_MAST بحالة L لكل يوم في النطاق */
    private void markAttendanceAsLeave(LeaveRequest r) {
        LocalDate d = r.getDateFrom();
        while (!d.isAfter(r.getDateTo())) {
            try {
                long exists = ((Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM ATTENDANCE_MAST WHERE EMP_i_id=:e AND ATT_D_DATE=:d")
                        .setParameter("e", r.getEmployeeId()).setParameter("d", d)
                        .getSingleResult()).longValue();

                if (exists > 0) {
                    em.createNativeQuery(
                            "UPDATE ATTENDANCE_MAST SET ATT_C_STATUS='L' WHERE EMP_i_id=:e AND ATT_D_DATE=:d")
                            .setParameter("e", r.getEmployeeId()).setParameter("d", d)
                            .executeUpdate();
                } else {
                    em.createNativeQuery(
                            "INSERT INTO ATTENDANCE_MAST (ATT_I_ID,EMP_i_id,AGENT_I_ID,ATT_D_DATE,ATT_C_STATUS,CREATED_AT) "
                                    +
                                    "VALUES (:id,:e,:a,:d,'L',CURRENT_TIMESTAMP)")
                            .setParameter("id", generateAttId())
                            .setParameter("e", r.getEmployeeId())
                            .setParameter("a", r.getAgentId())
                            .setParameter("d", d)
                            .executeUpdate();
                }
            } catch (Exception ignored) {
            }
            d = d.plusDays(1);
        }
    }

    // ════════════════════════════════════════════════
    // تحميل القوائم
    // ════════════════════════════════════════════════

    public void search() {
        loadRequests();
    }

    private void loadRequests() {
        try {
            String jpql = "SELECT l FROM LeaveRequest l WHERE l.agentId=:a ";
            if (!"ALL".equals(filterStatus))
                jpql += "AND l.status=:s ";
            jpql += "ORDER BY l.dateFrom DESC";

            var q = em.createQuery(jpql, LeaveRequest.class).setParameter("a", agentId);
            if (!"ALL".equals(filterStatus))
                q.setParameter("s", LeaveStatus.valueOf(filterStatus));
            requests = q.getResultList();

            // تعبئة أسماء الموظفين وأنواع الإجازة للعرض
            for (LeaveRequest r : requests) {
                r.setEmployeeName(getEmployeeName(r.getEmployeeId()));
                r.setLeaveTypeName(getLeaveTypeName(r.getLeaveTypeId()));
            }
        } catch (Exception e) {
            requests = List.of();
        }
    }

    /** رصيد كل موظف من كل نوع إجازة: المتاح سنوياً ناقص المعتمد هذا العام */
    @SuppressWarnings("unchecked")
    public void loadBalanceSummary() {
        try {
            balanceSummary = em.createNativeQuery(
                    "SELECT e.EMP_C_NAME, lt.LVT_C_NAME, lt.LVT_I_ANNUAL_DAYS, " +
                            "NVL(SUM(CASE WHEN EXTRACT(YEAR FROM lr.LVR_D_FROM)=EXTRACT(YEAR FROM SYSDATE) " +
                            "             AND lr.LVR_C_STATUS='A' THEN lr.LVR_I_DAYS ELSE 0 END),0) AS USED " +
                            "FROM EMPLOYEE_MAST e " +
                            "CROSS JOIN LEAVE_TYPE lt " +
                            "LEFT JOIN LEAVE_REQUEST lr ON lr.EMP_i_id = e.EMP_i_id AND lr.LVT_I_ID = lt.LVT_I_ID " +
                            "WHERE e.AGENT_I_ID=:a AND e.EMP_C_STATUS='A' AND lt.AGENT_I_ID=:a AND lt.LVT_C_STATUS='A' "
                            +
                            "GROUP BY e.EMP_C_NAME, lt.LVT_C_NAME, lt.LVT_I_ANNUAL_DAYS " +
                            "ORDER BY e.EMP_C_NAME, lt.LVT_C_NAME")
                    .setParameter("a", agentId).getResultList();
        } catch (Exception e) {
            balanceSummary = List.of();
        }
    }

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
            leaveTypes = em.createQuery(
                    "SELECT t FROM LeaveType t WHERE t.agentId=:a AND t.status='A' ORDER BY t.name",
                    LeaveType.class).setParameter("a", agentId).getResultList();
        } catch (Exception e) {
            leaveTypes = List.of();
        }
    }

    // ════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════

    private String getEmployeeName(Long empId) {
        if (employees == null)
            return "—";
        return employees.stream().filter(e -> empId.equals(((Number) e[0]).longValue()))
                .map(e -> e[1].toString()).findFirst().orElse("—");
    }

    private String getLeaveTypeName(Long ltId) {
        if (leaveTypes == null)
            return "—";
        return leaveTypes.stream().filter(t -> ltId.equals(t.getId()))
                .map(LeaveType::getName).findFirst().orElse("—");
    }

    private Long generateId() {
        try {
            return ((Number) em.createNativeQuery(
                    "SELECT ID_I_GEN FROM KEY_GENERATION WHERE TABLE_C_NAME='LEAVE_REQUEST'")
                    .getSingleResult()).longValue();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private Long generateAttId() {
        try {
            return ((Number) em.createNativeQuery(
                    "SELECT ID_I_GEN FROM KEY_GENERATION WHERE TABLE_C_NAME='ATTENDANCE_MAST'")
                    .getSingleResult()).longValue();
        } catch (Exception e) {
            return System.currentTimeMillis() + 1;
        }
    }

    private UserSession getCurrentUser() {
        return (UserSession) FacesContext.getCurrentInstance()
                .getExternalContext().getRequestMap().get(AuthFilter.SESSION_ATTR);
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

    public Long getLeaveTypeId() {
        return leaveTypeId;
    }

    public void setLeaveTypeId(Long v) {
        this.leaveTypeId = v;
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

    public BigDecimal getDays() {
        return days;
    }

    public void setDays(BigDecimal v) {
        this.days = v;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String v) {
        this.reason = v;
    }

    public String getFilterStatus() {
        return filterStatus;
    }

    public void setFilterStatus(String v) {
        this.filterStatus = v;
    }

    public List<LeaveRequest> getRequests() {
        return requests;
    }

    public List<Object[]> getBalanceSummary() {
        return balanceSummary;
    }

    public List<Object[]> getEmployees() {
        return employees;
    }

    public List<LeaveType> getLeaveTypes() {
        return leaveTypes;
    }
}
