package com.joud.security.util;

import com.joud.security.dto.ApiError;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDateTime;

public class SecurityResponseUtil {

    public static Response createErrorResponse(Response.Status status, String message, String path) {
        ApiError error = new ApiError(LocalDateTime.now(), status.getStatusCode(), status.getReasonPhrase(), message,
                path);
        return Response.status(status).entity(error).type(MediaType.APPLICATION_JSON).build();
    }

    /**
     * بناء استجابة خطأ موحدة بتنسيق JSON (مطبقة لكل الـ filters في النظام).
     *
     * @param status      رمز الحالة (مثل UNAUTHORIZED أو FORBIDDEN)
     * @param userMessage رسالة الخطأ التي ستظهر للعميل
     * @param requestPath المسار الذي حاول المستخدم الوصول إليه
     */
    public static Response buildJsonError(Response.Status status,
            String userMessage,
            String requestPath) {
        ApiError apiError = new ApiError(
                LocalDateTime.now(),
                status.getStatusCode(),
                status.getReasonPhrase(), // نص الحالة (مثل "Unauthorized")
                userMessage,
                requestPath);

        return Response.status(status)
                .entity(apiError)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

}
