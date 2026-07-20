package com.joud.notification.listener;

import com.joud.core.event.EntityChangeEvent;
import com.joud.notification.service.EmailService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

@ApplicationScoped
public class GlobalEntityNotificationObserver {

    @Inject
    private EmailService emailService;

    public void onEntityChanged(@ObservesAsync EntityChangeEvent event) {
        Object entity = event.getEntity();

        // التفاعل التلقائي بناءً على نوع الـ Entity التي تم حفظها في قاعدة البيانات!
        if (event.getActionType() == EntityChangeEvent.ActionType.CREATED) {
            
            // إذا تم إنشاء قيد مالي جديد مثلاً
            if (entity.getClass().getSimpleName().equals("JournalEntry")) {
                emailService.sendEmailAsync("manager@company.com", 
                        "تم تسجيل قيد جديد", 
                        "تم إنشاء قيد محاسبي جديد في النظام.");
            }
            
            // إذا تم إنشاء إيداع استثماري جديد في مديول المضاربة
            else if (entity.getClass().getSimpleName().equals("InvestorDeposit")) {
                emailService.sendEmailAsync("investor@client.com", 
                        "تأكيد استلام إيداع", 
                        "شكراً لك، تم تسجيل الإيداع بنجاح.");
            }
        }
    }
}
