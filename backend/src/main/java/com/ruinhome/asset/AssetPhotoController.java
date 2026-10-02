package com.ruinhome.asset;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/assets/{assetId}")
public class AssetPhotoController {

    private final AssetPhotoService assetPhotoService;

    public AssetPhotoController(AssetPhotoService assetPhotoService) {
        this.assetPhotoService = assetPhotoService;
    }

    @GetMapping("/photos")
    public List<AssetPhotoService.AssetPhotoResponse> list(@PathVariable Long assetId) {
        return assetPhotoService.listAssetPhotos(assetId);
    }

    @PostMapping("/photos")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public AssetPhotoService.AssetPhotoResponse upload(@PathVariable Long assetId,
                                                       @RequestParam("file") MultipartFile file) {
        return assetPhotoService.uploadAssetPhoto(assetId, file);
    }

    @GetMapping("/photos/{photoId}/content")
    public ResponseEntity<byte[]> content(@PathVariable Long assetId, @PathVariable Long photoId) {
        return toResponse(assetPhotoService.findAssetContent(assetId, photoId));
    }

    @DeleteMapping("/photos/{photoId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public void delete(@PathVariable Long assetId, @PathVariable Long photoId) {
        assetPhotoService.deleteAssetPhoto(assetId, photoId);
    }

    @GetMapping("/repairs/{repairId}/photos")
    public List<AssetPhotoService.AssetPhotoResponse> listRepairPhotos(@PathVariable Long assetId,
                                                                       @PathVariable Long repairId) {
        return assetPhotoService.listRepairPhotos(assetId, repairId);
    }

    @PostMapping("/repairs/{repairId}/photos")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public AssetPhotoService.AssetPhotoResponse uploadRepairPhoto(
            @PathVariable Long assetId,
            @PathVariable Long repairId,
            @RequestParam("stage") AssetPhotoStage stage,
            @RequestParam("file") MultipartFile file) {
        return assetPhotoService.uploadRepairPhoto(assetId, repairId, stage, file);
    }

    @GetMapping("/repairs/{repairId}/photos/{photoId}/content")
    public ResponseEntity<byte[]> repairContent(@PathVariable Long assetId,
                                                @PathVariable Long repairId,
                                                @PathVariable Long photoId) {
        return toResponse(assetPhotoService.findRepairContent(assetId, repairId, photoId));
    }

    @DeleteMapping("/repairs/{repairId}/photos/{photoId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public void deleteRepairPhoto(@PathVariable Long assetId,
                                  @PathVariable Long repairId,
                                  @PathVariable Long photoId) {
        assetPhotoService.deleteRepairPhoto(assetId, repairId, photoId);
    }

    private ResponseEntity<byte[]> toResponse(AssetPhotoService.Content content) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(content.bytes());
    }
}
