package com.ruinhome.contract;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ContractFeePriceRepository extends JpaRepository<ContractFeePrice, Long> {

    List<ContractFeePrice> findByContractId(Long contractId);

    List<ContractFeePrice> findByContractIdIn(Collection<Long> contractIds);

    void deleteByContractId(Long contractId);
}
