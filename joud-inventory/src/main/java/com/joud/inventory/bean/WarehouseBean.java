package com.joud.inventory.bean;

import com.joud.inventory.entity.Warehouse;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

// ══════════════════════════════════════════════════════════════
// WarehouseBean — إدارة المستودعات
// ══════════════════════════════════════════════════════════════
@Named("wareBean")
@ViewScoped
public class WarehouseBean implements Serializable {

    private List<Warehouse> warehouses;
    private Warehouse selected = new Warehouse();
    private boolean editMode = false;
    private boolean showDialog = false;
    private long activeCount;

    @PersistenceContext
    private EntityManager em;

    @PostConstruct
    public void init() {
        loadWarehouses();
    }

    public void loadWarehouses() {
        try {
            warehouses = em.createQuery(
                    "SELECT w FROM Warehouse w ORDER BY w.active DESC, w.name ASC",
                    Warehouse.class).getResultList();
            activeCount = warehouses.stream().filter(Warehouse::isActive).count();
        } catch (Exception e) {
            warehouses = List.of();
        }
    }

    public void prepareNew() {
        selected = new Warehouse();
        selected.setActive(1);
        selected.setType("main");
        editMode = false;
        showDialog = true;
    }

    public void prepareEdit(Warehouse w) {
        selected = em.find(Warehouse.class, w.getId());
        editMode = true;
        showDialog = true;
    }

    @Transactional
    public void save() {
        try {
            if (editMode) {
                em.merge(selected);
                addInfo("تم تحديث: " + selected.getName());
            } else {
                selected.setId(generateId());
                selected.setCode(nextCode());
                em.persist(selected);
                addInfo("تمت الإضافة: " + selected.getName());
            }
            showDialog = false;
            loadWarehouses();
        } catch (Exception e) {
            addError("خطأ: " + e.getMessage());
        }
    }

    @Transactional
    public void toggleActive(Warehouse w) {
        Warehouse e = em.find(Warehouse.class, w.getId());
        if (e != null) {
            e.setActive(e.getActive() == 1 ? 0 : 1);
            em.merge(e);
            addInfo(e.getActive() == 1 ? "تم تفعيل: " + e.getName() : "تم إيقاف: " + e.getName());
            loadWarehouses();
        }
    }

    /** عدد الأصناف في مستودع معين */
    public long getProductCount(Long wareId) {
        try {
            return ((Number) em.createNativeQuery(
                    "SELECT COUNT(*) FROM products WHERE WARE_I_ID=:w AND prod_c_status='A'")
                    .setParameter("w", wareId).getSingleResult()).longValue();
        } catch (Exception e) {
            return 0;
        }
    }

    /** قيمة المخزون في مستودع معين */
    public BigDecimal getStockValue(Long wareId) {
        try {
            Object r = em.createNativeQuery(
                    "SELECT NVL(SUM(current_quantity*prod_f_avg_cost),0) " +
                            "FROM products WHERE WARE_I_ID=:w AND prod_c_status='A'")
                    .setParameter("w", wareId).getSingleResult();
            return r != null ? new BigDecimal(r.toString()) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private String nextCode() {
        try {
            long n = ((Number) em.createNativeQuery(
                    "SELECT COUNT(*)+1 FROM warehouses").getSingleResult()).longValue();
            return String.format("WH-%03d", n);
        } catch (Exception e) {
            return "WH-001";
        }
    }

    private Long generateId() {
        try {
            return ((Number) em.createNativeQuery(
                    "SELECT ID_I_GEN FROM KEY_GENERATION WHERE TABLE_C_NAME='warehouses'")
                    .getSingleResult()).longValue();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private void addInfo(String m) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO, m, null));
    }

    private void addError(String m) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, m, null));
    }

    // Getters & Setters
    public List<Warehouse> getWarehouses() {
        return warehouses;
    }

    public Warehouse getSelected() {
        return selected;
    }

    public void setSelected(Warehouse v) {
        this.selected = v;
    }

    public boolean isEditMode() {
        return editMode;
    }

    public boolean isShowDialog() {
        return showDialog;
    }

    public void setShowDialog(boolean v) {
        this.showDialog = v;
    }

    public long getActiveCount() {
        return activeCount;
    }
}
