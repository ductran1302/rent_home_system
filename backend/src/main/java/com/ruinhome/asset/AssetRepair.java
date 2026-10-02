package com.ruinhome.asset;

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

import java.time.LocalDate;

@Entity
@Table(name = "asset_repair")
@Getter
@Setter
@NoArgsConstructor
public class AssetRepair extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(name = "reported_at", nullable = false)
    private LocalDate reportedAt;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "cost", nullable = false)
    private Long cost = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AssetRepairStatus status = AssetRepairStatus.PENDING;

    @Column(name = "done_at")
    private LocalDate doneAt;

    @Column(name = "note", length = 500)
    private String note;
}
