package com.ruinhome.stats;

import com.ruinhome.billing.Invoice;
import com.ruinhome.billing.InvoiceRepository;
import com.ruinhome.billing.InvoiceStatus;
import com.ruinhome.contract.ContractDtos;
import com.ruinhome.contract.ContractService;
import com.ruinhome.house.HouseDtos;
import com.ruinhome.house.HouseService;
import com.ruinhome.person.PersonDtos;
import com.ruinhome.person.PersonRepository;
import com.ruinhome.person.PersonService;
import com.ruinhome.room.RoomDtos;
import com.ruinhome.room.RoomRepository;
import com.ruinhome.room.RoomService;
import com.ruinhome.user.Role;
import com.ruinhome.user.UserAccount;
import com.ruinhome.user.UserAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class StatsRevenueTest {

    @Autowired
    private StatsService statsService;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private PersonService personService;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private HouseService houseService;
    @Autowired
    private RoomService roomService;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private ContractService contractService;
    @Autowired
    private UserAccountRepository userAccountRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void as(String username, Role role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private Long createRoomWithContract(String houseCode, Long holderId) {
        if (holderId == null) {
            holderId = personService.create(
                    new PersonDtos.PersonRequest("Chu Nha Doanh Thu", null, null, null)).id();
        }
        Long houseId = houseService.create(new HouseDtos.HouseRequest(
                houseCode, "Nha Doanh Thu " + houseCode, "Dia chi " + houseCode,
                holderId, null, null)).id();
        Long roomId = roomService.create(
                new RoomDtos.RoomRequest(houseId, "DT-01", null, null)).id();
        LocalDate today = LocalDate.now();
        contractService.create(new ContractDtos.ContractCreateRequest(
                roomId, holderId, 3000000L, today.minusDays(30), today.plusDays(100),
                null, null, null, null));
        return roomId;
    }

    private void invoice(Long roomId, String period, long total, long paid, InvoiceStatus status) {
        Invoice entity = new Invoice();
        entity.setRoom(roomRepository.findById(roomId).orElseThrow());
        entity.setPeriod(period);
        entity.setTotalAmount(total);
        entity.setPaidAmount(paid);
        entity.setStatus(status);
        invoiceRepository.save(entity);
    }

    @Test
    void revenueSumsCollectedAndOutstandingByMonth() {
        as("admin", Role.ADMIN);
        int year = Year.now().getValue();
        Long roomId = createRoomWithContract("NHA-DT1", null);
        invoice(roomId, String.format("%d-03", year), 100000L, 40000L, InvoiceStatus.PARTIAL);

        var response = statsService.revenue(year);

        assertThat(response.year()).isEqualTo(year);
        assertThat(response.months()).hasSize(12);
        var march = response.months().get(2);
        assertThat(march.period()).isEqualTo(String.format("%d-03", year));
        assertThat(march.collected()).isEqualTo(40000L);
        assertThat(march.outstanding()).isEqualTo(60000L);
        assertThat(response.months().get(0).collected()).isZero();
        assertThat(response.months().get(0).outstanding()).isZero();
        assertThat(response.months().get(11).period()).isEqualTo(String.format("%d-12", year));
    }

    @Test
    void revenueForUserOnlyCountsOwnRoom() {
        as("admin", Role.ADMIN);
        int year = Year.now().getValue();
        var tenant = personService.create(new PersonDtos.PersonRequest(
                "Khach Doanh Thu", null, null, null));
        UserAccount account = new UserAccount();
        account.setUsername("khachdt");
        account.setPasswordHash("hash");
        account.setRole(Role.USER);
        account.setEnabled(true);
        account.setRoot(false);
        account.setPerson(personRepository.findById(tenant.id()).orElseThrow());
        userAccountRepository.save(account);

        Long ownRoomId = createRoomWithContract("NHA-DT2", tenant.id());
        invoice(ownRoomId, String.format("%d-05", year), 50000L, 20000L, InvoiceStatus.PARTIAL);
        Long otherRoomId = createRoomWithContract("NHA-DT3", null);
        invoice(otherRoomId, String.format("%d-06", year), 999999L, 999999L, InvoiceStatus.PAID);

        as("khachdt", Role.USER);
        var response = statsService.revenue(year);

        var may = response.months().get(4);
        assertThat(may.collected()).isEqualTo(20000L);
        assertThat(may.outstanding()).isEqualTo(30000L);
        var june = response.months().get(5);
        assertThat(june.collected()).isZero();
        assertThat(june.outstanding()).isZero();
    }
}
