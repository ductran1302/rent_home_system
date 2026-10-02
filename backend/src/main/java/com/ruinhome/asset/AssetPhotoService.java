package com.ruinhome.asset;

import com.ruinhome.auth.CurrentUserService;
import com.ruinhome.file.FileStorageService;
import com.ruinhome.house.House;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AssetPhotoService {

    public static final int MAX_PHOTOS = 5;
    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    private final AssetPhotoRepository assetPhotoRepository;
    private final AssetRepository assetRepository;
    private final AssetRepairRepository assetRepairRepository;
    private final FileStorageService fileStorageService;
    private final CurrentUserService currentUserService;

    public AssetPhotoService(AssetPhotoRepository assetPhotoRepository, AssetRepository assetRepository,
                             AssetRepairRepository assetRepairRepository, FileStorageService fileStorageService,
                             CurrentUserService currentUserService) {
        this.assetPhotoRepository = assetPhotoRepository;
        this.assetRepository = assetRepository;
        this.assetRepairRepository = assetRepairRepository;
        this.fileStorageService = fileStorageService;
        this.currentUserService = currentUserService;
    }

    public record AssetPhotoResponse(Long id, String originalName, LocalDateTime uploadedAt,
                                     AssetPhotoStage stage, String contentUrl) {
    }

    public record Content(byte[] bytes, String contentType) {
    }

    @Transactional
    public AssetPhotoResponse uploadAssetPhoto(Long assetId, MultipartFile file) {
        Asset asset = findAsset(assetId);
        if (assetPhotoRepository.countByAssetIdAndRepairIsNull(assetId) >= MAX_PHOTOS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tối đa " + MAX_PHOTOS + " ảnh");
        }
        AssetPhoto photo = new AssetPhoto();
        photo.setAsset(asset);
        photo.setOriginalName(file != null ? file.getOriginalFilename() : null);
        photo.setFilePath(store("assets/" + assetId, file));
        return toAssetResponse(assetId, assetPhotoRepository.save(photo));
    }

    @Transactional(readOnly = true)
    public List<AssetPhotoResponse> listAssetPhotos(Long assetId) {
        findAsset(assetId);
        return assetPhotoRepository.findByAssetIdAndRepairIsNullOrderByUploadedAtDesc(assetId).stream()
                .map(photo -> toAssetResponse(assetId, photo))
                .toList();
    }

    @Transactional(readOnly = true)
    public Content findAssetContent(Long assetId, Long photoId) {
        findAsset(assetId);
        AssetPhoto photo = assetPhotoRepository.findById(photoId)
                .filter(item -> item.getRepair() == null && item.getAsset().getId().equals(assetId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh"));
        return readContent(photo);
    }

    @Transactional
    public void deleteAssetPhoto(Long assetId, Long photoId) {
        findAsset(assetId);
        AssetPhoto photo = assetPhotoRepository.findById(photoId)
                .filter(item -> item.getRepair() == null && item.getAsset().getId().equals(assetId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh"));
        fileStorageService.delete(photo.getFilePath());
        assetPhotoRepository.delete(photo);
    }

    @Transactional
    public AssetPhotoResponse uploadRepairPhoto(Long assetId, Long repairId, AssetPhotoStage stage,
                                                MultipartFile file) {
        AssetRepair repair = findRepair(assetId, repairId);
        if (stage == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn ảnh trước hoặc ảnh sau");
        }
        if (assetPhotoRepository.countByRepairId(repairId) >= MAX_PHOTOS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tối đa " + MAX_PHOTOS + " ảnh");
        }
        AssetPhoto photo = new AssetPhoto();
        photo.setAsset(repair.getAsset());
        photo.setRepair(repair);
        photo.setStage(stage);
        photo.setOriginalName(file != null ? file.getOriginalFilename() : null);
        photo.setFilePath(store("assets/" + assetId + "/repairs/" + repairId, file));
        return toRepairResponse(assetId, repairId, assetPhotoRepository.save(photo));
    }

    @Transactional(readOnly = true)
    public List<AssetPhotoResponse> listRepairPhotos(Long assetId, Long repairId) {
        findRepair(assetId, repairId);
        return assetPhotoRepository.findByRepairIdOrderByUploadedAtDesc(repairId).stream()
                .map(photo -> toRepairResponse(assetId, repairId, photo))
                .toList();
    }

    @Transactional(readOnly = true)
    public Content findRepairContent(Long assetId, Long repairId, Long photoId) {
        findRepair(assetId, repairId);
        return readContent(findRepairPhoto(repairId, photoId));
    }

    @Transactional
    public void deleteRepairPhoto(Long assetId, Long repairId, Long photoId) {
        findRepair(assetId, repairId);
        AssetPhoto photo = findRepairPhoto(repairId, photoId);
        fileStorageService.delete(photo.getFilePath());
        assetPhotoRepository.delete(photo);
    }

    @Transactional
    public void deleteByRepair(Long repairId) {
        for (AssetPhoto photo : assetPhotoRepository.findByRepairId(repairId)) {
            fileStorageService.delete(photo.getFilePath());
            assetPhotoRepository.delete(photo);
        }
    }

    @Transactional
    public void deleteByAsset(Long assetId) {
        for (AssetPhoto photo : assetPhotoRepository.findByAssetId(assetId)) {
            fileStorageService.delete(photo.getFilePath());
            assetPhotoRepository.delete(photo);
        }
    }

    private String store(String folder, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn tệp ảnh");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ảnh tối đa 5MB");
        }
        String contentType = file.getContentType();
        if (!fileStorageService.isAllowedType(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ chấp nhận ảnh JPG, PNG hoặc WebP");
        }
        return fileStorageService.store(folder, file, contentType);
    }

    private Content readContent(AssetPhoto photo) {
        Path path = fileStorageService.resolve(photo.getFilePath());
        if (!Files.exists(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tệp ảnh không còn trên máy chủ");
        }
        try {
            return new Content(Files.readAllBytes(path), fileStorageService.contentTypeFor(photo.getFilePath()));
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được tệp ảnh", e);
        }
    }

    private Asset findAsset(Long assetId) {
        Asset asset = assetRepository.findByIdAndActiveTrue(assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài sản"));
        checkHouseVisible(asset.getRoom().getHouse());
        return asset;
    }

    private AssetRepair findRepair(Long assetId, Long repairId) {
        findAsset(assetId);
        AssetRepair repair = assetRepairRepository.findById(repairId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy lịch sử sửa chữa"));
        if (!repair.getAsset().getId().equals(assetId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch sử sửa chữa");
        }
        return repair;
    }

    private AssetPhoto findRepairPhoto(Long repairId, Long photoId) {
        return assetPhotoRepository.findById(photoId)
                .filter(item -> item.getRepair() != null && item.getRepair().getId().equals(repairId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh"));
    }

    private void checkHouseVisible(House house) {
        if (currentUserService.isAdmin()) {
            currentUserService.checkArea(house.getAreaAdmin());
            return;
        }
        Long personId = currentUserService.personId();
        if (!personId.equals(house.getOwner().getId()) && !personId.equals(house.getManager().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền với nhà này");
        }
    }

    private AssetPhotoResponse toAssetResponse(Long assetId, AssetPhoto photo) {
        return new AssetPhotoResponse(photo.getId(), photo.getOriginalName(), photo.getUploadedAt(),
                photo.getStage(), "/api/assets/" + assetId + "/photos/" + photo.getId() + "/content");
    }

    private AssetPhotoResponse toRepairResponse(Long assetId, Long repairId, AssetPhoto photo) {
        return new AssetPhotoResponse(photo.getId(), photo.getOriginalName(), photo.getUploadedAt(),
                photo.getStage(), "/api/assets/" + assetId + "/repairs/" + repairId + "/photos/"
                + photo.getId() + "/content");
    }
}
