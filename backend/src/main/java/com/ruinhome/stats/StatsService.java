package com.ruinhome.stats;

import com.ruinhome.auth.CurrentUserService;
import com.ruinhome.billing.InvoiceRepository;
import com.ruinhome.billing.InvoiceStatus;
import com.ruinhome.contract.ContractRepository;
import com.ruinhome.contract.ContractStatus;
import com.ruinhome.house.HouseRepository;
import com.ruinhome.room.RoomRepository;
import com.ruinhome.user.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

@Service
public class StatsService {

    private final HouseRepository houseRepository;
    private final RoomRepository roomRepository;
    private final ContractRepository contractRepository;
    private final InvoiceRepository invoiceRepository;
    private final CurrentUserService currentUserService;

    public StatsService(HouseRepository houseRepository, RoomRepository roomRepository,
                        ContractRepository contractRepository, InvoiceRepository invoiceRepository,
                        CurrentUserService currentUserService) {
        this.houseRepository = houseRepository;
        this.roomRepository = roomRepository;
        this.contractRepository = contractRepository;
        this.invoiceRepository = invoiceRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public StatsResponse stats() {
        var account = currentUserService.account();
        Long ownerScope = null;
        Long userScope = null;
        String areaScope = null;
        if (account.getRole() == Role.ADMIN) {
            areaScope = currentUserService.areaOrNull();
        } else if (account.getRole() == Role.MANAGER) {
            ownerScope = currentUserService.personId();
        } else if (account.getRole() == Role.USER) {
            userScope = currentUserService.personIdOrNull();
            if (userScope == null) {
                return new StatsResponse(0L, 0L, 0L, 0L, 0L, 0L);
            }
        }

        String period = YearMonth.now().toString();

        long houseCount = 0;
        long roomCount = 0;
        long vacantRoomCount = 0;
        if (account.getRole() != Role.USER) {
            houseCount = houseRepository.countActiveForScope(ownerScope, areaScope);
            roomCount = roomRepository.countActiveForScope(ownerScope, areaScope);
            vacantRoomCount = roomRepository.countVacantForScope(ownerScope, areaScope);
        }

        long activeContractCount = userScope != null
                ? contractRepository.countActiveForPerson(userScope)
                : contractRepository.countActiveForScope(ownerScope, areaScope);

        long unpaidInvoiceCount = invoiceRepository.countUnpaidForPeriod(period, ownerScope, userScope, areaScope);
        long outstandingDebt = invoiceRepository.sumDebtForPeriod(period, ownerScope, userScope, areaScope);

        return new StatsResponse(houseCount, roomCount, vacantRoomCount, activeContractCount,
                unpaidInvoiceCount, outstandingDebt);
    }
}
