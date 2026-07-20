package com.joud.core.security.service;

import com.joud.core.menu.MenuItem;
import com.joud.core.module.PluginRegistry;
import com.joud.core.security.filter.AuthFilter;
import com.joud.core.security.session.UserSession;

import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * NavbarBean — Backing Bean للقائمة الجانبية الديناميكية
 *
 * يجمع عناصر القائمة من جميع الوحدات المثبتة عبر PluginRegistry
 * ويفلترها حسب صلاحيات المستخدم الحالي.
 */
@Named("navbarBean")
@RequestScoped
public class NavbarBean {

    @Inject private PluginRegistry pluginRegistry;

    // ─── المستخدم الحالي ────────────────────────────

    public UserSession getCurrentUser() {
        return (UserSession) FacesContext.getCurrentInstance()
            .getExternalContext()
            .getRequestMap()
            .get(AuthFilter.SESSION_ATTR);
    }

    public String getCurrentUsername() {
        UserSession u = getCurrentUser();
        return u != null ? u.getUsername() : "—";
    }

    public String getCurrentUserInitials() {
        UserSession u = getCurrentUser();
        if (u == null || u.getUsername() == null) return "?";
        String name = u.getUsername();
        return name.length() >= 2
            ? name.substring(0, 2).toUpperCase()
            : name.toUpperCase();
    }

    public String getCurrentUserRole() {
        UserSession u = getCurrentUser();
        if (u == null || u.getRoles().isEmpty()) return "مستخدم";
        return u.getRoles().iterator().next();
    }

    // ─── عناصر القائمة ──────────────────────────────

    /**
     * يعيد قائمة العناصر المفلترة حسب صلاحيات المستخدم
     * مرتبة حسب getMenuOrder() من كل وحدة
     */
    public List<MenuItem> getMenuItems() {
        Locale locale = FacesContext.getCurrentInstance()
            .getViewRoot().getLocale();

        UserSession user = getCurrentUser();

        return pluginRegistry.getAllMenuItems(locale)
            .stream()
            .filter(item -> canView(item, user))
            .collect(Collectors.toList());
    }

    /**
     * هل المستخدم يملك صلاحية رؤية هذا العنصر؟
     */
    private boolean canView(MenuItem item, UserSession user) {
        if (item.getPermission() == null || item.getPermission().isBlank()) return true;
        if (user == null) return false;
        return user.hasPermission(item.getPermission());
    }

    // ─── التاريخ ─────────────────────────────────────

    public String getCurrentDate() {
        return LocalDate.now()
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    public String getCurrentDayName() {
        return LocalDate.now()
            .format(DateTimeFormatter.ofPattern("EEEE", Locale.of("ar")));
    }

    // ─── الوحدات النشطة ──────────────────────────────

    public int getInstalledModulesCount() {
        return pluginRegistry.getInstalledModules().size();
    }
}
