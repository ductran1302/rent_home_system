package com.ruinhome.billing;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/billing")
public class FeeConfigController {

    private final FeeConfigService feeConfigService;

    public FeeConfigController(FeeConfigService feeConfigService) {
        this.feeConfigService = feeConfigService;
    }

    @GetMapping("/fee-types")
    public List<BillingDtos.FeeTypeResponse> feeTypes() {
        return feeConfigService.listFeeTypes();
    }

    @GetMapping("/fee-rates")
    public List<BillingDtos.FeeRateResponse> feeRates(@RequestParam(required = false) Long feeTypeId) {
        return feeConfigService.listFeeRates(feeTypeId);
    }

    @PutMapping("/fee-rates")
    @PreAuthorize("hasRole('ADMIN')")
    public BillingDtos.FeeRateResponse upsertFeeRate(
            @Valid @RequestBody BillingDtos.FeeRateUpsertRequest request) {
        return feeConfigService.upsertFeeRate(request);
    }
}
