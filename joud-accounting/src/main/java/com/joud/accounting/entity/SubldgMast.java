package com.joud.accounting.entity;

import jakarta.persistence.*;
import lombok.*;
import com.joud.core.entity.BaseEntity;

@Entity
@Table(name = "subldg_mast")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class SubldgMast extends BaseEntity {

    // ربط مباشر كـ Long لتجاوز تعقيد العلاقات حالياً وتسريع العمل
    @Column(name = "ldg_i_id")
    private Long ldgId;

    @Column(name = "sub_c_ldgdesc", nullable = false, length = 100)
    private String subLdgDesc;

    @Column(name = "sub_c_type", nullable = false, length = 1)
    private String subType;

    @Column(name = "sub_c_revalid", nullable = false, length = 1)
    private String subRevalid;

    @Column(name = "group_i_id")
    private Long groupId;

    @Column(name = "act_c_type", nullable = false, length = 10)
    private String actType;

    @Column(name = "profit_c_calc", length = 1)
    private String profitCalc;

    @Column(name = "act_c_class", length = 10)
    private String actClass;

    @Column(name = "act_c_nature", length = 10)
    private String actNature;

    @Column(name = "bud_c_flag", length = 1)
    private String budFlag;

    @Column(name = "user_i_id")
    private Long userId;
}
