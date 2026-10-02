package com.ruinhome.contract;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ContractRepository extends JpaRepository<Contract, Long> {

    Optional<Contract> findByIdAndRoomHouseId(Long id, Long houseId);

    List<Contract> findByRoomIdOrderByStartDateDesc(Long roomId);

    List<Contract> findByHolderIdOrderByStartDateDesc(Long holderId);

    Optional<Contract> findByRoomIdAndStatus(Long roomId, ContractStatus status);

    @Query("""
            select c from Contract c
            where c.room.id = :roomId
              and c.startDate <= :periodEnd
              and c.endDate >= :periodStart
            order by c.startDate desc
            """)
    Optional<Contract> findFirstCoveringPeriod(@Param("roomId") Long roomId,
                                               @Param("periodStart") LocalDate periodStart,
                                               @Param("periodEnd") LocalDate periodEnd);

    boolean existsByRoomIdAndStatus(Long roomId, ContractStatus status);

    @Modifying
    @Query("update Contract c set c.status = :to, c.updatedAt = CURRENT_TIMESTAMP "
            + "where c.status = :from and c.endDate < :today")
    int expireOverdue(@Param("from") ContractStatus from, @Param("to") ContractStatus to, @Param("today") LocalDate today);

    long countByStatus(ContractStatus status);

    @Query("select c.room.id from Contract c where c.status = com.ruinhome.contract.ContractStatus.ACTIVE and c.room.house.id = :houseId")
    Set<Long> findActiveContractRoomIdsByHouse(@Param("houseId") Long houseId);

    @Query("""
            select c from Contract c
              join fetch c.room r
              join fetch r.house h
              join fetch c.holder
            where (:houseId is null or h.id = :houseId)
              and (:roomId is null or r.id = :roomId)
              and (:status is null or c.status = :status)
              and (:ownerScope is null or h.owner.id = :ownerScope or h.manager.id = :ownerScope)
              and (:areaScope is null or h.areaAdmin = :areaScope)
              and (:userScope is null or c.holder.id = :userScope
                   or exists (select t.id from c.tenants t where t.id = :userScope))
            """)
    Page<Contract> search(@Param("houseId") Long houseId,
                          @Param("roomId") Long roomId,
                          @Param("status") ContractStatus status,
                          @Param("ownerScope") Long ownerScope,
                          @Param("userScope") Long userScope,
                          @Param("areaScope") String areaScope,
                          Pageable pageable);

    @Query("""
            select c from Contract c
              join fetch c.room r
              join fetch r.house h
            where c.status = com.ruinhome.contract.ContractStatus.ACTIVE
              and c.startDate <= :end
              and c.endDate >= :start
              and (:ownerScope is null or h.owner.id = :ownerScope or h.manager.id = :ownerScope)
              and (:areaScope is null or h.areaAdmin = :areaScope)
            """)
    List<Contract> findActiveForPeriod(@Param("start") LocalDate start,
                                       @Param("end") LocalDate end,
                                       @Param("ownerScope") Long ownerScope,
                                       @Param("areaScope") String areaScope);

    @Query("""
            select count(c) > 0 from Contract c
            where c.room.id = :roomId
              and (c.holder.id = :personId
                   or exists (select t from c.tenants t where t.id = :personId))
            """)
    boolean existsForRoomAndPerson(@Param("roomId") Long roomId, @Param("personId") Long personId);

    @Query("""
            select count(c) from Contract c join c.room r join r.house h
            where c.status = com.ruinhome.contract.ContractStatus.ACTIVE
              and (:ownerScope is null or h.owner.id = :ownerScope or h.manager.id = :ownerScope)
              and (:areaScope is null or h.areaAdmin = :areaScope)
            """)
    long countActiveForScope(@Param("ownerScope") Long ownerScope,
                             @Param("areaScope") String areaScope);

    @Query("""
            select count(c) from Contract c
            where c.status = com.ruinhome.contract.ContractStatus.ACTIVE
              and (c.holder.id = :personId
                   or exists (select t from c.tenants t where t.id = :personId))
            """)
    long countActiveForPerson(@Param("personId") Long personId);
}
