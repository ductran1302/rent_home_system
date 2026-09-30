package com.ruinhome.room;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByHouseIdAndActiveTrueOrderByRoomNumberAsc(Long houseId);

    boolean existsByHouseIdAndRoomNumber(Long houseId, String roomNumber);

    long countByHouseIdAndActiveTrue(Long houseId);

    Optional<Room> findByIdAndActiveTrue(Long id);

    @Query("select r from Room r where r.active = true and r.house.id = :houseId and r.id not in (select c.room.id from Contract c where c.status = com.ruinhome.contract.ContractStatus.ACTIVE) order by r.roomNumber")
    List<Room> findVacantByHouse(@Param("houseId") Long houseId);

    long countByActiveTrue();

    @Query("""
            select count(r) from Room r join r.house h
            where r.active = true
              and (:ownerScope is null or h.owner.id = :ownerScope or h.manager.id = :ownerScope)
            """)
    long countActiveForScope(@Param("ownerScope") Long ownerScope);

    @Query("""
            select count(r) from Room r join r.house h
            where r.active = true
              and (:ownerScope is null or h.owner.id = :ownerScope or h.manager.id = :ownerScope)
              and r.id not in (select c.room.id from Contract c
                               where c.status = com.ruinhome.contract.ContractStatus.ACTIVE)
            """)
    long countVacantForScope(@Param("ownerScope") Long ownerScope);
}
