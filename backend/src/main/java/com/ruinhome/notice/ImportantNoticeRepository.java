package com.ruinhome.notice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ImportantNoticeRepository extends JpaRepository<ImportantNotice, Long> {

    @Query("""
            select n from ImportantNotice n left join n.house h
            where n.active = true
              and (:areaScope is null or n.house is null or h.areaAdmin = :areaScope)
              and (:managerScope is null or n.house is null
                   or h.owner.id = :managerScope or h.manager.id = :managerScope)
            order by n.id desc
            """)
    List<ImportantNotice> findAllForScope(@Param("areaScope") String areaScope,
                                          @Param("managerScope") Long managerScope);

    @Query("""
            select n from ImportantNotice n left join n.house h
            where n.active = true
              and (n.startsAt is null or n.startsAt <= :now)
              and (n.endsAt is null or n.endsAt >= :now)
              and (:areaScope is null or n.house is null or h.areaAdmin = :areaScope)
            order by n.id
            """)
    List<ImportantNotice> findActive(@Param("now") LocalDateTime now,
                                     @Param("areaScope") String areaScope);

    @Query("""
            select n from ImportantNotice n left join n.house h
            where n.active = true
              and (n.startsAt is null or n.startsAt <= :now)
              and (n.endsAt is null or n.endsAt >= :now)
              and (n.house is null or h.owner.id = :personId or h.manager.id = :personId)
            order by n.id
            """)
    List<ImportantNotice> findActiveForManager(@Param("now") LocalDateTime now,
                                               @Param("personId") Long personId);

    @Query("""
            select n from ImportantNotice n
            where n.active = true
              and (n.startsAt is null or n.startsAt <= :now)
              and (n.endsAt is null or n.endsAt >= :now)
              and (n.house is null or exists (
                    select 1 from Contract c join c.room r
                    where r.house = n.house
                      and (c.holder.id = :personId
                           or exists (select t2 from c.tenants t2 where t2.id = :personId))))
            order by n.id
            """)
    List<ImportantNotice> findActiveForTenant(@Param("now") LocalDateTime now,
                                              @Param("personId") Long personId);
}
