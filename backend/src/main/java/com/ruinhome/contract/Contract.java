package com.ruinhome.contract;

import com.ruinhome.common.BaseEntity;
import com.ruinhome.person.Person;
import com.ruinhome.room.Room;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "contract")
@Getter
@Setter
@NoArgsConstructor
public class Contract extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "holder_id", nullable = false)
    private Person holder;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "contract_tenant",
            joinColumns = @JoinColumn(name = "contract_id"),
            inverseJoinColumns = @JoinColumn(name = "person_id")
    )
    private Set<Person> tenants = new HashSet<>();

    @Column(name = "monthly_rent", nullable = false)
    private Long monthlyRent;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ContractStatus status = ContractStatus.ACTIVE;

    @Column(name = "note", length = 500)
    private String note;
}
