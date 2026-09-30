package com.ruinhome.room;

import com.ruinhome.auth.CurrentUserService;
import com.ruinhome.contract.ContractRepository;
import com.ruinhome.contract.ContractStatus;
import com.ruinhome.house.House;
import com.ruinhome.house.HouseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final HouseRepository houseRepository;
    private final ContractRepository contractRepository;
    private final CurrentUserService currentUserService;

    public RoomService(RoomRepository roomRepository, HouseRepository houseRepository,
                       ContractRepository contractRepository, CurrentUserService currentUserService) {
        this.roomRepository = roomRepository;
        this.houseRepository = houseRepository;
        this.contractRepository = contractRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public List<RoomDtos.RoomResponse> listByHouse(Long houseId) {
        House house = findVisibleHouse(houseId);
        Set<Long> occupiedRoomIds = contractRepository
                .findActiveContractRoomIdsByHouse(house.getId());
        return roomRepository.findByHouseIdAndActiveTrueOrderByRoomNumberAsc(house.getId()).stream()
                .map(room -> toResponse(room, house, occupiedRoomIds.contains(room.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public RoomDtos.RoomResponse get(Long id) {
        Room room = find(id);
        House house = findVisibleHouse(room.getHouse().getId());
        boolean occupied = contractRepository.existsByRoomIdAndStatus(room.getId(), ContractStatus.ACTIVE);
        return toResponse(room, house, occupied);
    }

    @Transactional
    public RoomDtos.RoomResponse create(RoomDtos.RoomRequest request) {
        House house = findVisibleHouse(request.houseId());
        checkManageable();
        if (roomRepository.existsByHouseIdAndRoomNumber(house.getId(), request.roomNumber().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Số phòng đã tồn tại trong nhà này");
        }
        Room room = new Room();
        room.setHouse(house);
        room.setRoomNumber(request.roomNumber().trim());
        room.setAreaM2(request.areaM2());
        room.setNote(normalizeNote(request.note()));
        Room saved = roomRepository.save(room);
        return toResponse(saved, house, false);
    }

    @Transactional
    public RoomDtos.RoomResponse update(Long id, RoomDtos.RoomRequest request) {
        Room room = find(id);
        House house = findVisibleHouse(room.getHouse().getId());
        checkManageable();
        if (!request.houseId().equals(house.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể chuyển phòng sang nhà khác");
        }
        if (!room.getRoomNumber().equals(request.roomNumber().trim())
                && roomRepository.existsByHouseIdAndRoomNumber(house.getId(), request.roomNumber().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Số phòng đã tồn tại trong nhà này");
        }
        room.setRoomNumber(request.roomNumber().trim());
        room.setAreaM2(request.areaM2());
        room.setNote(normalizeNote(request.note()));
        Room saved = roomRepository.save(room);
        boolean occupied = contractRepository.existsByRoomIdAndStatus(saved.getId(), ContractStatus.ACTIVE);
        return toResponse(saved, house, occupied);
    }

    @Transactional
    public void softDelete(Long id) {
        Room room = find(id);
        House house = findVisibleHouse(room.getHouse().getId());
        checkManageable();
        if (contractRepository.existsByRoomIdAndStatus(room.getId(), ContractStatus.ACTIVE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phòng đang có hợp đồng hoạt động, không thể xoá");
        }
        room.setActive(false);
        roomRepository.save(room);
    }

    private Room find(Long id) {
        return roomRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phòng"));
    }

    private House findVisibleHouse(Long houseId) {
        House house = houseRepository.findByIdAndActiveTrue(houseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy nhà"));
        if (currentUserService.isAdmin()) {
            return house;
        }
        Long personId = currentUserService.personId();
        Long ownerId = house.getOwner().getId();
        Long managerId = house.getManager().getId();
        if (!personId.equals(ownerId) && !personId.equals(managerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền với nhà này");
        }
        return house;
    }

    private void checkManageable() {
        if (!currentUserService.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Chỉ quản trị viên mới có quyền với phòng này");
        }
    }

    private String normalizeNote(String note) {
        if (note == null) {
            return null;
        }
        String trimmed = note.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private RoomDtos.RoomResponse toResponse(Room room, House house, boolean occupied) {
        return new RoomDtos.RoomResponse(
                room.getId(),
                house.getId(),
                house.getName(),
                room.getRoomNumber(),
                room.getAreaM2(),
                occupied,
                room.isActive(),
                room.getNote(),
                room.getCreatedAt(),
                room.getUpdatedAt());
    }
}
