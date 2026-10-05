package com.ruinhome.billing;

import com.ruinhome.contract.ContractDtos;
import com.ruinhome.contract.ContractService;
import com.ruinhome.house.HouseDtos;
import com.ruinhome.house.HouseService;
import com.ruinhome.person.PersonDtos;
import com.ruinhome.person.PersonRepository;
import com.ruinhome.person.PersonService;
import com.ruinhome.room.RoomDtos;
import com.ruinhome.room.RoomService;
import com.ruinhome.user.Role;
import com.ruinhome.user.UserAccount;
import com.ruinhome.user.UserAccountRepository;
import com.ruinhome.user.UserDtos;
import com.ruinhome.user.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class InvoiceOwnerBankAccountTest {

    @Autowired
    private InvoiceService invoiceService;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private HouseService houseService;
    @Autowired
    private PersonService personService;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private RoomService roomService;
    @Autowired
    private ContractService contractService;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private UserService userService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void asRoot() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private record Fixture(Long roomId, Long ownerId) {
    }

    private Fixture createRoom(String houseCode, String roomNumber) {
        var owner = personService.create(new PersonDtos.PersonRequest("Chu Nha " + houseCode, null, null, null));
        var house = houseService.create(new HouseDtos.HouseRequest(
                houseCode, "Nha " + houseCode, "Dia chi " + houseCode, owner.id(), null, null));
        var room = roomService.create(new RoomDtos.RoomRequest(house.id(), roomNumber, null, null));
        contractService.create(new ContractDtos.ContractCreateRequest(
                room.id(), owner.id(), 3_000_000L,
                LocalDate.of(2030, 1, 1), LocalDate.of(2030, 12, 31), null, null, null, null));
        return new Fixture(room.id(), owner.id());
    }

    private void linkOwnerAccount(Long personId, String bankAccount) {
        UserAccount account = new UserAccount();
        account.setUsername("owneracc-" + personId);
        account.setPasswordHash("hash");
        account.setRole(Role.USER);
        account.setEnabled(true);
        account.setRoot(false);
        account.setPerson(personRepository.findById(personId).orElseThrow());
        account.setBankAccount(bankAccount);
        userAccountRepository.save(account);
    }

    @Test
    void invoiceDetailCarriesOwnerBankAccount() {
        asRoot();
        Fixture fixture = createRoom("NHA-QR-1", "P301");
        linkOwnerAccount(fixture.ownerId(), "9988776655");

        invoiceService.generate("2030-03");
        Long invoiceId = invoiceRepository.findByRoomIdAndPeriod(fixture.roomId(), "2030-03")
                .orElseThrow().getId();

        var detail = invoiceService.get(invoiceId);
        assertThat(detail.bankAccount()).isEqualTo("9988776655");

        userAccountRepository.findFirstByPersonIdAndBankAccountIsNotNullOrderByIdAsc(fixture.ownerId())
                .ifPresent(account -> {
                    account.setBankAccount(null);
                    userAccountRepository.save(account);
                });
        assertThat(invoiceService.get(invoiceId).bankAccount()).isNull();
    }

    @Test
    void invoiceDetailWithoutLinkedAccountHasNoBankAccount() {
        asRoot();
        Fixture fixture = createRoom("NHA-QR-2", "P302");

        invoiceService.generate("2030-03");
        Long invoiceId = invoiceRepository.findByRoomIdAndPeriod(fixture.roomId(), "2030-03")
                .orElseThrow().getId();

        assertThat(invoiceService.get(invoiceId).bankAccount()).isNull();
    }

    @Test
    void userServiceSavesAndClearsBankAccount() {
        asRoot();
        var created = userService.create(new UserDtos.UserCreateRequest(
                "qruser01", "matkhau123", "USER", null, null, null, " 0123456789 ", null));
        assertThat(created.bankAccount()).isEqualTo("0123456789");

        var updated = userService.update(created.id(), new UserDtos.UserUpdateRequest(
                null, "USER", null, null, null, "   ", null));
        assertThat(updated.bankAccount()).isNull();
    }
}
