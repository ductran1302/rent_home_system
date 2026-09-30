package com.ruinhome.billing;

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

import java.util.List;

@RestController
@RequestMapping("/api/billing/meters")
public class MeterController {

    private final MeterService meterService;

    public MeterController(MeterService meterService) {
        this.meterService = meterService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<BillingDtos.MeterResponse> search(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) Long roomId) {
        return meterService.search(period, roomId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public BillingDtos.MeterResponse create(@Valid @RequestBody BillingDtos.MeterCreateRequest request) {
        return meterService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public BillingDtos.MeterResponse update(@PathVariable Long id,
                                            @Valid @RequestBody BillingDtos.MeterUpdateRequest request) {
        return meterService.update(id, request);
    }
}
