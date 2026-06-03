package com.joud.core.security.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * User — كيان المستخدم الرئيسي
 * جدول: users
 */
@Entity
@Table(name = "users")
@NamedQueries({
    @NamedQuery(name = "User.findByUsername",
        query = "SELECT u FROM User u WHERE u.username = :username AND u.status = 'A'"),
    @NamedQuery(name = "User.findByEmail",
        query = "SELECT u FROM User u WHERE u.email = :email AND u.status = 'A'"),
})
public class User {

    @Id
    @Column(name = "user_i_id")
    private Long id;

    @Column(name = "user_c_name", unique = true, nullable = false, length = 100)
    private String username;

    @Column(name = "email", unique = true, nullable = false, length = 100)
    private String email;

    @Column(name = "password_c_hash", nullable = false, length = 100)
    private String passwordHash;

    // A=Active, L=Locked, I=Inactive
    @Column(name = "users_c_status", nullable = false, length = 1)
    private String status = "A";

    @Column(name = "failed_c_attempts")
    private Integer failedAttempts = 0;

    @Column(name = "locked_i_until")
    private LocalDateTime lockedUntil;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "AGENT_I_ID")
    private Long agentId;

    @Column(name = "EMP_i_id")
    private Long employeeId;

    // العلاقة مع الأدوار
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns        = @JoinColumn(name = "user_i_id"),
        inverseJoinColumns = @JoinColumn(name = "role_i_id")
    )
    private Set<Role> roles = new HashSet<>();

    // ─── Helpers ───────────────────────────────────

    public boolean isActive()  { return "A".equals(status); }
    public boolean isLocked()  { return "L".equals(status) ||
                                        (lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now())); }
    public boolean hasRole(String roleName) {
        return roles.stream().anyMatch(r -> r.getName().equalsIgnoreCase(roleName));
    }
    public boolean hasPermission(String permission) {
        return roles.stream().flatMap(r -> r.getPermissions().stream())
                    .anyMatch(p -> p.equalsIgnoreCase(permission));
    }

    // ─── Getters & Setters ─────────────────────────
    public Long getId()                      { return id; }
    public void setId(Long id)               { this.id = id; }
    public String getUsername()              { return username; }
    public void setUsername(String v)        { this.username = v; }
    public String getEmail()                 { return email; }
    public void setEmail(String v)           { this.email = v; }
    public String getPasswordHash()          { return passwordHash; }
    public void setPasswordHash(String v)    { this.passwordHash = v; }
    public String getStatus()               { return status; }
    public void setStatus(String v)         { this.status = v; }
    public Integer getFailedAttempts()       { return failedAttempts; }
    public void setFailedAttempts(Integer v) { this.failedAttempts = v; }
    public LocalDateTime getLockedUntil()    { return lockedUntil; }
    public void setLockedUntil(LocalDateTime v) { this.lockedUntil = v; }
    public LocalDateTime getLastLogin()      { return lastLogin; }
    public void setLastLogin(LocalDateTime v){ this.lastLogin = v; }
    public LocalDateTime getCreatedAt()      { return createdAt; }
    public Long getAgentId()                 { return agentId; }
    public void setAgentId(Long v)           { this.agentId = v; }
    public Long getEmployeeId()              { return employeeId; }
    public void setEmployeeId(Long v)        { this.employeeId = v; }
    public Set<Role> getRoles()              { return roles; }
    public void setRoles(Set<Role> v)        { this.roles = v; }
}
