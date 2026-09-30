package com.ruinhome.contract;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContractPhotoRepository extends JpaRepository<ContractPhoto, Long> {

    @EntityGraph(attributePaths = {"contract"})
    List<ContractPhoto> findByContractIdOrderByUploadedAtDesc(Long contractId);
}
