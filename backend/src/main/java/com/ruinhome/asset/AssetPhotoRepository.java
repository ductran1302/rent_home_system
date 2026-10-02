package com.ruinhome.asset;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AssetPhotoRepository extends JpaRepository<AssetPhoto, Long> {

    List<AssetPhoto> findByAssetIdAndRepairIsNullOrderByUploadedAtDesc(Long assetId);

    List<AssetPhoto> findByRepairIdOrderByUploadedAtDesc(Long repairId);

    List<AssetPhoto> findByAssetIdInOrderByUploadedAtDesc(Collection<Long> assetIds);

    List<AssetPhoto> findByRepairIdInOrderByUploadedAtDesc(Collection<Long> repairIds);

    long countByAssetIdAndRepairIsNull(Long assetId);

    long countByAssetId(Long assetId);

    long countByRepairId(Long repairId);

    List<AssetPhoto> findByAssetId(Long assetId);

    List<AssetPhoto> findByRepairId(Long repairId);
}
