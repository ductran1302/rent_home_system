package com.ruinhome.asset;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AssetRepository extends JpaRepository<Asset, Long> {

    List<Asset> findByRoomIdAndActiveTrueOrderByCodeAsc(Long roomId);

    boolean existsByCode(String code);

    Optional<Asset> findByIdAndActiveTrue(Long id);

    List<Asset> findByIdInAndActiveTrue(Collection<Long> ids);

    @Query(value = """
            select a from Asset a
              join fetch a.room r
              join fetch r.house h
            where a.active = true
              and (:houseId is null or h.id = :houseId)
              and (:roomId is null or r.id = :roomId)
              and (:condition is null or a.condition = :condition)
              and (:category is null or a.category = :category)
              and (:q is null or lower(a.code) like :q or lower(a.name) like :q)
              and (:ownerScope is null or h.owner.id = :ownerScope or h.manager.id = :ownerScope)
              and (:areaScope is null or h.areaAdmin = :areaScope)
            """, countQuery = """
            select count(a) from Asset a
              join a.room r
              join r.house h
            where a.active = true
              and (:houseId is null or h.id = :houseId)
              and (:roomId is null or r.id = :roomId)
              and (:condition is null or a.condition = :condition)
              and (:category is null or a.category = :category)
              and (:q is null or lower(a.code) like :q or lower(a.name) like :q)
              and (:ownerScope is null or h.owner.id = :ownerScope or h.manager.id = :ownerScope)
              and (:areaScope is null or h.areaAdmin = :areaScope)
            """)
    Page<Asset> search(@Param("houseId") Long houseId,
                       @Param("roomId") Long roomId,
                       @Param("condition") AssetCondition condition,
                       @Param("category") AssetCategory category,
                       @Param("q") String q,
                       @Param("ownerScope") Long ownerScope,
                       @Param("areaScope") String areaScope,
                       Pageable pageable);
}
