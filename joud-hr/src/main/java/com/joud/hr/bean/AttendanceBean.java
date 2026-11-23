package com.joud.hr.bean;

import com.joud.hr.entity.Attendance;
import com.joud.hr.entity.Attendance.AttStatus;
import com.joud.hr.entity.Employee;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AttendanceBean — تسجيل الحضور والغياب اليومي
 *
 * يعرض سجل اليوم المختار لكل الموظفين النشطين دفعة واحدة
 * (يُنشئ سطراً افتراضياً "حاضر" لمن لا سجل له بعد)، يتيح
 * تسجيل وقت الحضور/الانصراف أو تغيير الحالة مباشرة من الجدول،
 * بالإضافة إلى ملخص شهري لكل موظف.
 */
@Named("attBean")
@ViewScoped
public class AttendanceBean implements Serializable {

    // ─── شاشة اليوم ─────────────────────────────────
    private LocalDate selectedDate = LocalDate.now();
    private List<AttendanceRow> dayRows = new ArrayList<>();

    // ─── إحصاءات اليوم ──────────────────────────────
    private long presentCount, absentCount, leaveCount;

    // ─── الملخص الشهري ──────────────────────────────
    private int summaryYear = LocalDate.now().getYear();
    private int summaryMonth = LocalDate.now().getMonthValue();
    private List<Object[]> monthlySummary;

    private Long agentId;

    @PersistenceContext
    private EntityManager em;

    @PostConstruct
    public void init() {
        UserSession user = getCurrentUser();
        if (user != null)
            agentId = user.getAgentId();
        loadDay();
        loadMonthlySummary();
    }

    // ════════════════════════════════════════════════
    // تحميل سجل اليوم — مع سطر افتراضي لكل موظف نشط
    // ════════════════════════════════════════════════

    public void loadDay() {
        try {
            List<Employee> employees = em.createNamedQuery("Employee.findActive", Employee.class)
                    .setParameter("agentId", agentId).getResultList();

            List<Attendance> existing = em.createNamedQuery("Attendance.findByDate", Attendance.class)
                    .setParameter("agentId", agentId).setParameter("date", selectedDate)
                    .getResultList();

            dayRows = new ArrayList<>();
            presentCount = absentCount = leaveCount = 0;

            for (Employee emp : employees) {
                Attendance att = existing.stream()
                        .filter(a -> a.getEmployeeId().equals(emp.getId()))
                        .findFirst().orElse(null);

                boolean isNew = att == null;
                if (isNew) {
                    att = new Attendance();
                    att.setEmployeeId(emp.getId());
                    att.setAgentId(agentId);
                    att.setAttDate(selectedDate);
                    att.setStatus(AttStatus.P);
                }
                dayRows.add(new AttendanceRow(att, emp.getName(), emp.getEmployeeCode(), isNew));

                switch (att.getStatus()) {
                    case P -> presentCount++;
                    case A -> absentCount++;
                    case L -> leaveCount++;
                    default -> {
                    }
                }
            }
        } catch (Exception e) {
            dayRows = new ArrayList<>();
        }
    }

    public void onDateChange() {
        loadDay();
    }

    // ════════════════════════════════════════════════
    // الحفظ — يُنشئ أو يُحدِّث كل سطر معدَّل
    // ════════════════════════════════════════════════

    @Transactional
    public void saveDay() {
        try {
            for (AttendanceRow row : dayRows) {
                Attendance att = row.attendance();
                att.calculateHours();
                if (row.isNew() && att.getId() == null) {
                    att.setId(generateId());
                    em.persist(att);
                } else {
                    em.merge(att);
                }
            }
            addInfo("تم حفظ سجل الحضور ليوم " + selectedDate);
            loadDay();
            loadMonthlySummary();
        } catch (Exception e) {
            addError("خطأ في الحفظ: " + e.getMessage());
        }
    }

    /** تسجيل حضور سريع — يضبط وقت الحضور الآن */
    public void quickCheckIn(AttendanceRow row) {
        row.attendance().setCheckIn(LocalDateTime.now());
        row.attendance().setStatus(AttStatus.P);
    }

    /** تسجيل انصراف سريع — يضبط وقت الانصراف الآن ويحسب الساعات */
    public void quickCheckOut(AttendanceRow row) {
        row.attendance().setCheckOut(LocalDateTime.now());
        row.attendance().calculateHours();
    }

    /** تحديد الكل كحاضرين دفعة واحدة (يوم عمل عادي) */
    public void markAllPresent() {
        for (AttendanceRow row : dayRows) {
            row.attendance().setStatus(AttStatus.P);
        }
        recalcCounts();
    }

    public void onStatusChange() {
        recalcCounts();
    }

    private void recalcCounts() {
        presentCount = absentCount = leaveCount = 0;
        for (AttendanceRow row : dayRows) {
            switch (row.attendance().getStatus()) {
                case P -> presentCount++;
                case A -> absentCount++;
                case L -> leaveCount++;
                default -> {
                }
            }
        }
    }

    // ════════════════════════════════════════════════
    // الملخص الشهري
    // ════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    public void loadMonthlySummary() {
        try {
            monthlySummary = em.createNativeQuery(
                    "SELECT e.EMP_C_NAME, a.PRESENT_DAYS, a.ABSENT_DAYS, " +
                            "a.LEAVE_DAYS, a.TOTAL_HOURS, a.TOTAL_OT_HOURS " +
                            "FROM V_ATTENDANCE_MONTHLY a " +
                            "JOIN EMPLOYEE_MAST e ON a.EMP_i_id = e.EMP_i_id " +
                            "WHERE a.AGENT_I_ID=:a AND a.ATT_YEAR=:y AND a.ATT_MONTH=:m " +
                            "ORDER BY e.EMP_C_NAME")
                    .setParameter("a", agentId).setParameter("y", summaryYear)
                    .setParameter("m", summaryMonth).getResultList();
        } catch (Exception e) {
            monthlySummary = List.of();
        }
    }

    // ════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════

    private Long generateId() {
        try {
            return ((Number) em.createNativeQuery(
                    "SELECT ID_I_GEN FROM KEY_GENERATION WHERE TABLE_C_NAME='ATTENDANCE_MAST'")
                    .getSingleResult()).longValue();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    public String getMonthName(int m) {
        String[] n = { "", "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
                "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر" };
        return m >= 1 && m <= 12 ? n[m] : String.valueOf(m);
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

    // ─── Inner Record لعرض الجدول ────────────────────
    public record AttendanceRow(Attendance attendance, String employeeName,
            String employeeCode, boolean isNew) {
    }

    // ─── Getters & Setters ─────────────────────────
    public LocalDate getSelectedDate() {
        return selectedDate;
    }

    public void setSelectedDate(LocalDate v) {
        this.selectedDate = v;
    }

    public List<AttendanceRow> getDayRows() {
        return dayRows;
    }

    public long getPresentCount() {
        return presentCount;
    }

    public long getAbsentCount() {
        return absentCount;
    }

    public long getLeaveCount() {
        return leaveCount;
    }

    public int getSummaryYear() {
        return summaryYear;
    }

    public void setSummaryYear(int v) {
        this.summaryYear = v;
    }

    public int getSummaryMonth() {
        return summaryMonth;
    }

    public void setSummaryMonth(int v) {
        this.summaryMonth = v;
    }

    public List<Object[]> getMonthlySummary() {
        return monthlySummary;
    }
}
