package com.ruinhome.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FeeRateRepository extends JpaRepository<FeeRate, Long> {

    Optional<FeeRate> findByFeeTypeIdAndPeriod(Long feeTypeId, String period);

    @Query("select fr from FeeRate fr join fetch fr.feeType where fr.period = :period and fr.active = true")
    List<FeeRate> findByPeriod(@Param("period") String period);

    @Query("""
            select fr from FeeRate fr join fetch fr.feeType
            where fr.active = true and (:feeTypeId is null or fr.feeType.id = :feeTypeId)
            order by fr.feeType.code, fr.period
            """)
    List<FeeRate> search(@Param("feeTypeId") Long feeTypeId);
}
