package com.ruinhome.contract;

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
@Table(name = "contract_fee_price",
        uniqueConstraints = @UniqueConstraint(name = "uq_contract_fee_price",
                columnNames = {"contract_id", "fee_code"}))
@Getter
@Setter
@NoArgsConstructor
public class ContractFeePrice extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Column(name = "fee_code", nullable = false, length = 30)
    private String feeCode;

    @Column(name = "price", nullable = false)
    private Long price;
}
