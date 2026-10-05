package com.ruinhome.billing;

import com.ruinhome.room.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MeterReadingRepository extends JpaRepository<MeterReading, Long> {

    Optional<MeterReading> findByRoomIdAndFeeTypeIdAndPeriod(Long roomId, Long feeTypeId, String period);

    boolean existsByRoomIdAndFeeTypeIdAndPeriod(Long roomId, Long feeTypeId, String period);

    @Query("""
            select r from Room r
              join fetch r.house h
            where exists (select 1 from Contract c
                          where c.room = r
                            and c.status = com.ruinhome.contract.ContractStatus.ACTIVE
                            and c.startDate <= :periodEnd
                            and c.endDate >= :periodStart)
              and exists (select 1 from MeterReading m
                          where m.room = r
                            and m.period = :prevPeriod
                            and m.feeType.code = :feeCode)
              and not exists (select 1 from MeterReading m2
                          where m2.room = r
                            and m2.period = :period
                            and m2.feeType.code = :feeCode)
            """)
    List<Room> findRoomsMissingReading(@Param("period") String period,
                                       @Param("prevPeriod") String prevPeriod,
                                       @Param("feeCode") String feeCode,
                                       @Param("periodStart") LocalDate periodStart,
                                       @Param("periodEnd") LocalDate periodEnd);

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
