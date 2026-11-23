package com.joud.inventory.bean;

import com.joud.inventory.entity.*;
import com.joud.inventory.service.InventoryService;
import com.joud.core.security.filter.AuthFilter;
import com.joud.core.security.session.UserSession;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

// ══════════════════════════════════════════════════════════════
// ProductBean — إدارة الأصناف
// ══════════════════════════════════════════════════════════════
@Named("prodBean")
@ViewScoped
public class ProductBean implements Serializable {

    private List<Product> products;
    private List<Product> filtered;
    private Product selected = new Product();
    private boolean editMode = false;
    private boolean showDialog = false;

    // فلاتر
    private Long filterCategoryId;
    private String filterStatus = "A";
    private String searchQ;
    private boolean showBelowMin = false;

    // إحصاءات
    private long totalActive;
    private long belowMinCount;
    private long pendingAlerts;
    private BigDecimal totalValue;

    // حركة يدوية
    private Long moveProductId;
    private String moveType = "ADJ";
    private BigDecimal moveQty = BigDecimal.ZERO;
    private BigDecimal moveCost = BigDecimal.ZERO;
    private String moveNotes;
    private Long moveWarehouseId;
    private boolean showMoveDialog = false;

    // قوائم
    private List<Object[]> categories;
    private List<Object[]> warehouses;
    private List<Object[]> units;
    private List<Object[]> suppliers;
    private List<Object[]> currencies;

    private Long agentId;

    @Inject
    private InventoryService invService;
    @PersistenceContext
    private EntityManager em;

    @PostConstruct
    public void init() {
        UserSession u = getCurrentUser();
        if (u != null)
            agentId = u.getAgentId();
        loadLookups();
        loadProducts();
        loadStats();
    }

    // ─── تحميل ─────────────────────────────────────
    public void loadProducts() {
        try {
            if (showBelowMin) {
                products = em.createNamedQuery("Product.findBelowMin", Product.class)
                        .getResultList();
                return;
            }
            if (searchQ != null && !searchQ.isBlank()) {
                products = em.createNamedQuery("Product.search", Product.class)
                        .setParameter("q", searchQ).getResultList();
                return;
            }
            if (filterCategoryId != null) {
                products = em.createNamedQuery("Product.findByCategory", Product.class)
                        .setParameter("catId", filterCategoryId).getResultList();
                return;
            }
            products = em.createNamedQuery("Product.findActive", Product.class)
                    .getResultList();
        } catch (Exception e) {
            products = List.of();
        }
    }

    private void loadStats() {
        totalActive = invService.countActiveProducts(agentId);
        belowMinCount = invService.countBelowMin();
        pendingAlerts = invService.countPendingAlerts(agentId);
        totalValue = invService.totalStockValue(agentId);
    }

    // ─── CRUD ───────────────────────────────────────
    public void prepareNew() {
        selected = new Product();
        selected.setAgentId(agentId);
        selected.setStatus("A");
        selected.setTrackStock("Y");
        selected.setIsService("N");
        selected.setCurrentQty(BigDecimal.ZERO);
        selected.setMinQty(BigDecimal.ZERO);
        editMode = false;
        showDialog = true;
    }

    public void prepareEdit(Product p) {
        selected = em.find(Product.class, p.getId());
        editMode = true;
        showDialog = true;
    }

    @Transactional
    public void save() {
        try {
            if (editMode) {
                em.merge(selected);
                addInfo("تم التحديث: " + selected.getName());
            } else {
                selected.setId(generateId("products"));
                selected.setCode(nextCode());
                em.persist(selected);
                addInfo("تمت الإضافة: " + selected.getName());
            }
            showDialog = false;
            loadProducts();
            loadStats();
        } catch (Exception e) {
            addError("خطأ: " + e.getMessage());
        }
    }

    @Transactional
    public void deactivate(Product p) {
        Product e = em.find(Product.class, p.getId());
        if (e != null) {
            e.setStatus("D");
            em.merge(e);
        }
        addInfo("تم إيقاف: " + p.getName());
        loadProducts();
        loadStats();
    }

    // ─── حركة يدوية ────────────────────────────────
    public void prepareMoveDialog(Product p) {
        moveProductId = p.getId();
        moveCost = p.getAvgCost();
        moveWarehouseId = p.getWarehouseId();
        moveQty = BigDecimal.ZERO;
        moveNotes = null;
        moveType = "ADJ";
        showMoveDialog = true;
    }

    public void recordMovement() {
        try {
            UserSession u = getCurrentUser();
            invService.recordManualMovement(
                    moveProductId, agentId, moveWarehouseId,
                    moveType, moveQty, moveCost, null,
                    moveNotes, u != null ? u.getUserId() : 0L);
            addInfo("تم تسجيل الحركة");
            showMoveDialog = false;
            loadProducts();
            loadStats();
        } catch (InventoryService.InventoryException e) {
            addError(e.getMessage());
        }
    }

    // ─── فلاتر ──────────────────────────────────────
    public void toggleBelowMin() {
        showBelowMin = !showBelowMin;
        loadProducts();
    }

    public void search() {
        loadProducts();
    }

    public void resetFilters() {
        searchQ = null;
        filterCategoryId = null;
        showBelowMin = false;
        loadProducts();
    }

    // ─── Helpers ───────────────────────────────────
    @SuppressWarnings("unchecked")
    private void loadLookups() {
        try {
            categories = em.createNativeQuery(
                    "SELECT categ_i_id,categ_c_name FROM categories ORDER BY categ_c_name").getResultList();
            warehouses = em.createNativeQuery(
                    "SELECT ware_i_id,ware_c_name FROM warehouses WHERE is_active=1 ORDER BY ware_c_name")
                    .getResultList();
            units = em.createNativeQuery(
                    "SELECT unit_i_id,unit_c_name,unit_short_name FROM units ORDER BY unit_c_name").getResultList();
            suppliers = em.createNativeQuery(
                    "SELECT parties_i_id,parties_c_name FROM parties " +
                            "WHERE parties_c_type IN('supplier','both') AND is_active=1 ORDER BY parties_c_name")
                    .getResultList();
            currencies = em.createNativeQuery(
                    "SELECT CURRENCY_I_ID,CURRENCY_C_DESC,CURRENCY_C_SYMBOL FROM CURRENCY_MAST ORDER BY CURRENCY_C_DESC")
                    .getResultList();
        } catch (Exception e) {
            categories = warehouses = units = suppliers = currencies = List.of();
        }
    }

    public String getCategoryName(Long id) {
        if (id == null || categories == null)
            return "—";
        return categories.stream().filter(r -> id.equals(((Number) r[0]).longValue()))
                .map(r -> r[1].toString()).findFirst().orElse("—");
    }

    public String getWarehouseName(Long id) {
        if (id == null || warehouses == null)
            return "—";
        return warehouses.stream().filter(r -> id.equals(((Number) r[0]).longValue()))
                .map(r -> r[1].toString()).findFirst().orElse("—");
    }

    public String getUnitName(Long id) {
        if (id == null || units == null)
            return "—";
        return units.stream().filter(r -> id.equals(((Number) r[0]).longValue()))
                .map(r -> r[2].toString()).findFirst().orElse("—");
    }

    private String nextCode() {
        try {
            Object r = em.createNativeQuery(
                    "SELECT COUNT(*)+1 FROM products").getSingleResult();
            return String.format("PRD-%05d", ((Number) r).longValue());
        } catch (Exception e) {
            return "PRD-00001";
        }
    }

    private Long generateId(String t) {
        try {
            return ((Number) em.createNativeQuery(
                    "SELECT ID_I_GEN FROM KEY_GENERATION WHERE TABLE_C_NAME=:t")
                    .setParameter("t", t).getSingleResult()).longValue();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private UserSession getCurrentUser() {
        return (UserSession) FacesContext.getCurrentInstance()
                .getExternalContext().getRequestMap().get(AuthFilter.SESSION_ATTR);
    }

    private void addInfo(String m) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, m, null));
    }

    private void addError(String m) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, m, null));
    }

    // Getters & Setters
    public List<Product> getProducts() {
        return products;
    }

    public List<Product> getFiltered() {
        return filtered;
    }

    public void setFiltered(List<Product> v) {
        this.filtered = v;
    }

    public Product getSelected() {
        return selected;
    }

    public void setSelected(Product v) {
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

    public Long getFilterCategoryId() {
        return filterCategoryId;
    }

    public void setFilterCategoryId(Long v) {
        this.filterCategoryId = v;
    }

    public String getFilterStatus() {
        return filterStatus;
    }

    public void setFilterStatus(String v) {
        this.filterStatus = v;
    }

    public String getSearchQ() {
        return searchQ;
    }

    public void setSearchQ(String v) {
        this.searchQ = v;
    }

    public boolean isShowBelowMin() {
        return showBelowMin;
    }

    public long getTotalActive() {
        return totalActive;
    }

    public long getBelowMinCount() {
        return belowMinCount;
    }

    public long getPendingAlerts() {
        return pendingAlerts;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public Long getMoveProductId() {
        return moveProductId;
    }

    public void setMoveProductId(Long v) {
        this.moveProductId = v;
    }

    public String getMoveType() {
        return moveType;
    }

    public void setMoveType(String v) {
        this.moveType = v;
    }

    public BigDecimal getMoveQty() {
        return moveQty;
    }

    public void setMoveQty(BigDecimal v) {
        this.moveQty = v;
    }

    public BigDecimal getMoveCost() {
        return moveCost;
    }

    public void setMoveCost(BigDecimal v) {
        this.moveCost = v;
    }

    public String getMoveNotes() {
        return moveNotes;
    }

    public void setMoveNotes(String v) {
        this.moveNotes = v;
    }

    public Long getMoveWarehouseId() {
        return moveWarehouseId;
    }

    public void setMoveWarehouseId(Long v) {
        this.moveWarehouseId = v;
    }

    public boolean isShowMoveDialog() {
        return showMoveDialog;
    }

    public void setShowMoveDialog(boolean v) {
        this.showMoveDialog = v;
    }

    public List<Object[]> getCategories() {
        return categories;
    }

    public List<Object[]> getWarehouses() {
        return warehouses;
    }

    public List<Object[]> getUnits() {
        return units;
    }

    public List<Object[]> getSuppliers() {
        return suppliers;
    }

    public List<Object[]> getCurrencies() {
        return currencies;
    }
}
