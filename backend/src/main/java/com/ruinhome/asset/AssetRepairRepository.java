package com.ruinhome.asset;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface AssetRepairRepository extends JpaRepository<AssetRepair, Long> {

    @EntityGraph(attributePaths = {"asset", "asset.room", "asset.room.house"})
    List<AssetRepair> findByAssetIdOrderByReportedAtDesc(Long assetId);

    List<AssetRepair> findByAssetIdIn(Collection<Long> assetIds);

    @Query("""
            select coalesce(sum(r.cost), 0) from AssetRepair r
            where r.asset.id in :assetIds
              and r.reportedAt >= :from
              and r.reportedAt <= :to
            """)
    long sumCostByAssetIdsBetween(@Param("assetIds") Collection<Long> assetIds,
                                  @Param("from") LocalDate from,
                                  @Param("to") LocalDate to);

    @Query(value = """
            select r from AssetRepair r
              join fetch r.asset a
              join fetch a.room rr
              join fetch rr.house h
            where (:houseId is null or h.id = :houseId)
              and (:roomId is null or rr.id = :roomId)
              and (:status is null or r.status = :status)
              and (:q is null or lower(a.code) like :q or lower(a.name) like :q
                   or lower(r.description) like :q)
              and (:ownerScope is null or h.owner.id = :ownerScope or h.manager.id = :ownerScope)
              and (:areaScope is null or h.areaAdmin = :areaScope)
            """, countQuery = """
            select count(r) from AssetRepair r
              join r.asset a
              join a.room rr
              join rr.house h
            where (:houseId is null or h.id = :houseId)
              and (:roomId is null or rr.id = :roomId)
              and (:status is null or r.status = :status)
              and (:q is null or lower(a.code) like :q or lower(a.name) like :q
                   or lower(r.description) like :q)
              and (:ownerScope is null or h.owner.id = :ownerScope or h.manager.id = :ownerScope)
              and (:areaScope is null or h.areaAdmin = :areaScope)
            """)
    Page<AssetRepair> search(@Param("houseId") Long houseId,
                             @Param("roomId") Long roomId,
                             @Param("status") AssetRepairStatus status,
                             @Param("q") String q,
                             @Param("ownerScope") Long ownerScope,
                             @Param("areaScope") String areaScope,
                             Pageable pageable);
}
