package com.joud.security;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import jakarta.ws.rs.core.MediaType;
import com.joud.security.dto.ApiError;
import java.time.LocalDateTime;

@Provider
@Secured // بفضل هذا الوسم (Name Binding)، لن يعمل الفلتر إلا على الدوال المحمية فقط.
         // دالة الـ Login لن تتأثر.
@Priority(Priorities.AUTHORIZATION)
public class JWTAuthorizationFilter implements ContainerRequestFilter {

    @Context
    private ResourceInfo resourceInfo;

    // دالة قطع الطلب بالـ JSON الموحد الأنيق الخاص بك
    private void abortWithJson(ContainerRequestContext requestContext, Response.Status status, String message) {
        ApiError apiError = new ApiError(
                LocalDateTime.now(),
                status.getStatusCode(),
                status.getReasonPhrase(),
                message,
                requestContext.getUriInfo().getPath());

        requestContext.abortWith(Response
                .status(status)
                .entity(apiError)
                .type(MediaType.APPLICATION_JSON)
                .build());
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        // 1. استخراج الـ Header وفحصه بأمان لتجنب الـ NullPointerException
        String authorizationHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);

        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            abortWithJson(requestContext, Response.Status.UNAUTHORIZED,
                    "صلاحية الوصول غير موجودة أو معطوبة. يرجى تمرير التوكن الصحيح.");
            return;
        }

        // 2. قص التوكن بأمان
        String token = authorizationHeader.substring("Bearer".length()).trim();

        try {
            // 3. فك التوكن واستخراج الأدوار
            List<String> userRoles = extractRolesFromToken(token);

            // 4. قراءة الوسوم لمعرفة الأدوار المطلوبة للدالة المستهدفة
            Method method = resourceInfo.getResourceMethod();
            Secured securedAnnotation = method.getAnnotation(Secured.class);

            if (securedAnnotation == null) {
                Class<?> clazz = resourceInfo.getResourceClass();
                securedAnnotation = clazz.getAnnotation(Secured.class);
            }

            if (securedAnnotation != null) {
                String[] allowedRoles = securedAnnotation.value();

                if (allowedRoles.length > 0) {
                    boolean hasPermission = checkUserRoles(userRoles, allowedRoles);

                    if (!hasPermission) {
                        // استخدام دالتك الأنيقة الموحدة لحظر المستخدم غير المصرح له بدلاً من النص
                        // القديم
                        abortWithJson(requestContext, Response.Status.FORBIDDEN,
                                "عذراً، لا تمتلك الصلاحيات الكافية للوصول إلى هذا القسم من النظام.");
                        return;
                    }
                }
            }

            // 5. حقن السياق الأمني (SecurityContext) لتستفيد منه الـ Services وموديول
            // المخازن
            final String username = "sa"; // هنا تستخرج الاسم الفعلي من التوكن مستقبلاً
            requestContext.setSecurityContext(new jakarta.ws.rs.core.SecurityContext() {
                @Override
                public java.security.Principal getUserPrincipal() {
                    return () -> username;
                }

                @Override
                public boolean isUserInRole(String role) {
                    return userRoles.contains(role);
                }

                @Override
                public boolean isSecure() {
                    return requestContext.getSecurityContext().isSecure();
                }

                @Override
                public String getAuthenticationScheme() {
                    return "Bearer";
                }
            });

        } catch (Exception e) {
            // استخدام الـ JSON الموحد حتى في حالة حدوث استثناء أو تزوير في التوكن
            abortWithJson(requestContext, Response.Status.UNAUTHORIZED,
                    "التوكن الممرر غير صالح أو تم التلاعب به.");
        }
    }
    /*
     * private boolean isValidToken(String token) {
     * // هنا تضع منطق التحقق من الـ Token (مثلاً JWT أو API Key مسجل للوكيل الذكي)
     * return "joud-smart-agent-secret-key".equals(token); // مثال بسيط جداً
     * }
     */

    /*
     * private void abortWithUnauthorized(ContainerRequestContext requestContext) {
     * requestContext.abortWith(
     * Response.status(Response.Status.UNAUTHORIZED)
     * .header(HttpHeaders.WWW_AUTHENTICATE, AUTHENTICATION_SCHEME + " realm=\"" +
     * REALM + "\"")
     * .entity("{\"error\": \"غير مصرح لك بالوصول، يرجى تقديم توكن صالح\"}")
     * .build()
     * );
     * }
     */

    private boolean checkUserRoles(List<String> userRoles, String[] allowedRoles) {
        return Arrays.stream(allowedRoles).anyMatch(userRoles::contains);
    }

    private List<String> extractRolesFromToken(String token) {
        return List.of("SALES_MAN"); // تجريبي حالياً
    }
}