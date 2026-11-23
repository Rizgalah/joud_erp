package com.joud.core.menu;

import java.util.ArrayList;
import java.util.List;

/**
 * MenuItem — عنصر في القائمة الجانبية
 *
 * الـ Header/Navbar يجمع قوائم جميع الوحدات المثبتة
 * ويبني القائمة الكاملة تلقائياً.
 */
public class MenuItem {

    private final String id;           // معرف فريد (e.g. "hr.employees")
    private final String label;        // النص الظاهر للمستخدم
    private final String icon;         // أيقونة PrimeFaces (e.g. "pi pi-users")
    private final String outcome;      // وجهة JSF navigation (e.g. "/hr/employees")
    private final String permission;   // الصلاحية المطلوبة (e.g. "hr.employee.view")
    private final int order;           // الترتيب داخل مجموعة الوحدة
    private final List<MenuItem> children; // عناصر فرعية (قائمة منسدلة)

    private MenuItem(Builder builder) {
        this.id         = builder.id;
        this.label      = builder.label;
        this.icon       = builder.icon;
        this.outcome    = builder.outcome;
        this.permission = builder.permission;
        this.order      = builder.order;
        this.children   = builder.children;
    }

    // ─── Getters ───────────────────────────────────
    public String getId()             { return id; }
    public String getLabel()          { return label; }
    public String getIcon()           { return icon; }
    public String getOutcome()        { return outcome; }
    public String getPermission()     { return permission; }
    public int getOrder()             { return order; }
    public List<MenuItem> getChildren() { return children; }
    public boolean hasChildren()      { return !children.isEmpty(); }

    // ─── Builder ───────────────────────────────────

    public static Builder builder(String id, String label) {
        return new Builder(id, label);
    }

    public static class Builder {
        private final String id;
        private final String label;
        private String icon       = "pi pi-circle";
        private String outcome    = "";
        private String permission = "";
        private int order         = 10;
        private final List<MenuItem> children = new ArrayList<>();

        private Builder(String id, String label) {
            this.id    = id;
            this.label = label;
        }

        public Builder icon(String icon)             { this.icon = icon; return this; }
        public Builder outcome(String outcome)        { this.outcome = outcome; return this; }
        public Builder permission(String permission)  { this.permission = permission; return this; }
        public Builder order(int order)              { this.order = order; return this; }
        public Builder child(MenuItem item)          { this.children.add(item); return this; }
        public MenuItem build()                      { return new MenuItem(this); }
    }
}
