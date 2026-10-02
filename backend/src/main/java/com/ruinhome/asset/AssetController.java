package com.ruinhome.asset;

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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Map<String, Object> list(
            @RequestParam(required = false) Long houseId,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) AssetCondition condition,
            @RequestParam(required = false) AssetCategory category,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = assetService.search(houseId, roomId, condition, category, q, page, Math.min(size, 100));
        return Map.of(
                "items", result.getContent(),
                "total", result.getTotalElements(),
                "page", result.getNumber(),
                "size", result.getSize());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public AssetDtos.AssetResponse get(@PathVariable Long id) {
        return assetService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public AssetDtos.AssetResponse create(@Valid @RequestBody AssetDtos.AssetRequest request) {
        return assetService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public AssetDtos.AssetResponse update(@PathVariable Long id,
                                          @Valid @RequestBody AssetDtos.AssetRequest request) {
        return assetService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Map<String, String> delete(@PathVariable Long id) {
        assetService.softDelete(id);
        return Map.of("message", "Đã xoá tài sản");
    }

    @GetMapping("/{id}/repairs")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<AssetDtos.AssetRepairResponse> listRepairs(@PathVariable Long id) {
        return assetService.listRepairs(id);
    }

    @GetMapping("/repairs")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Map<String, Object> searchRepairs(
            @RequestParam(required = false) Long houseId,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) AssetRepairStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = assetService.searchRepairs(houseId, roomId, status, q, page, Math.min(size, 100));
        return Map.of(
                "items", result.getContent(),
                "total", result.getTotalElements(),
                "page", result.getNumber(),
                "size", result.getSize());
    }

    @PostMapping("/{id}/repairs")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public AssetDtos.AssetRepairResponse createRepair(@PathVariable Long id,
                                                      @Valid @RequestBody AssetDtos.AssetRepairRequest request) {
        return assetService.createRepair(id, request);
    }

    @PutMapping("/{id}/repairs/{repairId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public AssetDtos.AssetRepairResponse updateRepair(@PathVariable Long id,
                                                      @PathVariable Long repairId,
                                                      @Valid @RequestBody AssetDtos.AssetRepairRequest request) {
        return assetService.updateRepair(id, repairId, request);
    }

    @DeleteMapping("/{id}/repairs/{repairId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Map<String, String> deleteRepair(@PathVariable Long id, @PathVariable Long repairId) {
        assetService.deleteRepair(id, repairId);
        return Map.of("message", "Đã xoá lịch sử sửa chữa");
    }
}
