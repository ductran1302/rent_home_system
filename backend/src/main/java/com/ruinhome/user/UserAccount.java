package com.ruinhome.user;

import com.ruinhome.common.BaseEntity;
import com.ruinhome.person.Person;
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
@Table(name = "user_account")
@Getter
@Setter
@NoArgsConstructor
public class UserAccount extends BaseEntity {

    @Column(name = "username", nullable = false, length = 100, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Person person;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "manager_start_date")
    private LocalDate managerStartDate;

    @Column(name = "manager_end_date")
    private LocalDate managerEndDate;

    @Column(name = "area_admin", length = 100)
    private String areaAdmin;

    @Column(name = "is_root", nullable = false)
    private boolean root;

    @Column(name = "bank_account", length = 30)
    private String bankAccount;
}
