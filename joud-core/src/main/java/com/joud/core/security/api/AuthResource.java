package com.joud.core.security.api;

import com.joud.core.security.service.AuthService;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.util.logging.Logger;

@RequestScoped
@Path("/auth") // سيعمل الرابط تحت: /api/auth
public class AuthResource {

    private static final Logger LOG = Logger.getLogger(AuthResource.class.getName());

    @Inject
    private AuthService authService; // حقن نفس سيرفيس الأمان المركزية للنواة

    @Context
    private HttpServletRequest servletRequest; // لجلب الـ IP الخاص بالطلب الخارجي لـ Brute Force protection

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    // تنبيه: لا تضع وسم @Secured هنا لأن المستخدم لا يملك توكن بعد
    public Response login(@FormParam("user") String username, @FormParam("password") String password) {

        // 1. استخراج الـ IP لتمريره لنظام الحماية والأمان الخاص بك
        String ip = servletRequest != null ? servletRequest.getRemoteAddr() : "0:0:0:0:0:0:0:1";

        // 2. استدعاء نفس منطق التحقق المركزي للنظام
        AuthService.LoginOutcome outcome = authService.login(username, password, ip);

        // 3. معالجة النتيجة بناءً على الـ Outcome الخاص بنظام جود
        switch (outcome.result()) {
            case SUCCESS -> {
                // 💡 السحر هنا: بدلاً من الكوكيز والـ xhtml، نقوم بتوليد وجلب توكن الـ JWT
                // وإرجاعه
                String jwtToken = generateJwtToken(username); // استدع دالة توليد الـ JWT الخاصة بك هنا

                LOG.info("REST Login successful for user: " + username + " from IP: " + ip);
                return Response.ok(jwtToken).build(); // إرسال التوكن لبوستمان بكود 200 OK
            }
            case IP_BLOCKED -> {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity("تم حظر هذا الـ IP مؤقتاً بسبب محاولات خاطئة.").build();
            }
            default -> {
                // تأخير بسيط لإحباط هجمات التخمين كالموجود في الـ Bean
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(outcome.message() != null ? outcome.message() : "اسم المستخدم أو كلمة المرور غير صحيحة")
                        .build();
            }
        }
    }

    private String generateJwtToken(String username) {
        // هنا تضع كود بناء توكن الـ JWT الفعلي (مثلاً باستخدام nimbus-jose-jwt أو jjwt)
        // حالياً لأغراض الفحص، يمكنك إرجاع نص تجريبي ثابت متناسق مع الفلتر
        return "mocked-jwt-token-for-" + username;
    }
}