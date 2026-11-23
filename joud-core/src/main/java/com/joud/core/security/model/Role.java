package com.joud.core.security.model;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Role — كيان الدور (Admin, Manager, User...)
 * جدول: role
 */
@Entity
@Table(name = "role")
public class Role {

    @Id
    @Column(name = "role_i_id")
    private Long id;

    @Column(name = "role_c_name", unique = true, nullable = false, length = 30)
    private String name;

    @Column(name = "role_c_desc", length = 100)
    private String description;

    // الصلاحيات كـ Set<String> في جدول منفصل
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_i_id")
    )
    @Column(name = "permission_code", length = 100)
    private Set<String> permissions = new HashSet<>();

    // ─── Getters & Setters ─────────────────────────
    public Long getId()                      { return id; }
    public void setId(Long id)               { this.id = id; }
    public String getName()                  { return name; }
    public void setName(String v)            { this.name = v; }
    public String getDescription()           { return description; }
    public void setDescription(String v)     { this.description = v; }
    public Set<String> getPermissions()      { return permissions; }
    public void setPermissions(Set<String> v){ this.permissions = v; }
}
