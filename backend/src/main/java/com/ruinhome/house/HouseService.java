package com.ruinhome.house;

import com.ruinhome.auth.CurrentUserService;
import com.ruinhome.person.Person;
import com.ruinhome.person.PersonRepository;
import com.ruinhome.room.RoomRepository;
import com.ruinhome.user.Role;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class HouseService {

    private final HouseRepository houseRepository;
    private final PersonRepository personRepository;
    private final RoomRepository roomRepository;
    private final CurrentUserService currentUserService;

    public HouseService(HouseRepository houseRepository, PersonRepository personRepository,
                        RoomRepository roomRepository, CurrentUserService currentUserService) {
        this.houseRepository = houseRepository;
        this.personRepository = personRepository;
        this.roomRepository = roomRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public List<HouseDtos.HouseResponse> list() {
        var account = currentUserService.account();
        if (account.getRole() == Role.USER) {
            return List.of();
        }
        if (account.getRole() == Role.ADMIN) {
            return houseRepository.findByActiveTrueOrderByCodeAsc().stream()
                    .map(this::toResponse).toList();
        }
        Long personId = currentUserService.personId();
        return houseRepository.findActiveByOwnerOrManager(personId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public HouseDtos.HouseResponse get(Long id) {
        return toResponse(findVisible(id));
    }

    @Transactional
    public HouseDtos.HouseResponse create(HouseDtos.HouseRequest request) {
        if (houseRepository.existsByCode(request.code().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã nhà đã tồn tại");
        }
        House house = new House();
        house.setCode(request.code().trim());
        house.setName(request.name().trim());
        house.setAddress(request.address().trim());
        house.setNote(normalizeNote(request.note()));
        house.setOwner(findPerson(request.ownerId()));
        house.setManager(request.managerId() != null ? findPerson(request.managerId())
                : house.getOwner());
        return toResponse(houseRepository.save(house));
    }

    @Transactional
    public HouseDtos.HouseResponse update(Long id, HouseDtos.HouseRequest request) {
        House house = findVisible(id);
        checkManageable();
        if (!house.getCode().equals(request.code().trim())
                && houseRepository.existsByCode(request.code().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã nhà đã tồn tại");
        }
        house.setCode(request.code().trim());
        house.setName(request.name().trim());
        house.setAddress(request.address().trim());
        house.setNote(normalizeNote(request.note()));
        house.setOwner(findPerson(request.ownerId()));
        house.setManager(request.managerId() != null ? findPerson(request.managerId())
                : house.getOwner());
        return toResponse(houseRepository.save(house));
    }

    @Transactional
    public void softDelete(Long id) {
        House house = findVisible(id);
        checkManageable();
        house.setActive(false);
        houseRepository.save(house);
    }

    private House findVisible(Long id) {
        House house = houseRepository.findByIdAndActiveTrue(id)
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
                    "Chỉ quản trị viên mới có quyền với nhà này");
        }
    }

    private Person findPerson(Long personId) {
        return personRepository.findById(personId)
                .filter(Person::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy người dùng làm chủ nhà"));
    }

    private String normalizeNote(String note) {
        if (note == null) {
            return null;
        }
        String trimmed = note.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private HouseDtos.HouseResponse toResponse(House house) {
        long roomCount = roomRepository.countByHouseIdAndActiveTrue(house.getId());
        return new HouseDtos.HouseResponse(
                house.getId(),
                house.getCode(),
                house.getName(),
                house.getAddress(),
                house.getOwner().getId(),
                house.getOwner().getFullName(),
                house.getManager().getId(),
                house.getManager().getFullName(),
                roomCount,
                house.isActive(),
                house.getNote(),
                house.getCreatedAt(),
                house.getUpdatedAt());
    }
}
