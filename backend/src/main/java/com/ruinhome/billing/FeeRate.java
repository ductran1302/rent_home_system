package com.ruinhome.billing;

import com.ruinhome.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "fee_rate",
        uniqueConstraints = @UniqueConstraint(columnNames = {"fee_type_id", "period"}))
@Getter
@Setter
@NoArgsConstructor
public class FeeRate extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fee_type_id", nullable = false)
    private FeeType feeType;

    @Column(name = "period", nullable = false, length = 7)
    private String period;

    @Column(name = "price", nullable = false)
    private Long price;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
