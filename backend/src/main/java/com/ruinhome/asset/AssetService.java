package com.ruinhome.asset;

import com.ruinhome.auth.CurrentUserService;
import com.ruinhome.house.House;
import com.ruinhome.room.Room;
import com.ruinhome.room.RoomRepository;
import com.ruinhome.user.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AssetService {

    private final AssetRepository assetRepository;
    private final AssetRepairRepository assetRepairRepository;
    private final AssetPhotoRepository assetPhotoRepository;
    private final RoomRepository roomRepository;
    private final ContractHandoverPort contractHandoverPort;
    private final AssetPhotoService assetPhotoService;
    private final CurrentUserService currentUserService;

    public AssetService(AssetRepository assetRepository, AssetRepairRepository assetRepairRepository,
                        AssetPhotoRepository assetPhotoRepository, RoomRepository roomRepository,
                        ContractHandoverPort contractHandoverPort, AssetPhotoService assetPhotoService,
                        CurrentUserService currentUserService) {
        this.assetRepository = assetRepository;
        this.assetRepairRepository = assetRepairRepository;
        this.assetPhotoRepository = assetPhotoRepository;
        this.roomRepository = roomRepository;
        this.contractHandoverPort = contractHandoverPort;
        this.assetPhotoService = assetPhotoService;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public Page<AssetDtos.AssetResponse> search(Long houseId, Long roomId, AssetCondition condition,
                                                AssetCategory category, String q, int page, int size) {
        Long ownerScope = null;
        String areaScope = null;
        var role = currentUserService.account().getRole();
        if (role == Role.ADMIN) {
            areaScope = currentUserService.areaOrNull();
        } else if (role == Role.MANAGER) {
            ownerScope = currentUserService.personId();
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền với tài sản");
        }
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "code"));
        Page<Asset> result = assetRepository.search(houseId, roomId, condition, category,
                toLike(q), ownerScope, areaScope, pageable);
        List<Long> ids = result.getContent().stream().map(Asset::getId).toList();
        Map<Long, RepairStat> stats = loadRepairStats(ids);
        Map<Long, String> photoUrls = loadAssetPhotoUrls(ids);
        Map<Long, Integer> photoCounts = loadAssetPhotoCounts(ids);
        return result.map(asset -> toResponse(asset, stats.getOrDefault(asset.getId(), RepairStat.EMPTY),
                photoUrls.get(asset.getId()), photoCounts.getOrDefault(asset.getId(), 0)));
    }

    @Transactional(readOnly = true)
    public AssetDtos.AssetResponse get(Long id) {
        Asset asset = find(id);
        RepairStat stat = loadRepairStats(List.of(asset.getId())).getOrDefault(asset.getId(), RepairStat.EMPTY);
        return toResponse(asset, stat, loadAssetPhotoUrls(List.of(asset.getId())).get(asset.getId()),
                (int) assetPhotoRepository.countByAssetId(asset.getId()));
    }

    @Transactional
    public AssetDtos.AssetResponse create(AssetDtos.AssetRequest request) {
        Room room = findVisibleRoom(request.roomId());
        String code = request.code().trim();
        if (assetRepository.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã tài sản đã tồn tại");
        }
        Asset asset = new Asset();
        asset.setRoom(room);
        apply(asset, request);
        Asset saved = assetRepository.save(asset);
        return toResponse(saved, RepairStat.EMPTY, null, 0);
    }

    @Transactional
    public AssetDtos.AssetResponse update(Long id, AssetDtos.AssetRequest request) {
        Asset asset = find(id);
        if (!request.roomId().equals(asset.getRoom().getId())) {
            Room room = findVisibleRoom(request.roomId());
            if (!room.getHouse().getId().equals(asset.getRoom().getHouse().getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Không thể chuyển tài sản sang nhà khác");
            }
            asset.setRoom(room);
        }
        if (!asset.getCode().equals(request.code().trim())
                && assetRepository.existsByCode(request.code().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã tài sản đã tồn tại");
        }
        apply(asset, request);
        Asset saved = assetRepository.save(asset);
        RepairStat stat = loadRepairStats(List.of(saved.getId())).getOrDefault(saved.getId(), RepairStat.EMPTY);
        return toResponse(saved, stat, loadAssetPhotoUrls(List.of(saved.getId())).get(saved.getId()),
                (int) assetPhotoRepository.countByAssetId(saved.getId()));
    }

    @Transactional
    public void softDelete(Long id) {
        Asset asset = find(id);
        if (contractHandoverPort.existsActiveHandover(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tài sản đang được giao trong hợp đồng");
        }
        assetPhotoService.deleteByAsset(id);
        asset.setActive(false);
        assetRepository.save(asset);
    }

    @Transactional(readOnly = true)
    public List<AssetDtos.AssetRepairResponse> listRepairs(Long assetId) {
        find(assetId);
        List<AssetRepair> repairs = assetRepairRepository.findByAssetIdOrderByReportedAtDesc(assetId);
        Map<Long, Map<AssetPhotoStage, String>> photos =
                loadRepairPhotoUrls(repairs.stream().map(AssetRepair::getId).toList());
        return repairs.stream()
                .map(repair -> toRepairResponse(repair,
                        photos.getOrDefault(repair.getId(), Map.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<AssetDtos.AssetRepairResponse> searchRepairs(Long houseId, Long roomId,
                                                             AssetRepairStatus status, String q,
                                                             int page, int size) {
        Long ownerScope = null;
        String areaScope = null;
        var role = currentUserService.account().getRole();
        if (role == Role.ADMIN) {
            areaScope = currentUserService.areaOrNull();
        } else if (role == Role.MANAGER) {
            ownerScope = currentUserService.personId();
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền với tài sản");
        }
        var pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "reportedAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<AssetRepair> result =
                assetRepairRepository.search(houseId, roomId, status, toLike(q), ownerScope, areaScope, pageable);
        Map<Long, Map<AssetPhotoStage, String>> photos =
                loadRepairPhotoUrls(result.getContent().stream().map(AssetRepair::getId).toList());
        return result.map(repair -> toRepairResponse(repair, photos.getOrDefault(repair.getId(), Map.of())));
    }

    @Transactional
    public AssetDtos.AssetRepairResponse createRepair(Long assetId, AssetDtos.AssetRepairRequest request) {
        Asset asset = find(assetId);
        AssetRepair repair = new AssetRepair();
        applyRepair(repair, request, asset);
        return toRepairResponse(assetRepairRepository.save(repair), Map.of());
    }

    @Transactional
    public AssetDtos.AssetRepairResponse updateRepair(Long assetId, Long repairId,
                                                      AssetDtos.AssetRepairRequest request) {
        Asset asset = find(assetId);
        AssetRepair repair = findRepair(assetId, repairId);
        applyRepair(repair, request, asset);
        return toRepairResponse(assetRepairRepository.save(repair), Map.of());
    }

    @Transactional
    public void deleteRepair(Long assetId, Long repairId) {
        find(assetId);
        AssetRepair repair = findRepair(assetId, repairId);
        assetPhotoService.deleteByRepair(repairId);
        assetRepairRepository.delete(repair);
    }

    private void apply(Asset asset, AssetDtos.AssetRequest request) {
        asset.setCode(request.code().trim());
        asset.setName(request.name().trim());
        asset.setCategory(request.category());
        asset.setPrice(request.price());
        asset.setPurchaseDate(request.purchaseDate());
        asset.setCondition(request.condition());
        asset.setNote(normalizeNote(request.note()));
    }

    private void applyRepair(AssetRepair repair, AssetDtos.AssetRepairRequest request, Asset asset) {
        repair.setAsset(asset);
        repair.setReportedAt(request.reportedAt());
        repair.setDescription(request.description().trim());
        repair.setCost(request.cost());
        repair.setStatus(request.status());
        repair.setNote(normalizeNote(request.note()));
        if (request.status() == AssetRepairStatus.DONE) {
            repair.setDoneAt(request.doneAt() != null ? request.doneAt() : LocalDate.now());
            relaxCondition(asset);
        } else {
            repair.setDoneAt(null);
        }
    }

    private void relaxCondition(Asset asset) {
        if (asset.getCondition() == AssetCondition.NEEDS_REPAIR
                || asset.getCondition() == AssetCondition.BROKEN) {
            asset.setCondition(AssetCondition.USED);
            assetRepository.save(asset);
        }
    }

    private Asset find(Long id) {
        Asset asset = assetRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài sản"));
        checkHouseVisible(asset.getRoom().getHouse());
        return asset;
    }

    private Room findVisibleRoom(Long roomId) {
        Room room = roomRepository.findByIdAndActiveTrue(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phòng"));
        checkHouseVisible(room.getHouse());
        return room;
    }

    private AssetRepair findRepair(Long assetId, Long repairId) {
        AssetRepair repair = assetRepairRepository.findById(repairId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch sử sửa chữa"));
        if (!repair.getAsset().getId().equals(assetId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch sử sửa chữa");
        }
        return repair;
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

    private Map<Long, RepairStat> loadRepairStats(List<Long> assetIds) {
        Map<Long, RepairStat> stats = new HashMap<>();
        assetIds.forEach(id -> stats.put(id, RepairStat.EMPTY));
        for (AssetRepair repair : assetRepairRepository.findByAssetIdIn(assetIds)) {
            Long assetId = repair.getAsset().getId();
            RepairStat current = stats.getOrDefault(assetId, RepairStat.EMPTY);
            stats.put(assetId, new RepairStat(current.count() + 1, current.cost() + repair.getCost()));
        }
        return stats;
    }

    private Map<Long, String> loadAssetPhotoUrls(List<Long> assetIds) {
        Map<Long, String> urls = new HashMap<>();
        if (assetIds.isEmpty()) {
            return urls;
        }
        for (AssetPhoto photo : assetPhotoRepository.findByAssetIdInOrderByUploadedAtDesc(assetIds)) {
            Long assetId = photo.getAsset().getId();
            urls.putIfAbsent(assetId, contentUrl(assetId, photo));
        }
        return urls;
    }

    private Map<Long, Integer> loadAssetPhotoCounts(List<Long> assetIds) {
        Map<Long, Integer> counts = new HashMap<>();
        if (assetIds.isEmpty()) {
            return counts;
        }
        for (AssetPhoto photo : assetPhotoRepository.findByAssetIdInOrderByUploadedAtDesc(assetIds)) {
            Long assetId = photo.getAsset().getId();
            counts.put(assetId, counts.getOrDefault(assetId, 0) + 1);
        }
        return counts;
    }

    private Map<Long, Map<AssetPhotoStage, String>> loadRepairPhotoUrls(List<Long> repairIds) {
        Map<Long, Map<AssetPhotoStage, String>> urls = new HashMap<>();
        if (repairIds.isEmpty()) {
            return urls;
        }
        for (AssetPhoto photo : assetPhotoRepository.findByRepairIdInOrderByUploadedAtDesc(repairIds)) {
            Long repairId = photo.getRepair().getId();
            urls.computeIfAbsent(repairId, key -> new HashMap<>())
                    .putIfAbsent(photo.getStage(),
                            contentUrl(photo.getAsset().getId(), photo));
        }
        return urls;
    }

    private String contentUrl(Long assetId, AssetPhoto photo) {
        if (photo.getRepair() == null) {
            return "/api/assets/" + assetId + "/photos/" + photo.getId() + "/content";
        }
        return "/api/assets/" + assetId + "/repairs/" + photo.getRepair().getId()
                + "/photos/" + photo.getId() + "/content";
    }

    private String toLike(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        return "%" + q.trim().toLowerCase() + "%";
    }

    private String normalizeNote(String note) {
        if (note == null) {
            return null;
        }
        String trimmed = note.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private AssetDtos.AssetResponse toResponse(Asset asset, RepairStat stat, String photoUrl, int photoCount) {
        Room room = asset.getRoom();
        House house = room.getHouse();
        return new AssetDtos.AssetResponse(
                asset.getId(),
                room.getId(),
                house.getId(),
                house.getName(),
                room.getRoomNumber(),
                asset.getCode(),
                asset.getName(),
                asset.getCategory(),
                asset.getPrice(),
                asset.getPurchaseDate(),
                asset.getCondition(),
                asset.getNote(),
                asset.isActive(),
                stat.count(),
                stat.cost(),
                photoUrl,
                photoCount,
                asset.getCreatedAt(),
                asset.getUpdatedAt());
    }

    private AssetDtos.AssetRepairResponse toRepairResponse(AssetRepair repair,
                                                           Map<AssetPhotoStage, String> photoUrls) {
        Asset asset = repair.getAsset();
        Room room = asset.getRoom();
        House house = room.getHouse();
        return new AssetDtos.AssetRepairResponse(
                repair.getId(),
                asset.getId(),
                asset.getCode(),
                asset.getName(),
                room.getId(),
                house.getId(),
                house.getName(),
                room.getRoomNumber(),
                repair.getReportedAt(),
                repair.getDescription(),
                repair.getCost(),
                repair.getStatus(),
                repair.getDoneAt(),
                repair.getNote(),
                photoUrls.get(AssetPhotoStage.TRUOC),
                photoUrls.get(AssetPhotoStage.SAU),
                repair.getCreatedAt(),
                repair.getUpdatedAt());
    }

    private record RepairStat(int count, long cost) {
        private static final RepairStat EMPTY = new RepairStat(0, 0L);
    }
}
