package com.joud.core.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import java.io.Serializable;

@MappedSuperclass
public abstract class BaseEntity implements Serializable {

    @Id
    @GeneratedValue(generator = "joud-id-generator")
    @GenericGenerator(name = "joud-id-generator", type = com.joud.core.id.JoudIdGenerator.class // 👈 الإشارة للكلاس
                                                                                                // المركزي الخاص بك
    )
    @Column(name = "ID", nullable = false)
    private Long id;

    // Getters and Setters الموحدة لكل النظام
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof BaseEntity))
            return false;
        BaseEntity that = (BaseEntity) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}