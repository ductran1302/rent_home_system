package com.ruinhome.contract;

import com.ruinhome.asset.Asset;
import com.ruinhome.asset.AssetCondition;
import com.ruinhome.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "contract_asset")
@Getter
@Setter
@NoArgsConstructor
public class ContractAsset extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(name = "handover_condition", nullable = false, length = 30)
    private AssetCondition handoverCondition;

    @Enumerated(EnumType.STRING)
    @Column(name = "return_condition", length = 30)
    private AssetCondition returnCondition;

    @Column(name = "handover_note", length = 500)
    private String handoverNote;

    @Column(name = "returned_at")
    private LocalDateTime returnedAt;
}
