package com.ruinhome.notice;

import com.ruinhome.auth.CurrentUserService;
import com.ruinhome.house.House;
import com.ruinhome.house.HouseRepository;
import com.ruinhome.user.Role;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NoticeService {

    private final ImportantNoticeRepository noticeRepository;
    private final HouseRepository houseRepository;
    private final CurrentUserService currentUserService;

    public NoticeService(ImportantNoticeRepository noticeRepository,
                         HouseRepository houseRepository,
                         CurrentUserService currentUserService) {
        this.noticeRepository = noticeRepository;
        this.houseRepository = houseRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public List<NoticeDtos.NoticeResponse> list() {
        String areaScope = null;
        Long managerScope = null;
        if (currentUserService.account().getRole() == Role.MANAGER) {
            managerScope = currentUserService.personId();
        } else if (!currentUserService.isRoot()) {
            areaScope = currentUserService.areaOrNull();
        }
        return noticeRepository.findAllForScope(areaScope, managerScope).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NoticeDtos.NoticeResponse> active() {
        Role role = currentUserService.account().getRole();
        LocalDateTime now = LocalDateTime.now();
        List<ImportantNotice> notices;
        if (role == Role.USER) {
            Long personId = currentUserService.personIdOrNull();
            if (personId == null) {
                return List.of();
            }
            notices = noticeRepository.findActiveForTenant(now, personId);
        } else if (role == Role.MANAGER) {
            notices = noticeRepository.findActiveForManager(now, currentUserService.personId());
        } else {
            String areaScope = currentUserService.isRoot() ? null : currentUserService.areaOrNull();
            notices = noticeRepository.findActive(now, areaScope);
        }
        return notices.stream().map(this::toResponse).toList();
    }

    @Transactional
    public NoticeDtos.NoticeResponse create(NoticeDtos.NoticeRequest request) {
        validatePeriod(request.startsAt(), request.endsAt());
        ImportantNotice notice = new ImportantNotice();
        notice.setTitle(request.title().trim());
        notice.setContent(request.content().trim());
        applyHouse(notice, request.houseId());
        notice.setStartsAt(request.startsAt());
        notice.setEndsAt(request.endsAt());
        return toResponse(noticeRepository.save(notice));
    }

    @Transactional
    public NoticeDtos.NoticeResponse update(Long id, NoticeDtos.NoticeRequest request) {
        validatePeriod(request.startsAt(), request.endsAt());
        ImportantNotice notice = findManageable(id);
        notice.setTitle(request.title().trim());
        notice.setContent(request.content().trim());
        applyHouse(notice, request.houseId());
        notice.setStartsAt(request.startsAt());
        notice.setEndsAt(request.endsAt());
        return toResponse(noticeRepository.save(notice));
    }

    @Transactional
    public void delete(Long id) {
        ImportantNotice notice = findManageable(id);
        notice.setActive(false);
        noticeRepository.save(notice);
    }

    private ImportantNotice findManageable(Long id) {
        ImportantNotice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy thông báo"));
        if (currentUserService.account().getRole() == Role.MANAGER) {
            if (notice.getHouse() == null || !isManagedHouse(notice.getHouse())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thông báo");
            }
        } else if (!currentUserService.isRoot() && notice.getHouse() != null) {
            currentUserService.checkArea(notice.getHouse().getAreaAdmin());
        }
        return notice;
    }

    private void applyHouse(ImportantNotice notice, Long houseId) {
        if (houseId == null) {
            if (currentUserService.account().getRole() == Role.MANAGER) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Quản lý phải chọn nhà cụ thể");
            }
            notice.setHouse(null);
            return;
        }
        House house = houseRepository.findById(houseId)
                .filter(House::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy nhà"));
        if (currentUserService.account().getRole() == Role.MANAGER) {
            if (!isManagedHouse(house)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Bạn chỉ thao tác được nhà của mình");
            }
        } else if (!currentUserService.isRoot()) {
            currentUserService.checkArea(house.getAreaAdmin());
        }
        notice.setHouse(house);
    }

    private boolean isManagedHouse(House house) {
        Long personId = currentUserService.personId();
        return (house.getOwner() != null && personId.equals(house.getOwner().getId()))
                || (house.getManager() != null && personId.equals(house.getManager().getId()));
    }

    private void validatePeriod(LocalDateTime startsAt, LocalDateTime endsAt) {
        if (startsAt != null && endsAt != null && !endsAt.isAfter(startsAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Thời gian kết thúc phải sau thời gian bắt đầu");
        }
    }

    private NoticeDtos.NoticeResponse toResponse(ImportantNotice notice) {
        House house = notice.getHouse();
        return new NoticeDtos.NoticeResponse(
                notice.getId(),
                notice.getTitle(),
                notice.getContent(),
                house != null ? house.getId() : null,
                house != null ? house.getName() : null,
                notice.getStartsAt(),
                notice.getEndsAt(),
                notice.isActive(),
                notice.getCreatedAt());
    }
}
