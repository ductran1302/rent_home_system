package com.ruinhome.billing;

import com.ruinhome.auth.CurrentUserService;
import com.ruinhome.house.House;
import com.ruinhome.room.Room;
import com.ruinhome.room.RoomRepository;
import com.ruinhome.user.Role;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class MeterService {

    private final MeterReadingRepository meterReadingRepository;
    private final RoomRepository roomRepository;
    private final FeeTypeRepository feeTypeRepository;
    private final CurrentUserService currentUserService;

    public MeterService(MeterReadingRepository meterReadingRepository, RoomRepository roomRepository,
                        FeeTypeRepository feeTypeRepository, CurrentUserService currentUserService) {
        this.meterReadingRepository = meterReadingRepository;
        this.roomRepository = roomRepository;
        this.feeTypeRepository = feeTypeRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public List<BillingDtos.MeterResponse> search(String period, Long roomId) {
        if (period != null) {
            BillingSupport.validatePeriod(period);
        }
        Long ownerScope = currentUserService.isAdmin() ? null : currentUserService.personId();
        return meterReadingRepository.search(period, roomId, ownerScope).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public BillingDtos.MeterResponse create(BillingDtos.MeterCreateRequest request) {
        BillingSupport.validatePeriod(request.period());
        Room room = findVisibleRoom(request.roomId());
        FeeType feeType = feeTypeRepository.findById(request.feeTypeId())
                .filter(FeeType::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy loại phí"));
        if (meterReadingRepository.existsByRoomIdAndFeeTypeIdAndPeriod(
                room.getId(), feeType.getId(), request.period())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Phòng này đã có chỉ số cho kỳ " + request.period());
        }
        MeterReading reading = new MeterReading();
        reading.setRoom(room);
        reading.setFeeType(feeType);
        reading.setPeriod(request.period());
        reading.setReading(request.reading());
        reading.setNote(blankToNull(request.note()));
        return toResponse(meterReadingRepository.save(reading));
    }

    @Transactional
    public BillingDtos.MeterResponse update(Long id, BillingDtos.MeterUpdateRequest request) {
        MeterReading reading = meterReadingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy chỉ số"));
        findVisibleRoom(reading.getRoom().getId());
        reading.setReading(request.reading());
        reading.setNote(blankToNull(request.note()));
        return toResponse(meterReadingRepository.save(reading));
    }

    private Room findVisibleRoom(Long roomId) {
        Room room = roomRepository.findByIdAndActiveTrue(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy phòng"));
        if (currentUserService.isAdmin()) {
            return room;
        }
        House house = room.getHouse();
        var account = currentUserService.account();
        if (account.getRole() == Role.USER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền với phòng này");
        }
        Long personId = currentUserService.personId();
        if (!personId.equals(house.getOwner().getId()) && !personId.equals(house.getManager().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền với phòng này");
        }
        return room;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BillingDtos.MeterResponse toResponse(MeterReading reading) {
        Room room = reading.getRoom();
        House house = room.getHouse();
        return new BillingDtos.MeterResponse(
                reading.getId(),
                room.getId(),
                room.getRoomNumber(),
                house.getName(),
                reading.getFeeType().getId(),
                reading.getFeeType().getCode(),
                reading.getFeeType().getName(),
                reading.getFeeType().getUnit(),
                reading.getPeriod(),
                reading.getReading(),
                reading.getNote());
    }
}
