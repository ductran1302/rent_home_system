package com.ruinhome.contract;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContractAssetRepository extends JpaRepository<ContractAsset, Long> {

    @EntityGraph(attributePaths = {"asset", "asset.room", "asset.room.house"})
    List<ContractAsset> findByContractIdOrderByAssetCodeAsc(Long contractId);

    @EntityGraph(attributePaths = {"asset", "asset.room", "asset.room.house"})
    Optional<ContractAsset> findByContractIdAndAssetId(Long contractId, Long assetId);

    void deleteByContractId(Long contractId);

    boolean existsByAssetIdAndContractStatus(Long assetId, ContractStatus status);
}
