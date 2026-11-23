package com.joud.exception;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import com.joud.security.dto.ApiError;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        // تجميع كل الحقول التي فشل التحقق منها في رسالة واحدة
        String violations = exception.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "));

        ApiError apiError = new ApiError(
                LocalDateTime.now(),
                Response.Status.BAD_REQUEST.getStatusCode(),
                Response.Status.BAD_REQUEST.getReasonPhrase(),
                "خطأ في البيانات المرسلة: " + violations,
                uriInfo != null ? uriInfo.getPath() : "unknown");

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(apiError)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}