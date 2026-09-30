package com.ruinhome.contract;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    private final ContractService contractService;

    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    @GetMapping
    public Map<String, Object> search(
            @RequestParam(required = false) Long houseId,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) ContractStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = contractService.search(houseId, roomId, status, page, Math.min(size, 100));
        return Map.of(
                "items", result.getContent(),
                "total", result.getTotalElements(),
                "page", result.getNumber(),
                "size", result.getSize());
    }

    @GetMapping("/{id}")
    public ContractDtos.ContractResponse get(@PathVariable Long id) {
        return contractService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ContractDtos.ContractResponse create(
            @Valid @RequestBody ContractDtos.ContractCreateRequest request) {
        return contractService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ContractDtos.ContractResponse update(
            @PathVariable Long id,
            @Valid @RequestBody ContractDtos.ContractUpdateRequest request) {
        return contractService.update(id, request);
    }

    @PostMapping("/{id}/terminate")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ContractDtos.ContractResponse terminate(@PathVariable Long id) {
        return contractService.terminate(id);
    }
}
