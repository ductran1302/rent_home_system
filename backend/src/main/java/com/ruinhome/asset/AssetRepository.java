package com.ruinhome.asset;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetRepository extends JpaRepository<Asset, Long> {

    List<Asset> findByRoomIdAndActiveTrueOrderByCodeAsc(Long roomId);

    boolean existsByCode(String code);
}
