package com.ruinhome.billing;

import com.ruinhome.common.BaseEntity;
import com.ruinhome.room.Room;
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
@Table(name = "meter_reading",
        uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "fee_type_id", "period"}))
@Getter
@Setter
@NoArgsConstructor
public class MeterReading extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fee_type_id", nullable = false)
    private FeeType feeType;

    @Column(name = "period", nullable = false, length = 7)
    private String period;

    @Column(name = "reading", nullable = false, precision = 12, scale = 2)
    private BigDecimal reading;

    @Column(name = "note", length = 500)
    private String note;
}
