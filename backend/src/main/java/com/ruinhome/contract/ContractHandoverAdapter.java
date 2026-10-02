package com.ruinhome.contract;

import com.ruinhome.asset.ContractHandoverPort;
import org.springframework.stereotype.Component;

@Component
public class ContractHandoverAdapter implements ContractHandoverPort {

    private final ContractAssetRepository contractAssetRepository;

    public ContractHandoverAdapter(ContractAssetRepository contractAssetRepository) {
        this.contractAssetRepository = contractAssetRepository;
    }

    @Override
    public boolean existsActiveHandover(Long assetId) {
        return contractAssetRepository.existsByAssetIdAndContractStatus(assetId, ContractStatus.ACTIVE);
    }
}
