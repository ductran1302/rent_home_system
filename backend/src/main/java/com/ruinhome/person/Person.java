package com.ruinhome.person;

import com.ruinhome.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "person")
@Getter
@Setter
@NoArgsConstructor
public class Person extends BaseEntity {

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "id_number", length = 20)
    private String idNumber;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Override
    protected void onCreate() {
        super.onCreate();
        setUpdatedAt(null);
    }
}
