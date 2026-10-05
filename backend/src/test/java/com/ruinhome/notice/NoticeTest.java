package com.ruinhome.notice;

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
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class NoticeTest {

    @Autowired
    private NoticeService noticeService;
    @Autowired
    private ImportantNoticeRepository noticeRepository;
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

    private UserAccount linkAccount(String username, Role role, Long personId) {
        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setPasswordHash("hash");
        account.setRole(role);
        account.setEnabled(true);
        account.setRoot(false);
        if (personId != null) {
            account.setPerson(personRepository.findById(personId).orElseThrow());
        }
        return userAccountRepository.save(account);
    }

    private Long createHouse(String code, Long ownerId) {
        return houseService.create(new HouseDtos.HouseRequest(
                code, "Nha Thong Bao " + code, "Dia chi " + code, ownerId, null, null)).id();
    }

    private void contractOnHouse(Long houseId, Long holderId) {
        Long roomId = roomService.create(
                new RoomDtos.RoomRequest(houseId, "TB-01", null, null)).id();
        LocalDate today = LocalDate.now();
        contractService.create(new ContractDtos.ContractCreateRequest(
                roomId, holderId, 3000000L, today.minusDays(30), today.plusDays(100),
                null, null, null, null));
    }

    private NoticeDtos.NoticeRequest request(String title, Long houseId,
                                             LocalDateTime startsAt, LocalDateTime endsAt) {
        return new NoticeDtos.NoticeRequest(title, "Noi dung " + title, houseId, startsAt, endsAt);
    }

    private List<String> activeTitles() {
        return noticeService.active().stream().map(NoticeDtos.NoticeResponse::title).toList();
    }

    @Test
    void adminCreatesGlobalNoticeAndManagerMustPickOwnHouse() {
        as("admin", Role.ADMIN);
        Long managerPerson = personService.create(
                new PersonDtos.PersonRequest("Quan Ly Thong Bao", null, null, null)).id();
        Long otherPerson = personService.create(
                new PersonDtos.PersonRequest("Chu Nha Khac Nha", null, null, null)).id();
        Long houseA = createHouse("NHA-NB-A", managerPerson);
        Long houseB = createHouse("NHA-NB-B", otherPerson);
        linkAccount("qlytb2", Role.MANAGER, managerPerson);

        var global = noticeService.create(request("Toan he thong", null, null, null));
        assertThat(global.houseId()).isNull();

        as("qlytb2", Role.MANAGER);
        assertThatThrownBy(() -> noticeService.create(request("Khong chon nha", null, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        var own = noticeService.create(request("Nha cua toi", houseA, null, null));
        assertThat(own.houseId()).isEqualTo(houseA);

        assertThatThrownBy(() -> noticeService.create(request("Nha nguoi khac", houseB, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void activeListRespectsRoleScopeAndTime() {
        as("admin", Role.ADMIN);
        Long managerPerson = personService.create(
                new PersonDtos.PersonRequest("Quan Ly Nha A", null, null, null)).id();
        Long tenantAPerson = personService.create(
                new PersonDtos.PersonRequest("Khach Nha A", null, null, null)).id();
        Long otherPerson = personService.create(
                new PersonDtos.PersonRequest("Chu Nha B", null, null, null)).id();
        Long houseA = createHouse("NHA-NA-A", managerPerson);
        Long houseB = createHouse("NHA-NA-B", otherPerson);
        contractOnHouse(houseA, tenantAPerson);
        linkAccount("qlyna", Role.MANAGER, managerPerson);
        linkAccount("khachna", Role.USER, tenantAPerson);
        linkAccount("khachkhonglienket", Role.USER, null);

        noticeService.create(request("Chung", null, null, null));
        noticeService.create(request("Nha A", houseA, null, null));
        noticeService.create(request("Nha B", houseB, null, null));
        noticeService.create(request("Het han", houseA,
                LocalDateTime.now().minusMonths(1), LocalDateTime.now().minusDays(1)));

        as("admin", Role.ADMIN);
        var adminActive = activeTitles();
        assertThat(adminActive).contains("Chung", "Nha A", "Nha B").doesNotContain("Het han");

        as("qlyna", Role.MANAGER);
        var managerActive = activeTitles();
        assertThat(managerActive).contains("Chung", "Nha A").doesNotContain("Nha B", "Het han");

        as("khachna", Role.USER);
        var tenantActive = activeTitles();
        assertThat(tenantActive).contains("Chung", "Nha A").doesNotContain("Nha B");

        as("khachkhonglienket", Role.USER);
        assertThat(activeTitles()).isEmpty();
    }

    @Test
    void updateAndDeleteEnforceScopeAndHideFromActive() {
        as("admin", Role.ADMIN);
        Long managerPerson = personService.create(
                new PersonDtos.PersonRequest("Quan Ly Nha C", null, null, null)).id();
        Long otherPerson = personService.create(
                new PersonDtos.PersonRequest("Chu Nha D", null, null, null)).id();
        Long houseC = createHouse("NHA-NC-C", managerPerson);
        Long houseD = createHouse("NHA-ND-D", otherPerson);
        linkAccount("qlync", Role.MANAGER, managerPerson);

        var noticeD = noticeService.create(request("Nha D", houseD, null, null));
        var noticeGlobal = noticeService.create(request("Chung nua", null, null, null));

        as("qlync", Role.MANAGER);
        assertThatThrownBy(() -> noticeService.update(noticeD.id(),
                request("Sua nha khac", houseD, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> noticeService.delete(noticeGlobal.id()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        as("admin", Role.ADMIN);
        noticeService.delete(noticeD.id());
        assertThat(noticeRepository.findById(noticeD.id()).orElseThrow().isActive()).isFalse();
        assertThat(activeTitles()).doesNotContain("Nha D");
        assertThat(noticeService.list().stream().map(NoticeDtos.NoticeResponse::title).toList())
                .doesNotContain("Nha D")
                .contains("Chung nua");
    }
}
