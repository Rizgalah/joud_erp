package com.joud.core.security.filter;

import com.joud.core.security.session.SessionManager;
import com.joud.core.security.session.UserSession;

import jakarta.inject.Inject;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * AuthFilter — حارس البوابة
 *
 * يعترض كل طلب HTTP ويتحقق من صحة الجلسة.
 * الصفحات المستثناة: login.xhtml, resources/*, public/*
 *
 * آلية العمل:
 *   1. اقرأ الـ cookie "JOUD_SESSION"
 *   2. ابحث عن الجلسة في SessionManager
 *   3. إذا وُجدت → اسمح بالمرور وضع المستخدم في request
 *   4. إذا لم توجد → أعد التوجيه لصفحة الدخول
 */
@WebFilter(urlPatterns = "/*", filterName = "AuthFilter")
public class AuthFilter implements Filter {

    public static final String SESSION_COOKIE  = "JOUD_SESSION";
    public static final String SESSION_ATTR    = "currentUser";
    public static final String CSRF_ATTR       = "csrfToken";
    public static final String LOGIN_PAGE      = "/login.xhtml";

    // المسارات المستثناة من الفلتر
    private static final Set<String> PUBLIC_PATHS = Set.of(
        "/login.xhtml",
        "/login",
        "/error.xhtml"
    );

    // البادئات المستثناة (static resources)
    private static final Set<String> PUBLIC_PREFIXES = Set.of(
        "/javax.faces.resource/",
        "/jakarta.faces.resource/",
        "/resources/",
        "/public/"
    );

    @Inject
    private SessionManager sessionManager;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String path = request.getServletPath();

        // ─── مسار عام؟ اسمح بالمرور ────────────────
        if (isPublic(path)) {
            chain.doFilter(request, response);
            return;
        }

        // ─── ابحث عن الـ cookie ──────────────────────
        String token = extractToken(request);
        Optional<UserSession> sessionOpt = (token != null)
            ? sessionManager.find(token)
            : Optional.empty();

        if (sessionOpt.isEmpty()) {
            // لا توجد جلسة صالحة → توجه لتسجيل الدخول
            String loginUrl = request.getContextPath() + LOGIN_PAGE;
            // إذا كان طلب AJAX أرسل 401 بدل إعادة التوجيه
            if (isAjaxRequest(request)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Session expired");
            } else {
                response.sendRedirect(loginUrl);
            }
            return;
        }

        // ─── جلسة صالحة → ضع المستخدم في الطلب ─────
        request.setAttribute(SESSION_ATTR, sessionOpt.get());
        chain.doFilter(request, response);
    }

    // ─── Helpers ────────────────────────────────────

    private boolean isPublic(String path) {
        if (PUBLIC_PATHS.contains(path)) return true;
        return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private String extractToken(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie c : request.getCookies()) {
            if (SESSION_COOKIE.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    private boolean isAjaxRequest(HttpServletRequest request) {
        return "XMLHttpRequest".equals(request.getHeader("X-Requested-With"))
            || "partial/ajax".equals(request.getHeader("Faces-Request"));
    }
}
