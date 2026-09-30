package com.ruinhome.contract;

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
    private final RoomRepository roomRepository;
    private final HouseRepository houseRepository;
    private final PersonRepository personRepository;
    private final CurrentUserService currentUserService;

    public ContractService(ContractRepository contractRepository,
                           ContractFeePriceRepository contractFeePriceRepository,
                           RoomRepository roomRepository,
                           HouseRepository houseRepository, PersonRepository personRepository,
                           CurrentUserService currentUserService) {
        this.contractRepository = contractRepository;
        this.contractFeePriceRepository = contractFeePriceRepository;
        this.roomRepository = roomRepository;
        this.houseRepository = houseRepository;
        this.personRepository = personRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public Page<ContractDtos.ContractResponse> search(Long houseId, Long roomId, ContractStatus status,
                                                      int page, int size) {
        contractRepository.expireOverdue(ContractStatus.ACTIVE, ContractStatus.EXPIRED, LocalDate.now());

        Long ownerScope = null;
        Long userScope = null;
        var role = currentUserService.account().getRole();
        if (role == Role.ADMIN) {
            // khong gioi han
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
        var result = contractRepository.search(houseId, roomId, status, ownerScope, userScope, pageable);
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
        return toResponse(saved);
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

    private Map<Long, Map<String, Long>> loadFeePrices(List<Long> contractIds) {
        if (contractIds.isEmpty()) {
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
}
