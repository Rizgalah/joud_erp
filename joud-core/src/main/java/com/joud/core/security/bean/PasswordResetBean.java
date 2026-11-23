package com.joud.core.security.bean;

import com.joud.core.security.model.User;
import com.joud.core.security.service.PasswordResetService;
import com.joud.security.util.SecurityResponseUtil;

import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import java.io.Serializable;
import java.util.Map;
import java.util.Optional;

/**
 * PasswordResetBean — Backing Bean لصفحة إعادة تعيين كلمة المرور
 *
 * دورة الحياة:
 * 1. @PostConstruct: قراءة التوكن من رابط البريد (?token=xxx)
 * 2. التحقق من صلاحية التوكن وعرض اسم المستخدم
 * 3. عند الضغط على "توليد كلمة مرور جديدة":
 * - استدعاء PasswordResetService.resetPassword()
 * - عرض كلمة المرور الجديدة للمستخدم مرة واحدة
 */
@Named("passwordResetBean")
@ViewScoped
public class PasswordResetBean implements Serializable {
    @Context
    private ContainerRequestContext requestContext;

    @Inject
    private PasswordResetService resetService;

    // ─── حالة الصفحة ──────────────────────────────

    /** التوكن القادم من رابط البريد الإلكتروني */
    private String token;

    /** المستخدم المُتحقَّق منه */
    private User verifiedUser;

    /** كلمة المرور الجديدة المولَّدة (تظهر مرة واحدة فقط) */
    private String generatedPassword;

    /** حالة الصفحة: CHECKING → VALID / INVALID → DONE */
    private PageState state = PageState.CHECKING;

    public enum PageState {
        CHECKING, // يتحقق من التوكن
        VALID, // التوكن صالح — ينتظر تأكيد المستخدم
        INVALID, // التوكن غير صالح أو منتهي الصلاحية
        DONE // تمّ إعادة التعيين بنجاح
    }

    // ─── التهيئة: قراءة التوكن والتحقق منه ─────────

    @PostConstruct
    public void init() {
        // قراءة البارامتر ?token=xxx من الرابط
        Map<String, String> params = FacesContext.getCurrentInstance()
                .getExternalContext().getRequestParameterMap();
        token = params.get("token");

        // 1. في حالة عدم وجود توكن أو توكن تالف (401 Unauthorized)
        if (token == null || token.isBlank()) {
            SecurityResponseUtil.createErrorResponse(
                    Response.Status.UNAUTHORIZED,
                    "صلاحية الوصول انتهت أو غير موجودة. يرجى تسجيل الدخول مجدداً.",
                    "/auth/reset-password");

        }

        // التحقق من صلاحية التوكن
        Optional<User> userOpt = resetService.validateToken(token);
        if (userOpt.isPresent()) {
            verifiedUser = userOpt.get();
            state = PageState.VALID;
        } else {
            state = PageState.INVALID;
        }
    }

    // ─── Action: توليد كلمة مرور جديدة ─────────────

    /**
     * يُطلق عند ضغط المستخدم على زر "توليد كلمة مرور جديدة"
     */
    public void doReset() {
        if (state != PageState.VALID)
            return;

        Optional<String> newPwd = resetService.resetPassword(token);

        if (newPwd.isPresent()) {
            generatedPassword = newPwd.get();
            state = PageState.DONE;
            addInfo("تم توليد كلمة المرور الجديدة بنجاح. احفظها الآن، لن تظهر مجدداً.");
        } else {
            state = PageState.INVALID;
            addError("انتهت صلاحية الرابط أو تم استخدامه مسبقاً. الرجاء طلب رابط جديد.");
        }
    }

    // ─── Getters ──────────────────────────────────

    public String getToken() {
        return token;
    }

    public User getVerifiedUser() {
        return verifiedUser;
    }

    public String getGeneratedPassword() {
        return generatedPassword;
    }

    public PageState getState() {
        return state;
    }

    public boolean isValid() {
        return state == PageState.VALID;
    }

    public boolean isInvalid() {
        return state == PageState.INVALID;
    }

    public boolean isDone() {
        return state == PageState.DONE;
    }

    /** اسم المستخدم للعرض */
    public String getUsername() {
        return verifiedUser != null ? verifiedUser.getUsername() : "";
    }

    // ─── Helpers ──────────────────────────────────

    private void addInfo(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO, msg, null));
    }

    private void addError(String msg) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, msg, null));
    }
}
