package com.ruinhome.billing;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByRoomIdAndPeriod(Long roomId, String period);

    boolean existsByRoomIdAndPeriod(Long roomId, String period);

    @Query("""
            select i from Invoice i
              join fetch i.room r
              join fetch r.house h
            where i.period = :period
              and i.status in (com.ruinhome.billing.InvoiceStatus.UNPAID,
                               com.ruinhome.billing.InvoiceStatus.PARTIAL)
            """)
    List<Invoice> findUnpaidForPeriod(@Param("period") String period);

    @Query("""
            select distinct i from Invoice i
              join fetch i.room r
              join fetch r.house h
            where (:period is null or i.period = :period)
              and (:houseId is null or h.id = :houseId)
              and (:status is null or i.status = :status)
              and (:ownerScope is null or h.owner.id = :ownerScope or h.manager.id = :ownerScope)
              and (:areaScope is null or h.areaAdmin = :areaScope)
              and (:userScope is null or exists (
                    select 1 from Contract c2
                    where c2.room = r
                      and (c2.holder.id = :userScope
                           or exists (select t2 from c2.tenants t2 where t2.id = :userScope))))
            """)
    Page<Invoice> search(@Param("period") String period,
                         @Param("houseId") Long houseId,
                         @Param("status") InvoiceStatus status,
                         @Param("ownerScope") Long ownerScope,
                         @Param("userScope") Long userScope,
                         @Param("areaScope") String areaScope,
                         Pageable pageable);

    @Query("""
            select count(i) from Invoice i join i.room r join r.house h
            where i.period = :period
              and i.status in (com.ruinhome.billing.InvoiceStatus.UNPAID,
                                  com.ruinhome.billing.InvoiceStatus.PARTIAL)
              and (:ownerScope is null or h.owner.id = :ownerScope or h.manager.id = :ownerScope)
              and (:areaScope is null or h.areaAdmin = :areaScope)
              and (:userScope is null or exists (
                    select 1 from Contract c2
                    where c2.room = r
                      and (c2.holder.id = :userScope
                           or exists (select t2 from c2.tenants t2 where t2.id = :userScope))))
            """)
    long countUnpaidForPeriod(@Param("period") String period,
                          @Param("ownerScope") Long ownerScope,
                          @Param("userScope") Long userScope,
                          @Param("areaScope") String areaScope);

    @Query("""
            select coalesce(sum(i.totalAmount - i.paidAmount), 0) from Invoice i join i.room r join r.house h
            where i.period = :period
              and i.status in (com.ruinhome.billing.InvoiceStatus.UNPAID,
                                  com.ruinhome.billing.InvoiceStatus.PARTIAL)
              and (:ownerScope is null or h.owner.id = :ownerScope or h.manager.id = :ownerScope)
              and (:areaScope is null or h.areaAdmin = :areaScope)
              and (:userScope is null or exists (
                    select 1 from Contract c2
                    where c2.room = r
                      and (c2.holder.id = :userScope
                           or exists (select t2 from c2.tenants t2 where t2.id = :userScope))))
            """)
    long sumDebtForPeriod(@Param("period") String period,
                      @Param("ownerScope") Long ownerScope,
                      @Param("userScope") Long userScope,
                      @Param("areaScope") String areaScope);

    @Query("""
            select new com.ruinhome.billing.InvoiceRevenue(
                i.period,
                coalesce(sum(i.paidAmount), 0),
                coalesce(sum(i.totalAmount - i.paidAmount), 0))
            from Invoice i join i.room r join r.house h
            where i.period between :fromPeriod and :toPeriod
              and (:ownerScope is null or h.owner.id = :ownerScope or h.manager.id = :ownerScope)
              and (:areaScope is null or h.areaAdmin = :areaScope)
              and (:userScope is null or exists (
                    select 1 from Contract c2
                    where c2.room = r
                      and (c2.holder.id = :userScope
                           or exists (select t2 from c2.tenants t2 where t2.id = :userScope))))
            group by i.period
            """)
    List<InvoiceRevenue> sumRevenueByPeriod(@Param("fromPeriod") String fromPeriod,
                                            @Param("toPeriod") String toPeriod,
                                            @Param("ownerScope") Long ownerScope,
                                            @Param("userScope") Long userScope,
                                            @Param("areaScope") String areaScope);
}
