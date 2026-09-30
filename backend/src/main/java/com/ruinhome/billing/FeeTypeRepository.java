package com.ruinhome.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeeTypeRepository extends JpaRepository<FeeType, Long> {

    List<FeeType> findByActiveTrueOrderByCodeAsc();

    Optional<FeeType> findByCode(String code);
}
