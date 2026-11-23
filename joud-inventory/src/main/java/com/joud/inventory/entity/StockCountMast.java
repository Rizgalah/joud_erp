package com.joud.inventory.entity;

import jakarta.persistence.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// ══════════════════════════════════════════════════════════════
// StockCountMast — رأس جلسة الجرد
// ══════════════════════════════════════════════════════════════
@Entity
@Table(name = "STOCK_COUNT_MAST")
public class StockCountMast implements Serializable {

    public enum CountStatus {
        D, I, C, P
    } // Draft, InProgress, Complete, Posted

    @Id
    @Column(name = "COUNT_I_ID")
    private Long id;

    @Column(name = "AGENT_I_ID", nullable = false)
    private Long agentId;

    @Column(name = "WARE_I_ID")
    private Long warehouseId;

    @Column(name = "COUNT_C_DESC", nullable = false, length = 200)
    private String description;

    @Column(name = "COUNT_D_DATE", nullable = false)
    private LocalDate countDate = LocalDate.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "COUNT_C_STS", length = 1)
    private CountStatus status = CountStatus.D;

    @Column(name = "COUNT_C_SCOPE", length = 1)
    private String scope = "A"; // A=All, C=Category, S=Single

    @Column(name = "CATEG_I_ID")
    private Long categoryId;

    @Column(name = "COUNT_F_DIFF_VAL", precision = 23, scale = 5)
    private BigDecimal diffValue = BigDecimal.ZERO;

    @Column(name = "COUNT_I_ITEMS")
    private Integer itemCount = 0;

    @Column(name = "APPROVED_BY")
    private Long approvedBy;

    @Column(name = "APPROVED_AT")
    private LocalDate approvedAt;

    @Column(name = "CREATED_BY")
    private Long createdBy;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt = LocalDateTime.now();
    // @OneToMany(mappedBy = "mast", cascade = CascadeType.ALL, fetch =
    // FetchType.LAZY)
    @OneToMany(mappedBy = "countId", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<StockCountLine> lines = new ArrayList<>();

    public boolean isDraft() {
        return CountStatus.D == status;
    }

    public boolean isComplete() {
        return CountStatus.C == status;
    }

    public boolean isPosted() {
        return CountStatus.P == status;
    }

    // Getters & Setters
    public Long getId() {
        return id;
    }

    public void setId(Long v) {
        this.id = v;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long v) {
        this.agentId = v;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long v) {
        this.warehouseId = v;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String v) {
        this.description = v;
    }

    public LocalDate getCountDate() {
        return countDate;
    }

    public void setCountDate(LocalDate v) {
        this.countDate = v;
    }

    public CountStatus getStatus() {
        return status;
    }

    public void setStatus(CountStatus v) {
        this.status = v;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String v) {
        this.scope = v;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long v) {
        this.categoryId = v;
    }

    public BigDecimal getDiffValue() {
        return diffValue;
    }

    public void setDiffValue(BigDecimal v) {
        this.diffValue = v;
    }

    public Integer getItemCount() {
        return itemCount;
    }

    public void setItemCount(Integer v) {
        this.itemCount = v;
    }

    public Long getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(Long v) {
        this.approvedBy = v;
    }

    public LocalDate getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDate v) {
        this.approvedAt = v;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long v) {
        this.createdBy = v;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<StockCountLine> getLines() {
        return lines;
    }

    public void setLines(List<StockCountLine> v) {
        this.lines = v;
    }
}
