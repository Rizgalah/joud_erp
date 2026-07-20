package com.joud.accounting.api;

import com.joud.accounting.entity.GeneralLedgerMaster;
import com.joud.accounting.service.SubLdgService;
import com.joud.core.api.BaseCrudResource;
import com.joud.security.Secured;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Path;

@Path("/accounting/subldg")
@RequestScoped
@Secured("accounting.subldg.view") // حماية الشاشة بصلاحيات الأمان لديك
public class SubLdgResource extends BaseCrudResource<GeneralLedgerMaster, Long> {

    @Inject
    private SubLdgService service;

    @Override
    protected SubLdgService getService() {
        return service;
    }
    // انتهى الكود! لا يوجد تكرار، لا توجد ميثودز مكررة.
    // السلف تكفل بـ (GET, POST, PUT, DELETE)!
    // 💡 شاشتك الآن تدعم تلقائياً:
    // GET /accounting/subldg -> لجلب كل الحسابات المساعدة
    // GET /accounting/subldg/{id} -> لجلب حساب معين
    // POST /accounting/subldg -> لإضافة حساب جديد (مع توليد الـ ID ديناميكياً)
    // PUT /accounting/subldg/{id} -> لتعديل حساب
    // DELETE /accounting/subldg/{id} -> لحذف حساب
    // كل هذا كتبته في سطرين فقط!
    // لكن إذا أردت أن تضيف ميثودز مخصصة لشاشتك (مثلاً: البحث بالاسم، إغلاق
    // فترة،ترحيل) فهذا مكانه هنا:

    /*
     * @GET
     * 
     * @Path("search/{name}") // /accounting/subldg/search/محمد
     * public List<GeneralLedgerMaster> searchByName(@PathParam("name") String name)
     * {
     * return getService().findByAccountName(name);
     * }
     * 
     * // --- ميثود لحفظ قيد خاص بالعميل ---
     * 
     * @POST
     * 
     * @Path("{customerCode}/credit")
     * public Response addCustomerCredit(@PathParam("customerCode") String
     * customerCode, double amount) {
     * try {
     * service.saveCustomerTransaction(customerCode, amount);
     * return Response.ok("تم إضافة رصيد للعميل بنجاح").build();
     * } catch (Exception e) {
     * return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
     * }
     * }
     */
}
