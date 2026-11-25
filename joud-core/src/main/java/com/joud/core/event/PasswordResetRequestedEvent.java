package com.joud.core.event;

public record PasswordResetRequestedEvent(String email, String rawToken) {}