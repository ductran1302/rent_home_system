package com.ruinhome.asset;

import com.ruinhome.common.BaseEntity;
import com.ruinhome.room.Room;
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
@Table(name = "asset")
@Getter
@Setter
@NoArgsConstructor
public class Asset extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "code", nullable = false, length = 50, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "price", nullable = false)
    private Long price;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private AssetCategory category = AssetCategory.KHAC;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition", nullable = false, length = 30)
    private AssetCondition condition;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
