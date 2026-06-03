package com.joud.core.security.service;

import com.joud.core.security.filter.AuthFilter;
import com.joud.core.security.session.UserSession;

import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

/**
 * LoginBean — الـ Backing Bean لشاشة تسجيل الدخول
 *
 * يربط شاشة login.xhtml بـ AuthService.
 * يضع الـ session token في HTTP Cookie عند النجاح.
 */
@Named("loginBean")
@RequestScoped
public class LoginBean {

    private String username;
    private String password;
    private boolean rememberMe;

    @Inject private AuthService authService;

    // ─── الدخول ────────────────────────────────────

    public String login() {
        FacesContext ctx = FacesContext.getCurrentInstance();

        // استخرج IP المستخدم
        String ip = ((jakarta.servlet.http.HttpServletRequest)
            ctx.getExternalContext().getRequest()).getRemoteAddr();

        AuthService.LoginOutcome outcome = authService.login(username, password, ip);

        switch (outcome.result()) {
            case SUCCESS -> {
                // ضع الـ token في Cookie
                setSessionCookie(ctx, outcome.session());
                // ضع الجلسة في request للاستخدام الفوري
                ctx.getExternalContext().getRequestMap()
                   .put(AuthFilter.SESSION_ATTR, outcome.session());
                return "/index?faces-redirect=true";
            }
            case IP_BLOCKED -> {
                ctx.addMessage(null, new FacesMessage(
                    FacesMessage.SEVERITY_ERROR, outcome.message(), null));
                return null;
            }
            default -> {
                ctx.addMessage(null, new FacesMessage(
                    FacesMessage.SEVERITY_ERROR, outcome.message(), null));
                // تأخير بسيط لإبطاء Brute Force
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                return null;
            }
        }
    }

    // ─── الخروج ────────────────────────────────────

    public String logout() {
        FacesContext ctx = FacesContext.getCurrentInstance();
        UserSession session = (UserSession) ctx.getExternalContext()
            .getRequestMap().get(AuthFilter.SESSION_ATTR);

        if (session != null) {
            authService.logout(session.getSessionToken());
        }

        // احذف الـ Cookie
        clearSessionCookie(ctx);

        return "/login?faces-redirect=true";
    }

    // ─── Private ────────────────────────────────────

    private void setSessionCookie(FacesContext ctx, UserSession session) {
        HttpServletResponse response =
            (HttpServletResponse) ctx.getExternalContext().getResponse();

        Cookie cookie = new Cookie(AuthFilter.SESSION_COOKIE, session.getSessionToken());
        cookie.setHttpOnly(true);   // لا يُقرأ من JavaScript
        cookie.setSecure(false);    // اجعلها true في الـ Production مع HTTPS
        cookie.setPath(ctx.getExternalContext().getRequestContextPath() + "/");
        cookie.setMaxAge(rememberMe ? 7 * 24 * 3600 : -1); // أسبوع أو جلسة فقط
        response.addCookie(cookie);
    }

    private void clearSessionCookie(FacesContext ctx) {
        HttpServletResponse response =
            (HttpServletResponse) ctx.getExternalContext().getResponse();

        Cookie cookie = new Cookie(AuthFilter.SESSION_COOKIE, "");
        cookie.setMaxAge(0);
        cookie.setPath(ctx.getExternalContext().getRequestContextPath() + "/");
        response.addCookie(cookie);
    }

    // ─── Getters & Setters ─────────────────────────
    public String getUsername()          { return username; }
    public void setUsername(String v)    { this.username = v; }
    public String getPassword()          { return password; }
    public void setPassword(String v)    { this.password = v; }
    public boolean isRememberMe()        { return rememberMe; }
    public void setRememberMe(boolean v) { this.rememberMe = v; }
}
