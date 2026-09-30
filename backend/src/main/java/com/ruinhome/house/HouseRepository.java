package com.ruinhome.house;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HouseRepository extends JpaRepository<House, Long> {

    List<House> findByActiveTrueOrderByCodeAsc();

    Optional<House> findByCodeAndActiveTrue(String code);

    Optional<House> findByIdAndActiveTrue(Long id);

    boolean existsByCode(String code);

    @Query("select h from House h where h.active = true and (h.owner.id = :personId or h.manager.id = :personId) order by h.code")
    List<House> findActiveByOwnerOrManager(@Param("personId") Long personId);

    long countByActiveTrue();

    @Query("select count(h) from House h where h.active = true and (:ownerScope is null or h.owner.id = :ownerScope or h.manager.id = :ownerScope)")
    long countActiveForScope(@Param("ownerScope") Long ownerScope);
}
