package com.ruinhome.billing;

import com.ruinhome.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "fee_type")
@Getter
@Setter
@NoArgsConstructor
public class FeeType extends BaseEntity {

    @Column(name = "code", nullable = false, length = 30, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
