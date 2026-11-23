package com.joud.core.security.session;

import com.joud.core.security.model.User;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * UserSession — بيانات جلسة المستخدم النشطة
 * يُخزَّن في SessionManager (ApplicationScoped)
 */
public class UserSession implements Serializable {

    private final String   sessionToken;
    private final Long     userId;
    private final String   username;
    private final String   email;
    private final Long     agentId;
    private final Set<String> roles;
    private final Set<String> permissions;
    private final LocalDateTime loginTime;
    private       LocalDateTime lastActivity;

    public UserSession(User user, String sessionToken) {
        this.sessionToken  = sessionToken;
        this.userId        = user.getId();
        this.username      = user.getUsername();
        this.email         = user.getEmail();
        this.agentId       = user.getAgentId();
        this.roles         = user.getRoles().stream()
                                 .map(r -> r.getName())
                                 .collect(Collectors.toSet());
        this.permissions   = user.getRoles().stream()
                                 .flatMap(r -> r.getPermissions().stream())
                                 .collect(Collectors.toSet());
        this.loginTime     = LocalDateTime.now();
        this.lastActivity  = LocalDateTime.now();
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public void touch() {
        this.lastActivity = LocalDateTime.now();
    }

    // ─── Getters ───────────────────────────────────
    public String getSessionToken()       { return sessionToken; }
    public Long getUserId()               { return userId; }
    public String getUsername()           { return username; }
    public String getEmail()              { return email; }
    public Long getAgentId()              { return agentId; }
    public Set<String> getRoles()         { return roles; }
    public Set<String> getPermissions()   { return permissions; }
    public LocalDateTime getLoginTime()   { return loginTime; }
    public LocalDateTime getLastActivity(){ return lastActivity; }
}
