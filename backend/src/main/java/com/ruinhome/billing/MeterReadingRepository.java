package com.ruinhome.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MeterReadingRepository extends JpaRepository<MeterReading, Long> {

    Optional<MeterReading> findByRoomIdAndFeeTypeIdAndPeriod(Long roomId, Long feeTypeId, String period);

    boolean existsByRoomIdAndFeeTypeIdAndPeriod(Long roomId, Long feeTypeId, String period);

    @Query("""
            select m from MeterReading m join fetch m.room r join fetch r.house h join fetch m.feeType
            where (:period is null or m.period = :period)
              and (:roomId is null or r.id = :roomId)
              and (:ownerScope is null or h.owner.id = :ownerScope or h.manager.id = :ownerScope)
              and (:areaScope is null or h.areaAdmin = :areaScope)
            order by m.period desc, r.roomNumber, m.feeType.code
            """)
    List<MeterReading> search(@Param("period") String period, @Param("roomId") Long roomId,
                              @Param("ownerScope") Long ownerScope,
                              @Param("areaScope") String areaScope);
}
