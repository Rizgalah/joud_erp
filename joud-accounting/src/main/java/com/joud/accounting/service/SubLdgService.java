package com.joud.accounting.service;

import com.joud.accounting.entity.GeneralLedgerMaster;
import com.joud.core.service.BaseCrudService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class SubLdgService extends BaseCrudService<GeneralLedgerMaster, Long> {

    @Inject
    public SubLdgService() {
        super(GeneralLedgerMaster.class);
    }

    /**
     * مثال لاستعلام مخصص غير موجود في السلف: جلب حسابات وكيل معين
     */
    public List<GeneralLedgerMaster> findByAgentCode(String agentCode) {
        return db.execute(em -> em.createQuery(
                "SELECT g FROM GeneralLedgerMaster g WHERE g.agent.code = :agentCode", GeneralLedgerMaster.class)
                .setParameter("agentCode", agentCode)
                .getResultList());
    }

    // 💡 هنا يمكنك كتابة أي بزنس مالي خاص فقط (مثلاً قيود معينة قبل الحفظ)
    // وإلا فكود الـ CRUD موروث وجاهز بنسبة 100%!

    // --- مثال: دالة لحفظ قيد خاص بالعميل ---
    public void saveCustomerTransaction(String customerCode, double amount) {

        // 1) نفتح معاملة (Transaction) يدوياً
        db.executeInTransaction(em -> {

            // 2) نجد حساب العميل (SubLedger / Agent)
            GeneralLedgerMaster customerAccount = findByAgentCode(customerCode)
                    .stream().findFirst().orElse(null);

            if (customerAccount == null) {
                throw new RuntimeException("لم يتم العثور على حساب العميل: " + customerCode);
            }

            // 3) إنشاء الحركة (المدين أو الدائن حسب منطقك)

            // 4) الحفظ

        });
    }
}
