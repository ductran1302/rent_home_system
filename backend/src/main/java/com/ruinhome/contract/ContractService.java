package com.ruinhome.contract;

import com.ruinhome.asset.Asset;
import com.ruinhome.asset.AssetCondition;
import com.ruinhome.asset.AssetRepair;
import com.ruinhome.asset.AssetRepairRepository;
import com.ruinhome.asset.AssetRepository;
import com.ruinhome.auth.CurrentUserService;
import com.ruinhome.house.House;
import com.ruinhome.house.HouseRepository;
import com.ruinhome.person.Person;
import com.ruinhome.person.PersonRepository;
import com.ruinhome.room.Room;
import com.ruinhome.room.RoomRepository;
import com.ruinhome.user.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ContractService {

    private static final Set<String> FEE_PRICE_CODES = Set.of("DIEN", "NUOC", "MANG", "DICH_VU");

    private final ContractRepository contractRepository;
    private final ContractFeePriceRepository contractFeePriceRepository;
    private final ContractAssetRepository contractAssetRepository;
    private final RoomRepository roomRepository;
    private final HouseRepository houseRepository;
    private final PersonRepository personRepository;
    private final AssetRepository assetRepository;
    private final AssetRepairRepository assetRepairRepository;
    private final CurrentUserService currentUserService;

    public ContractService(ContractRepository contractRepository,
                           ContractFeePriceRepository contractFeePriceRepository,
                           ContractAssetRepository contractAssetRepository,
                           RoomRepository roomRepository,
                           HouseRepository houseRepository, PersonRepository personRepository,
                           AssetRepository assetRepository, AssetRepairRepository assetRepairRepository,
                           CurrentUserService currentUserService) {
        this.contractRepository = contractRepository;
        this.contractFeePriceRepository = contractFeePriceRepository;
        this.contractAssetRepository = contractAssetRepository;
        this.roomRepository = roomRepository;
        this.houseRepository = houseRepository;
        this.personRepository = personRepository;
        this.assetRepository = assetRepository;
        this.assetRepairRepository = assetRepairRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public Page<ContractDtos.ContractResponse> search(Long houseId, Long roomId, ContractStatus status,
                                                      int page, int size) {
        contractRepository.expireOverdue(ContractStatus.ACTIVE, ContractStatus.EXPIRED, LocalDate.now());

        Long ownerScope = null;
        Long userScope = null;
        String areaScope = null;
        var role = currentUserService.account().getRole();
        if (role == Role.ADMIN) {
            areaScope = currentUserService.areaOrNull();
        } else if (role == Role.MANAGER) {
            ownerScope = currentUserService.personId();
        } else {
            var account = currentUserService.account();
            if (account.getPerson() == null) {
                return Page.empty(PageRequest.of(page, size));
            }
            userScope = account.getPerson().getId();
        }

        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startDate"));
        var result = contractRepository.search(houseId, roomId, status, ownerScope, userScope,
                areaScope, pageable);
        List<Contract> content = result.getContent();
        Map<Long, Map<String, Long>> feePrices = loadFeePrices(
                content.stream().map(Contract::getId).toList());
        List<ContractDtos.ContractResponse> items = content.stream()
                .map(contract -> toResponse(contract, feePrices.get(contract.getId())))
                .toList();
        return new PageImpl<>(items, pageable, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ContractDtos.ContractResponse get(Long id) {
        return toResponse(findVisible(id));
    }

    @Transactional
    public ContractDtos.ContractResponse create(ContractDtos.ContractCreateRequest request) {
        Room room = roomRepository.findByIdAndActiveTrue(request.roomId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phòng"));
        checkHouseVisible(room.getHouse());
        if (contractRepository.existsByRoomIdAndStatus(room.getId(), ContractStatus.ACTIVE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phòng đã có hợp đồng đang hoạt động");
        }
        validateDates(request.startDate(), request.endDate());

        Contract contract = new Contract();
        contract.setRoom(room);
        contract.setHolder(findActivePerson(request.holderId(), "khách thuê chính"));
        contract.setMonthlyRent(request.monthlyRent());
        contract.setStartDate(request.startDate());
        contract.setEndDate(request.endDate());
        contract.setStatus(ContractStatus.ACTIVE);
        contract.setNote(normalizeNote(request.note()));
        contract.setTenants(new LinkedHashSet<>(findActivePersons(request.tenantIds(), "người cùng thuê")));
        Contract saved = contractRepository.save(contract);
        replaceFeePrices(saved, request.feePrices());
        replaceAssets(saved, request.assetIds());
        return toResponse(saved);
    }

    @Transactional
    public ContractDtos.ContractResponse update(Long id, ContractDtos.ContractUpdateRequest request) {
        Contract contract = findVisible(id);
        checkHouseVisible(contract.getRoom().getHouse());
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Chỉ sửa được hợp đồng đang hoạt động");
        }
        validateDates(contract.getStartDate(), request.endDate());
        contract.setMonthlyRent(request.monthlyRent());
        contract.setEndDate(request.endDate());
        contract.setNote(normalizeNote(request.note()));
        contract.setTenants(new LinkedHashSet<>(findActivePersons(request.tenantIds(), "người cùng thuê")));
        Contract saved = contractRepository.save(contract);
        replaceFeePrices(saved, request.feePrices());
        replaceAssets(saved, request.assetIds());
        return toResponse(saved);
    }

    @Transactional
    public ContractDtos.ContractAssetListResponse listContractAssets(Long id) {
        Contract contract = findVisible(id);
        checkHouseVisible(contract.getRoom().getHouse());
        List<ContractAsset> rows = contractAssetRepository.findByContractIdOrderByAssetCodeAsc(contract.getId());
        Map<Long, RepairStat> stats = loadRepairStats(rows.stream()
                .map(row -> row.getAsset().getId()).toList());
        List<ContractDtos.ContractAssetItemResponse> items = rows.stream()
                .map(row -> toAssetItem(row, stats.getOrDefault(row.getAsset().getId(), RepairStat.EMPTY)))
                .toList();
        long repairCost = rows.isEmpty() ? 0L : assetRepairRepository.sumCostByAssetIdsBetween(
                rows.stream().map(row -> row.getAsset().getId()).toList(),
                contract.getStartDate(), contract.getEndDate());
        var summary = new ContractDtos.ContractAssetSummaryResponse(
                items.size(),
                (int) items.stream()
                        .filter(item -> item.condition() == AssetCondition.BROKEN).count(),
                (int) items.stream()
                        .filter(item -> item.condition() == AssetCondition.NEEDS_REPAIR).count(),
                repairCost);
        return new ContractDtos.ContractAssetListResponse(items, summary);
    }

    @Transactional
    public ContractDtos.ContractAssetItemResponse returnAsset(Long id, Long assetId,
                                                              ContractDtos.ContractAssetReturnRequest request) {
        Contract contract = findVisible(id);
        checkHouseVisible(contract.getRoom().getHouse());
        ContractAsset row = contractAssetRepository.findByContractIdAndAssetId(contract.getId(), assetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy tài sản trong hợp đồng"));
        Asset asset = row.getAsset();
        row.setReturnCondition(request.returnCondition());
        row.setReturnedAt(request.returnedAt() != null
                ? request.returnedAt().atStartOfDay()
                : LocalDateTime.now());
        asset.setCondition(request.returnCondition());
        contractAssetRepository.save(row);
        assetRepository.save(asset);
        return toAssetItem(contractAssetRepository.save(row),
                loadRepairStats(List.of(asset.getId())).getOrDefault(asset.getId(), RepairStat.EMPTY));
    }

    @Transactional
    public ContractDtos.ContractResponse terminate(Long id) {
        Contract contract = findVisible(id);
        checkHouseVisible(contract.getRoom().getHouse());
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ thu hồi được hợp đồng đang hoạt động");
        }
        contract.setStatus(ContractStatus.TERMINATED);
        return toResponse(contractRepository.save(contract));
    }

    private Contract findVisible(Long id) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng"));
        var role = currentUserService.account().getRole();
        if (role == Role.ADMIN) {
            checkHouseVisible(contract.getRoom().getHouse());
            return contract;
        }
        if (role == Role.MANAGER) {
            checkHouseVisible(contract.getRoom().getHouse());
            return contract;
        }
        Long personId = currentUserService.personIdOrNull();
        if (personId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền với hợp đồng này");
        }
        boolean isHolder = contract.getHolder().getId().equals(personId);
        boolean isTenant = contract.getTenants().stream()
                .anyMatch(tenant -> tenant.getId().equals(personId));
        if (!isHolder && !isTenant) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền với hợp đồng này");
        }
        return contract;
    }

    private void checkHouseVisible(House house) {
        var role = currentUserService.account().getRole();
        if (role == Role.ADMIN) {
            currentUserService.checkArea(house.getAreaAdmin());
            return;
        }
        if (role == Role.USER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền với nhà này");
        }
        Long personId = currentUserService.personId();
        if (!personId.equals(house.getOwner().getId()) && !personId.equals(house.getManager().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền với nhà này");
        }
    }

    private void validateDates(LocalDate start, LocalDate end) {
        if (!end.isAfter(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ngày kết thúc phải sau ngày bắt đầu");
        }
    }

    private String normalizeNote(String note) {
        if (note == null) {
            return null;
        }
        String trimmed = note.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void replaceFeePrices(Contract contract, Map<String, Long> feePrices) {
        if (feePrices == null) {
            return;
        }
        Map<String, Long> entries = new LinkedHashMap<>();
        feePrices.forEach((code, price) -> {
            if (price == null) {
                return;
            }
            if (!FEE_PRICE_CODES.contains(code)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Loại phí không hợp lệ: " + code);
            }
            if (price < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giá loại phí không hợp lệ");
            }
            entries.put(code, price);
        });
        contractFeePriceRepository.deleteByContractId(contract.getId());
        contractFeePriceRepository.flush();
        if (entries.isEmpty()) {
            return;
        }
        List<ContractFeePrice> rows = new ArrayList<>();
        entries.forEach((code, price) -> {
            ContractFeePrice row = new ContractFeePrice();
            row.setContract(contract);
            row.setFeeCode(code);
            row.setPrice(price);
            rows.add(row);
        });
        contractFeePriceRepository.saveAll(rows);
    }

    private void replaceAssets(Contract contract, List<Long> assetIds) {
        if (assetIds == null) {
            return;
        }
        contractAssetRepository.deleteByContractId(contract.getId());
        contractAssetRepository.flush();
        Set<Long> distinct = new LinkedHashSet<>(assetIds);
        if (distinct.isEmpty()) {
            return;
        }
        for (Long assetId : distinct) {
            Asset asset = assetRepository.findByIdAndActiveTrue(assetId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài sản"));
            if (!asset.getRoom().getId().equals(contract.getRoom().getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Tài sản không thuộc phòng của hợp đồng");
            }
            ContractAsset row = new ContractAsset();
            row.setContract(contract);
            row.setAsset(asset);
            row.setHandoverCondition(asset.getCondition());
            contractAssetRepository.save(row);
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

    private Map<Long, Map<String, Long>> loadFeePrices(List<Long> contractIds) {        if (contractIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<String, Long>> result = new HashMap<>();
        for (ContractFeePrice row : contractFeePriceRepository.findByContractIdIn(contractIds)) {
            result.computeIfAbsent(row.getContract().getId(), key -> new LinkedHashMap<>())
                    .put(row.getFeeCode(), row.getPrice());
        }
        return result;
    }

    private Person findActivePerson(Long personId, String label) {
        return personRepository.findById(personId)
                .filter(Person::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy " + label));
    }

    private List<Person> findActivePersons(List<Long> personIds, String label) {
        if (personIds == null || personIds.isEmpty()) {
            return List.of();
        }
        Set<Long> distinct = new LinkedHashSet<>(personIds);
        return distinct.stream().map(personId -> findActivePerson(personId, label)).toList();
    }

    private ContractDtos.ContractResponse toResponse(Contract contract) {
        Map<String, Long> feePrices = loadFeePrices(List.of(contract.getId())).get(contract.getId());
        return toResponse(contract, feePrices);
    }

    private ContractDtos.ContractResponse toResponse(Contract contract, Map<String, Long> feePrices) {
        Room room = contract.getRoom();
        House house = room.getHouse();
        var tenants = contract.getTenants().stream()
                .map(tenant -> new ContractDtos.TenantResponse(tenant.getId(), tenant.getFullName(), tenant.getPhone()))
                .toList();
        return new ContractDtos.ContractResponse(
                contract.getId(),
                room.getId(),
                house.getId(),
                house.getName(),
                house.getCode(),
                room.getRoomNumber(),
                contract.getHolder().getId(),
                contract.getHolder().getFullName(),
                contract.getHolder().getPhone(),
                contract.getMonthlyRent(),
                contract.getStartDate(),
                contract.getEndDate(),
                contract.getStatus(),
                tenants,
                feePrices == null ? Map.of() : feePrices,
                contract.getNote());
    }

    private ContractDtos.ContractAssetItemResponse toAssetItem(ContractAsset row, RepairStat stat) {
        Asset asset = row.getAsset();
        return new ContractDtos.ContractAssetItemResponse(
                row.getId(),
                asset.getId(),
                asset.getCode(),
                asset.getName(),
                asset.getCategory(),
                asset.getPrice(),
                asset.getCondition(),
                row.getHandoverCondition(),
                row.getReturnCondition(),
                row.getHandoverNote(),
                row.getReturnedAt() != null ? row.getReturnedAt().toLocalDate() : null,
                stat.count(),
                stat.cost());
    }

    private record RepairStat(int count, long cost) {
        private static final RepairStat EMPTY = new RepairStat(0, 0L);
    }
}
