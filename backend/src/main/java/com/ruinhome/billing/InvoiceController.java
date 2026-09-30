package com.ruinhome.billing;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/billing/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping
    public Map<String, Object> search(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) Long houseId,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = invoiceService.search(period, houseId, status, page, Math.min(size, 100));
        return Map.of(
                "items", result.getContent(),
                "total", result.getTotalElements(),
                "page", result.getNumber(),
                "size", result.getSize());
    }

    @GetMapping("/{id}")
    public BillingDtos.InvoiceDetailResponse get(@PathVariable Long id) {
        return invoiceService.get(id);
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public BillingDtos.GenerateResponse generate(@RequestParam String period) {
        return invoiceService.generate(period);
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public BillingDtos.InvoiceDetailResponse publish(@PathVariable Long id) {
        return invoiceService.publish(id);
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public BillingDtos.InvoiceDetailResponse pay(@PathVariable Long id,
                                                 @Valid @RequestBody BillingDtos.PaymentRequest request) {
        return invoiceService.pay(id, request);
    }

    @PostMapping("/{id}/lines")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public BillingDtos.InvoiceDetailResponse addLine(@PathVariable Long id,
                                                     @Valid @RequestBody BillingDtos.InvoiceLineRequest request) {
        return invoiceService.addLine(id, request);
    }

    @PutMapping("/{id}/room-price")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public BillingDtos.InvoiceDetailResponse updateRoomPrice(@PathVariable Long id,
                                                             @Valid @RequestBody BillingDtos.RoomPriceRequest request) {
        return invoiceService.updateRoomPrice(id, request);
    }

    @PutMapping("/{id}/lines/{lineId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public BillingDtos.InvoiceDetailResponse updateLine(@PathVariable Long id,
                                                        @PathVariable Long lineId,
                                                        @Valid @RequestBody BillingDtos.InvoiceLineUpdateRequest request) {
        return invoiceService.updateLine(id, lineId, request);
    }

    @DeleteMapping("/{id}/lines/{lineId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public BillingDtos.InvoiceDetailResponse deleteLine(@PathVariable Long id,
                                                        @PathVariable Long lineId) {
        return invoiceService.deleteLine(id, lineId);
    }
}
