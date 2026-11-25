package com.joud.notification.listener;

import com.joud.core.event.PasswordResetRequestedEvent;
import com.joud.notification.service.PasswordResetEmailService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class PasswordResetRequestedObserver {

    @Inject
    private PasswordResetEmailService emailService;

    public void onPasswordResetRequested(@Observes PasswordResetRequestedEvent event) {
        emailService.sendPasswordResetAsync(event.email(), event.rawToken());
    }
}