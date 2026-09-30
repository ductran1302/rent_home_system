package com.ruinhome.room;

import com.ruinhome.common.BaseEntity;
import com.ruinhome.house.House;
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

import java.math.BigDecimal;

@Entity
@Table(name = "room", uniqueConstraints = @UniqueConstraint(columnNames = {"house_id", "room_number"}))
@Getter
@Setter
@NoArgsConstructor
public class Room extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "house_id", nullable = false)
    private House house;

    @Column(name = "room_number", nullable = false, length = 20)
    private String roomNumber;

    @Column(name = "area_m2")
    private BigDecimal areaM2;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
