package com.joud.inventory.entity;

import jakarta.persistence.*;

// ══════════════════════════════════════════════════════════════
// Warehouse — المستودع
// ══════════════════════════════════════════════════════════════
@Entity
@Table(name = "warehouses")
public class Warehouse {

    @Id
    @Column(name = "ware_i_id")
    private Long id;

    @Column(name = "ware_c_code", unique = true, nullable = false, length = 100)
    private String code;

    @Column(name = "ware_c_name", nullable = false, length = 255)
    private String name;

    @Column(name = "ware_c_type", length = 20)
    private String type = "main"; // main | transit | return

    @Column(name = "ware_c_address", length = 500)
    private String address;

    @Column(name = "ware_c_manager", length = 255)
    private String manager;

    @Column(name = "ware_c_phone", length = 50)
    private String phone;

    @Column(name = "ware_c_location", length = 500)
    private String location;

    @Column(name = "ware_c_notes", length = 500)
    private String notes;

    @Column(name = "is_active")
    private Integer active = 1;

    @Column(name = "user_i_id")
    private Long userId;

    public boolean isActive() {
        return active != null && active == 1;
    }

    public String getTypeLabel() {
        return switch (type != null ? type : "main") {
            case "main" -> "رئيسي";
            case "transit" -> "عبور";
            case "return" -> "مردودات";
            default -> type;
        };
    }

    // Getters & Setters
    public Long getId() {
        return id;
    }

    public void setId(Long v) {
        this.id = v;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String v) {
        this.code = v;
    }

    public String getName() {
        return name;
    }

    public void setName(String v) {
        this.name = v;
    }

    public String getType() {
        return type;
    }

    public void setType(String v) {
        this.type = v;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String v) {
        this.address = v;
    }

    public String getManager() {
        return manager;
    }

    public void setManager(String v) {
        this.manager = v;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String v) {
        this.phone = v;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String v) {
        this.location = v;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String v) {
        this.notes = v;
    }

    public Integer getActive() {
        return active;
    }

    public void setActive(Integer v) {
        this.active = v;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long v) {
        this.userId = v;
    }
}
