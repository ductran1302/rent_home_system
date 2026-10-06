package com.ruinhome.billing;

import com.ruinhome.common.BaseEntity;
import com.ruinhome.room.Room;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoice",
        uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "period"}))
@Getter
@Setter
@NoArgsConstructor
public class Invoice extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "period", nullable = false, length = 7)
    private String period;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount = 0L;

    @Column(name = "paid_amount", nullable = false)
    private Long paidAmount = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "room_price_note", length = 500)
    private String roomPriceNote;

    @Column(name = "pre_elect_reading")
    private BigDecimal preElectReading;

    @Column(name = "current_elect_reading")
    private BigDecimal currentElectReading;

    @Column(name = "pre_water_reading")
    private BigDecimal preWaterReading;

    @Column(name = "current_water_reading")
    private BigDecimal currentWaterReading;

    @OneToMany(mappedBy = "invoice", cascade = jakarta.persistence.CascadeType.ALL,
            orphanRemoval = true)
    private List<InvoiceLine> lines = new ArrayList<>();
}
