package com.ruinhome.billing;

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
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class InvoiceDebtTest {

    @Autowired
    private InvoiceService invoiceService;
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

    private Long createDebtInvoice(String houseCode, Long managerId, int dueDateOffsetDays,
                                   InvoiceStatus status, long paid) {
        Long ownerId = personService.create(
                new PersonDtos.PersonRequest("Chu Nha " + houseCode, null, null, null)).id();
        Long houseId = houseService.create(new HouseDtos.HouseRequest(
                houseCode, "Nha " + houseCode, "Dia chi " + houseCode,
                ownerId, managerId, null)).id();
        Long roomId = roomService.create(
                new RoomDtos.RoomRequest(houseId, "CN-01", null, null)).id();
        LocalDate today = LocalDate.now();
        contractService.create(new ContractDtos.ContractCreateRequest(
                roomId, ownerId, 3000000L, today.minusDays(30), today.plusDays(100),
                null, null, null, null));

        Invoice invoice = new Invoice();
        invoice.setRoom(roomRepository.findById(roomId).orElseThrow());
        invoice.setPeriod(YearMonth.now().toString());
        invoice.setTotalAmount(500000L);
        invoice.setPaidAmount(paid);
        invoice.setStatus(status);
        invoice.setDueDate(today.plusDays(dueDateOffsetDays));
        return invoiceRepository.save(invoice).getId();
    }

    @Test
    void debtsClassifiesLevelsAndSortsWorstFirst() {
        as("admin", Role.ADMIN);
        createDebtInvoice("NHA-CN1", null, -20, InvoiceStatus.UNPAID, 0);
        createDebtInvoice("NHA-CN2", null, -10, InvoiceStatus.PARTIAL, 100000L);
        createDebtInvoice("NHA-CN3", null, -3, InvoiceStatus.UNPAID, 0);
        createDebtInvoice("NHA-CN4", null, 3, InvoiceStatus.UNPAID, 0);
        createDebtInvoice("NHA-CN5", null, -30, InvoiceStatus.PAID, 500000L);

        var page = invoiceService.debts(null, null, 0, 100);

        var items = page.getContent().stream()
                .filter(debt -> debt.houseName().startsWith("Nha NHA-CN"))
                .toList();
        assertThat(items).hasSize(4);
        assertThat(items.get(0).level()).isEqualTo(DebtLevel.DEBT);
        assertThat(items.get(0).overdueDays()).isEqualTo(20);
        assertThat(items.get(1).level()).isEqualTo(DebtLevel.LATE);
        assertThat(items.get(1).overdueDays()).isEqualTo(10);
        assertThat(items.get(1).remainingAmount()).isEqualTo(400000L);
        assertThat(items.get(1).status()).isEqualTo(InvoiceStatus.PARTIAL);
        assertThat(items.get(2).level()).isEqualTo(DebtLevel.OVERDUE);
        assertThat(items.get(2).overdueDays()).isEqualTo(3);
        assertThat(items.get(3).level()).isEqualTo(DebtLevel.NOT_DUE);
        assertThat(items.get(3).overdueDays()).isZero();
        assertThat(items.get(3).tenantName()).isEqualTo("Chu Nha NHA-CN4");
    }

    @Test
    void debtsLevelFilterReturnsOnlyMatchingBucket() {
        as("admin", Role.ADMIN);
        createDebtInvoice("NHA-FL1", null, -20, InvoiceStatus.UNPAID, 0);
        createDebtInvoice("NHA-FL2", null, -10, InvoiceStatus.UNPAID, 0);

        var debts = invoiceService.debts(DebtLevel.DEBT, null, 0, 100);
        var lates = invoiceService.debts(DebtLevel.LATE, null, 0, 100);

        assertThat(debts.getContent().stream()
                .filter(debt -> "Nha NHA-FL1".equals(debt.houseName()))).hasSize(1);
        assertThat(debts.getContent().stream()
                .filter(debt -> "Nha NHA-FL2".equals(debt.houseName()))).isEmpty();
        assertThat(lates.getContent().stream()
                .filter(debt -> "Nha NHA-FL2".equals(debt.houseName()))).hasSize(1);
        assertThat(lates.getContent().stream()
                .filter(debt -> "Nha NHA-FL1".equals(debt.houseName()))).isEmpty();
    }

    @Test
    void managerSeesOnlyOwnHouses() {
        as("admin", Role.ADMIN);
        Long managerAPersonId = personService.create(
                new PersonDtos.PersonRequest("Quan Ly A", null, null, null)).id();
        Long managerBPersonId = personService.create(
                new PersonDtos.PersonRequest("Quan Ly B", null, null, null)).id();
        UserAccount account = new UserAccount();
        account.setUsername("quanlycongno");
        account.setPasswordHash("hash");
        account.setRole(Role.MANAGER);
        account.setEnabled(true);
        account.setRoot(false);
        account.setPerson(personRepository.findById(managerAPersonId).orElseThrow());
        userAccountRepository.save(account);

        createDebtInvoice("NHA-MA", managerBPersonId, -20, InvoiceStatus.UNPAID, 0);
        createDebtInvoice("NHA-MB", managerAPersonId, -10, InvoiceStatus.UNPAID, 0);

        as("quanlycongno", Role.MANAGER);
        var page = invoiceService.debts(null, null, 0, 100);

        assertThat(page.getContent().stream()
                .filter(debt -> debt.houseName().startsWith("Nha NHA-M"))).hasSize(1);
        assertThat(page.getContent().stream()
                .filter(debt -> "Nha NHA-MB".equals(debt.houseName()))).hasSize(1);
        assertThat(page.getContent().stream()
                .filter(debt -> "Nha NHA-MA".equals(debt.houseName()))).isEmpty();
    }

    @Test
    void adminUpdatesDueDateAndPaidInvoiceIsRejected() {
        as("admin", Role.ADMIN);
        Long invoiceId = createDebtInvoice("NHA-PD1", null, -20, InvoiceStatus.UNPAID, 0);
        var mine = invoiceService.debts(null, null, 0, 100).getContent().stream()
                .filter(debt -> "Nha NHA-PD1".equals(debt.houseName()))
                .toList();
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).level()).isEqualTo(DebtLevel.DEBT);

        var updated = invoiceService.updateDueDate(invoiceId,
                new BillingDtos.DueDateRequest(LocalDate.now().plusDays(5)));

        assertThat(updated.dueDate()).isEqualTo(LocalDate.now().plusDays(5));
        var refreshed = invoiceService.debts(null, null, 0, 100).getContent().stream()
                .filter(debt -> "Nha NHA-PD1".equals(debt.houseName()))
                .toList();
        assertThat(refreshed).hasSize(1);
        assertThat(refreshed.get(0).level()).isEqualTo(DebtLevel.NOT_DUE);
        assertThat(refreshed.get(0).dueDate()).isEqualTo(LocalDate.now().plusDays(5));

        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow();
        invoice.setStatus(InvoiceStatus.PAID);
        invoiceRepository.save(invoice);
        assertThatThrownBy(() -> invoiceService.updateDueDate(invoiceId,
                new BillingDtos.DueDateRequest(LocalDate.now().plusDays(9))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }
}
