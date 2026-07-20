package com.joud.core.security.service;

import com.joud.core.security.model.User;
import com.joud.core.security.service.AuthService.LoginResult;
import com.joud.core.security.session.SessionManager;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @InjectMocks
    private AuthService authService; // الكلاس المراد اختباره

    @Mock
    private EntityManager em; // محاكاة الاتصال لعدم الاعتماد على قاعدة البيانات هنا

    @Mock
    private TypedQuery<User> typedQuery;
    @Mock
    private SessionManager sessionManager; // 2. أضف هذا السطر هنا لمحاكاة مدير الجلسات

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("اختبار تسجيل الدخول بنجاح للمستخدم sa")
    void testLoginSuccess() {
        // 1. تجهيز البيانات الافتراضية للمحاكاة (Mocking Setup)
        String rawPassword = "admin123";
        String hashedPassword = at.favre.lib.crypto.bcrypt.BCrypt.withDefaults()
                .hashToString(12, rawPassword.toCharArray());
        User mockUser = new User();
        mockUser.setUsername("sa");
        mockUser.setPasswordHash(hashedPassword); // نضع الـ Hash هنا وليس النص الواضح
        mockUser.setStatus("A"); // تأكد إن كان الكود يشترط أن يكون الحساب نش

        when(em.createNamedQuery(anyString(), eq(User.class))).thenReturn(typedQuery);
        when(typedQuery.setParameter(anyString(), any())).thenReturn(typedQuery);
        when(typedQuery.getSingleResult()).thenReturn(mockUser);

        // 2. استدعاء الميثود البرمجية الفعليّة
        LoginResult loginResult = authService.authenticate("sa", rawPassword);

        // 3. التحقق من النتيجة (Assertions)
        if (loginResult.name() != "SUCCESS") {
            System.out.println("يجب أن ينجح تسجيل الدخول عند إدخال بيانات صحيحة" + loginResult.name());
        }
        assertEquals("SUCCESS", loginResult.name(), "login must be successful if username and password are correct");
        assertTrue(loginResult.name().equals("SUCCESS"),
                "login must be successful if username and password are correct");
    }

    @Test
    @DisplayName("اختبار فشل تسجيل الدخول عند إدخال كلمة مرور خاطئة")
    void testLoginFailureWrongPassword() {
        User mockUser = new User();
        mockUser.setUsername("sa");
        mockUser.setPasswordHash("admin123");
        mockUser.setStatus("A");
        mockUser.setPasswordHash(
                at.favre.lib.crypto.bcrypt.BCrypt.withDefaults().hashToString(12, "admin123".toCharArray()));
        when(em.createNamedQuery(anyString(), eq(User.class))).thenReturn(typedQuery);

        when(typedQuery.setParameter(anyString(), any())).thenReturn(typedQuery);
        when(typedQuery.getSingleResult()).thenReturn(mockUser);

        // محاولة الدخول بكلمة مرور خاطئة
        LoginResult loginResult = authService.authenticate("sa", "wrong_password");

        assertFalse(loginResult.name().equals("SUCCESS"),
                "login must be failed if username and password are not correct");
    }
}