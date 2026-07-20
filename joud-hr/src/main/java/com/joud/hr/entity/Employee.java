package com.joud.hr.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

import com.joud.core.security.model.User;

/**
 * Employee — كيان الموظف الرئيسي
 * جدول: EMPLOYEE_MAST (موجود مسبقاً + حقول مُضافة)
 */
@Entity
@Table(name = "EMPLOYEE_MAST")
@NamedQueries({
        @NamedQuery(name = "Employee.findActive", query = "SELECT e FROM Employee e WHERE e.status='A' AND e.agentId=:agentId ORDER BY e.name"),
        @NamedQuery(name = "Employee.findByDept", query = "SELECT e FROM Employee e WHERE e.deptId=:deptId AND e.status='A' ORDER BY e.name"),
        @NamedQuery(name = "Employee.search", query = """
                SELECT e FROM Employee e
                WHERE e.agentId = :agentId AND e.status = 'A'
                  AND (LOWER(e.name) LIKE LOWER(CONCAT('%',:q,'%'))
                    OR e.name LIKE CONCAT('%',:q,'%'))
                ORDER BY e.name
                """),
})

public class Employee {

    /** نوع التوظيف */
    public enum EmploymentType {
        F, P, C, T
    }

    @Id
    @Column(name = "EMP_i_id")
    private Long id;

    @Column(name = "EMP_I_SEQNO")
    private Integer sequenceNo;

    @Column(name = "AGENT_I_ID", nullable = false)
    private Long agentId;

    // ─── البيانات الأساسية ──────────────────────────
    @Column(name = "EMP_C_NAME", nullable = false, length = 100)
    private String name;

    @Column(name = "EMP_C_ADD1", length = 100)
    private String address1;

    @Column(name = "EMP_C_ADD2", length = 100)
    private String address2;

    @Column(name = "EMP_C_CITY", length = 25)
    private String city;

    @Column(name = "COUNTRY_C_CODE", length = 10)
    private String countryCode;

    @Column(name = "EMP_D_DOJ", nullable = false)
    private LocalDate dateOfJoining;

    @Column(name = "EMP_D_CONFIRM")
    private LocalDate dateOfConfirmation;

    @Column(name = "EMP_D_DOB")
    private LocalDate dateOfBirth;

    @Column(name = "EMP_C_SEX", length = 1)
    private String gender; // M=ذكر, F=أنثى

    @Column(name = "EMP_C_MARSTS", length = 1)
    private String maritalStatus; // S=أعزب, M=متزوج

    // ─── الراتب الأساسي ─────────────────────────────
    @Column(name = "EMP_F_BASIC", nullable = false, precision = 23, scale = 5)
    private BigDecimal basicSalary = BigDecimal.ZERO;

    @Column(name = "EMP_F_APPROVED_SAL", precision = 23, scale = 5)
    private BigDecimal approvedSalary = BigDecimal.ZERO;

    @Column(name = "CURRENCY_I_ID")
    private Long currencyId;

    // ─── الوظيفة والقسم ─────────────────────────────
    @Column(name = "DEPT_I_ID")
    private Long deptId;

    @Column(name = "DESIG_I_ID")
    private Long desigId;

    @Enumerated(EnumType.STRING)
    @Column(name = "EMP_C_EMP_TYPE", length = 1)
    private EmploymentType employmentType = EmploymentType.F;

    @Column(name = "MANAGER_EMP_ID")
    private Long managerId;

    // ─── الوثائق ────────────────────────────────────
    @Column(name = "EMP_C_NATIONALITY", length = 50)
    private String nationality;

    @Column(name = "EMP_C_ID_NO", length = 50)
    private String idNumber;

    @Column(name = "EMP_D_ID_EXP")
    private LocalDate idExpiry;

    @Column(name = "EMP_C_PASSPORT", length = 30)
    private String passportNumber;

    @Column(name = "EMP_D_PASS_EXP")
    private LocalDate passportExpiry;

    // ─── التواصل ────────────────────────────────────
    @Column(name = "EMP_C_WORK_EMAIL", length = 100)
    private String workEmail;

    @Column(name = "EMP_C_MOBILE", length = 30)
    private String mobile;

    // ─── البنك ──────────────────────────────────────
    @Column(name = "EMP_C_BANK_ACC", length = 50)
    private String bankAccount;

    @Column(name = "EMP_C_BANK_NAME", length = 100)
    private String bankName;

    // ─── الحالة ─────────────────────────────────────
    @Column(name = "EMP_C_STATUS", length = 1)
    private String status = "A"; // A=نشط, I=غير نشط, T=منتهي

    @Column(name = "EMP_C_FLAG", length = 1)
    private String flag;

    @Column(name = "EMP_C_AUTHLEVEL", length = 10)
    private String authLevel;

    @OneToOne
    @JoinColumn(name = "user_i_id") // العمود في جدول الموظفين الذي يربطه بالمستخدم
    private User userIId;

    public User getUserIId() {
        return userIId;
    }

    public void setUserIId(User userIId) {
        this.userIId = userIId;
    }

    // ─── Helpers ───────────────────────────────────
    public boolean isActive() {
        return "A".equals(status);
    }

    public boolean isFullTime() {
        return EmploymentType.F == employmentType;
    }

    public int getYearsOfService() {
        if (dateOfJoining == null)
            return 0;
        return LocalDate.now().getYear() - dateOfJoining.getYear();
    }

    public String getEmploymentTypeLabel() {
        if (employmentType == null)
            return "—";
        return switch (employmentType) {
            case F -> "دوام كامل";
            case P -> "دوام جزئي";
            case C -> "عقد";
            case T -> "مؤقت";
        };
    }

    // ─── Getters & Setters ─────────────────────────
    public Long getId() {
        return id;
    }

    public void setId(Long v) {
        this.id = v;
    }

    public Integer getSequenceNo() {
        return sequenceNo;
    }

    public void setSequenceNo(Integer v) {
        this.sequenceNo = v;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long v) {
        this.agentId = v;
    }

    public String getName() {
        return name;
    }

    public void setName(String v) {
        this.name = v;
    }

    public String getAddress1() {
        return address1;
    }

    public void setAddress1(String v) {
        this.address1 = v;
    }

    public String getAddress2() {
        return address2;
    }

    public void setAddress2(String v) {
        this.address2 = v;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String v) {
        this.city = v;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String v) {
        this.countryCode = v;
    }

    public LocalDate getDateOfJoining() {
        return dateOfJoining;
    }

    public void setDateOfJoining(LocalDate v) {
        this.dateOfJoining = v;
    }

    public LocalDate getDateOfConfirmation() {
        return dateOfConfirmation;
    }

    public void setDateOfConfirmation(LocalDate v) {
        this.dateOfConfirmation = v;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate v) {
        this.dateOfBirth = v;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String v) {
        this.gender = v;
    }

    public String getMaritalStatus() {
        return maritalStatus;
    }

    public void setMaritalStatus(String v) {
        this.maritalStatus = v;
    }

    public BigDecimal getBasicSalary() {
        return basicSalary;
    }

    public void setBasicSalary(BigDecimal v) {
        this.basicSalary = v;
    }

    public BigDecimal getApprovedSalary() {
        return approvedSalary;
    }

    public void setApprovedSalary(BigDecimal v) {
        this.approvedSalary = v;
    }

    public Long getCurrencyId() {
        return currencyId;
    }

    public void setCurrencyId(Long v) {
        this.currencyId = v;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long v) {
        this.deptId = v;
    }

    public Long getDesigId() {
        return desigId;
    }

    public void setDesigId(Long v) {
        this.desigId = v;
    }

    public EmploymentType getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(EmploymentType v) {
        this.employmentType = v;
    }

    public Long getManagerId() {
        return managerId;
    }

    public void setManagerId(Long v) {
        this.managerId = v;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String v) {
        this.nationality = v;
    }

    public String getIdNumber() {
        return idNumber;
    }

    public void setIdNumber(String v) {
        this.idNumber = v;
    }

    public LocalDate getIdExpiry() {
        return idExpiry;
    }

    public void setIdExpiry(LocalDate v) {
        this.idExpiry = v;
    }

    public String getPassportNumber() {
        return passportNumber;
    }

    public void setPassportNumber(String v) {
        this.passportNumber = v;
    }

    public LocalDate getPassportExpiry() {
        return passportExpiry;
    }

    public void setPassportExpiry(LocalDate v) {
        this.passportExpiry = v;
    }

    public String getWorkEmail() {
        return workEmail;
    }

    public void setWorkEmail(String v) {
        this.workEmail = v;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String v) {
        this.mobile = v;
    }

    public String getBankAccount() {
        return bankAccount;
    }

    public void setBankAccount(String v) {
        this.bankAccount = v;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String v) {
        this.bankName = v;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String v) {
        this.status = v;
    }

    public String getFlag() {
        return flag;
    }

    public void setFlag(String v) {
        this.flag = v;
    }

    public String getAuthLevel() {
        return authLevel;
    }

    public void setAuthLevel(String v) {
        this.authLevel = v;
    }

    // للكود القديم
    public String getEmployeeCode() {
        return String.format("EMP-%05d", sequenceNo != null ? sequenceNo : id);
    }
}
