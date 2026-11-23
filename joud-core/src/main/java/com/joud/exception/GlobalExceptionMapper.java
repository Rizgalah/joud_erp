package com.joud.exception;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import com.joud.security.dto.ApiError;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

@Provider // وسم أساسي ليعرفه خادم جاكرتا تلقائياً
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    // إعداد كائن التسجيل لربطه مع ملف joud.log الخاص بنظامك
    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class.getName());

    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(Throwable exception) {

        // 1. تحديد الحالة الافتراضية (500 Internal Server Error للأخطاء البرمجية
        // العامة)
        Response.Status status = Response.Status.INTERNAL_SERVER_ERROR;
        String userMessage = "حدث خطأ داخلي في الخادم، تم تسجيل المشكلة وجاري التعامل معها.";

        // 2. معالجة خاصة لاستثناءات الـ REST القياسية (مثل 404 Not Found أو 405 Method
        // Not Allowed)
        // لكي لا تتحول كلها إلى كود 500 وتفقد معناها
        if (exception instanceof WebApplicationException) {
            WebApplicationException webAppException = (WebApplicationException) exception;
            Response originalResponse = webAppException.getResponse();

            // استخلاص الحالة الأصلية التي أطلقها النظام
            int statusCode = originalResponse.getStatus();
            status = Response.Status.fromStatusCode(statusCode);

            // الاحتفاظ بالرسالة الأصلية إذا كانت مخصصة
            if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
                userMessage = exception.getMessage();
            }
        } else {
            // 3. كتابة الخطأ البرمجي غير المتوقع في ملف joud.log بالتفصيل الكامل (Stack
            // Trace)
            // هذا الجزء للمطور فقط ولن يراه العميل الخارجي
            LOG.log(Level.SEVERE, "[joud.log] Unexpected System Error caught at path: "
                    + (uriInfo != null ? uriInfo.getPath() : "unknown"), exception);
        }

        // 4. بناء شاشة الخطأ الأنيقة والموحدة للـ Front-End
        ApiError apiError = new ApiError(
                LocalDateTime.now(),
                status.getStatusCode(),
                status.getReasonPhrase(),
                userMessage,
                uriInfo != null ? uriInfo.getPath() : "unknown");

        // 5. إرجاع الاستجابة الموحدة بصيغة JSON
        return Response
                .status(status)
                .entity(apiError)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}