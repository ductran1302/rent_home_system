package com.ruinhome.notification;

import com.ruinhome.auth.CurrentUserService;
import com.ruinhome.billing.BillingDtos;
import com.ruinhome.billing.FeeType;
import com.ruinhome.billing.FeeTypeRepository;
import com.ruinhome.billing.Invoice;
import com.ruinhome.billing.InvoiceRepository;
import com.ruinhome.billing.InvoiceService;
import com.ruinhome.billing.InvoiceStatus;
import com.ruinhome.billing.MeterReading;
import com.ruinhome.billing.MeterReadingRepository;
import com.ruinhome.contract.ContractDtos;
import com.ruinhome.contract.ContractRepository;
import com.ruinhome.contract.ContractService;
import com.ruinhome.contract.ContractStatus;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class NotificationTest {

    @Autowired
    private NotificationService notificationService;
    @Autowired
    private NotificationScanService scanService;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private InvoiceService invoiceService;
    @Autowired
    private MeterReadingRepository meterReadingRepository;
    @Autowired
    private FeeTypeRepository feeTypeRepository;
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
    private ContractRepository contractRepository;
    @Autowired
    private CurrentUserService currentUserService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void as(String username, Role role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    private UserAccount createTenantAdmin(String username) {
        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setPasswordHash("hash");
        account.setRole(Role.ADMIN);
        account.setEnabled(true);
        account.setAreaAdmin(username);
        account.setRoot(false);
        return userAccountRepository.save(account);
    }

    private Long createRoomWithContract(int daysFromNow, String houseCode, Long holderId) {
        if (holderId == null) {
            holderId = personService.create(
                    new PersonDtos.PersonRequest("Chu Nha Thong Bao", null, null, null)).id();
        }
        Long houseId = houseService.create(new HouseDtos.HouseRequest(
                houseCode, "Nha Tro Thong Bao " + houseCode, "Dia chi " + houseCode,
                holderId, null, null)).id();
        Long roomId = roomService.create(
                new RoomDtos.RoomRequest(houseId, "TB-01", null, null)).id();
        LocalDate today = LocalDate.now();
        contractService.create(new ContractDtos.ContractCreateRequest(
                roomId, holderId, 3000000L, today.minusDays(30), today.plusDays(daysFromNow),
                null, null, null, null));
        return roomId;
    }

    private long accountId(String username) {
        return userAccountRepository.findByUsername(username).orElseThrow().getId();
    }

    @Test
    void scanNotifiesAreaStaffAndRootAboutExpiringContract() {
        createTenantAdmin("tbarea");
        createTenantAdmin("tbarea2");
        as("tbarea", Role.ADMIN);
        Long roomId = createRoomWithContract(3, "NHA-TB", null);

        int createdFirst = scanService.scan();
        int createdSecond = scanService.scan();

        assertThat(createdSecond).isZero();
        assertThat(createdFirst).isGreaterThanOrEqualTo(2);
        var contract = contractRepository.findByRoomIdAndStatus(roomId, ContractStatus.ACTIVE)
                .orElseThrow();
        String key = "CONTRACT_EXPIRING:" + contract.getId() + ":" + LocalDate.now().plusDays(3);
        assertThat(notificationRepository.existsByUserIdAndDedupKey(accountId("admin"), key)).isTrue();
        assertThat(notificationRepository.existsByUserIdAndDedupKey(accountId("tbarea"), key)).isTrue();
        assertThat(notificationRepository.existsByUserIdAndDedupKey(accountId("tbarea2"), key)).isFalse();
    }

    @Test
    void listReadFlowAndNotFoundForOtherUser() {
        as("admin", Role.ADMIN);
        var admin = currentUserService.account();
        Notification first = new Notification();
        first.setUser(admin);
        first.setType(NotificationType.CONTRACT_EXPIRING);
        first.setTitle("Thong bao mot");
        first.setBody("Noi dung thong bao");
        first.setLink("/contracts");
        first.setDedupKey("TEST:flow:1");
        first.setRead(false);
        notificationRepository.save(first);

        assertThat(notificationService.list(false, 0, 100).getContent())
                .extracting(NotificationDtos.NotificationResponse::title)
                .contains("Thong bao mot");
        long before = notificationService.unreadCount();
        assertThat(before).isGreaterThanOrEqualTo(1);

        notificationService.markRead(first.getId());
        assertThat(notificationService.unreadCount()).isEqualTo(before - 1);

        Notification second = new Notification();
        second.setUser(admin);
        second.setType(NotificationType.INVOICE_UNPAID);
        second.setTitle("Thong bao hai");
        second.setDedupKey("TEST:flow:2");
        second.setRead(false);
        notificationRepository.save(second);
        notificationService.markAllRead();
        assertThat(notificationService.unreadCount()).isZero();

        createTenantAdmin("tbarea3");
        Notification other = new Notification();
        other.setUser(userAccountRepository.findByUsername("tbarea3").orElseThrow());
        other.setType(NotificationType.INVOICE_UNPAID);
        other.setTitle("Cua khu vuc khac");
        other.setDedupKey("TEST:flow:other");
        other.setRead(false);
        notificationRepository.save(other);

        assertThatThrownBy(() -> notificationService.markRead(other.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void scanNotifiesAboutExpiringManagerAccount() {
        UserAccount manager = new UserAccount();
        manager.setUsername("quanlytb");
        manager.setPasswordHash("hash");
        manager.setRole(Role.MANAGER);
        manager.setEnabled(true);
        manager.setRoot(false);
        manager.setManagerEndDate(LocalDate.now().plusDays(3));
        userAccountRepository.save(manager);

        scanService.scan();

        String key = "MANAGER_EXPIRING:" + manager.getId() + ":" + LocalDate.now().plusDays(3);
        assertThat(notificationRepository.existsByUserIdAndDedupKey(accountId("admin"), key)).isTrue();
        assertThat(notificationRepository.existsByUserIdAndDedupKey(accountId("quanlytb"), key)).isTrue();
    }

    @Test
    void publishNotifiesLinkedTenantUser() {
        as("admin", Role.ADMIN);
        var tenant = personService.create(new PersonDtos.PersonRequest(
                "Khach Hang Thong Bao", null, null, null));
        UserAccount tenantUser = new UserAccount();
        tenantUser.setUsername("khachtb");
        tenantUser.setPasswordHash("hash");
        tenantUser.setRole(Role.USER);
        tenantUser.setEnabled(true);
        tenantUser.setRoot(false);
        tenantUser.setPerson(personRepository.findById(tenant.id()).orElseThrow());
        userAccountRepository.save(tenantUser);

        Long roomId = createRoomWithContract(100, "NHA-TB2", tenant.id());
        Invoice invoice = new Invoice();
        invoice.setRoom(roomRepository.findById(roomId).orElseThrow());
        invoice.setPeriod(YearMonth.now().toString());
        invoice.setTotalAmount(100000L);
        invoice.setPaidAmount(0L);
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoiceRepository.save(invoice);

        invoiceService.publish(invoice.getId());

        assertThat(notificationRepository.existsByUserIdAndDedupKey(
                accountId("khachtb"), "INVOICE_PUBLISHED:" + invoice.getId())).isTrue();
    }

    @Test
    void payNotifiesLinkedTenantUserAboutPartialAndFullPayment() {
        as("admin", Role.ADMIN);
        var tenant = personService.create(new PersonDtos.PersonRequest(
                "Khach Hang Nhan Tien", null, null, null));
        UserAccount tenantUser = new UserAccount();
        tenantUser.setUsername("khachnt");
        tenantUser.setPasswordHash("hash");
        tenantUser.setRole(Role.USER);
        tenantUser.setEnabled(true);
        tenantUser.setRoot(false);
        tenantUser.setPerson(personRepository.findById(tenant.id()).orElseThrow());
        userAccountRepository.save(tenantUser);

        Long roomId = createRoomWithContract(100, "NHA-TB4", tenant.id());
        Invoice invoice = new Invoice();
        invoice.setRoom(roomRepository.findById(roomId).orElseThrow());
        invoice.setPeriod(YearMonth.now().toString());
        invoice.setTotalAmount(100000L);
        invoice.setPaidAmount(0L);
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoiceRepository.save(invoice);
        invoiceService.publish(invoice.getId());
        long tenantId = accountId("khachnt");

        invoiceService.pay(invoice.getId(), new BillingDtos.PaymentRequest(40000L));

        String partialKey = "INVOICE_PAYMENT:" + invoice.getId() + ":40000";
        assertThat(notificationRepository.existsByUserIdAndDedupKey(tenantId, partialKey)).isTrue();
        Notification partial = notificationsOf(tenantId, NotificationType.INVOICE_PAYMENT_RECEIVED);
        assertThat(partial.getTitle()).isEqualTo("Ghi nhận đóng tiền");
        assertThat(partial.getBody()).contains("TB-01").contains("Còn thiếu 60.000 đ.");
        assertThat(partial.getLink()).isEqualTo("/billing");

        invoiceService.pay(invoice.getId(), new BillingDtos.PaymentRequest(60000L));

        String fullKey = "INVOICE_PAYMENT:" + invoice.getId() + ":100000";
        assertThat(notificationRepository.existsByUserIdAndDedupKey(tenantId, fullKey)).isTrue();
        assertThat(notificationRepository.existsByUserIdAndDedupKey(
                accountId("khachnt"), "INVOICE_PUBLISHED:" + invoice.getId())).isTrue();
        Notification full = notificationsOf(tenantId, NotificationType.INVOICE_PAYMENT_RECEIVED);
        assertThat(full.getBody()).contains("Đã nhận đủ tiền phòng TB-01");
    }

    private Notification notificationsOf(long userId, NotificationType type) {
        return notificationRepository.findByUserId(userId,
                        org.springframework.data.domain.PageRequest.of(0, 50,
                                org.springframework.data.domain.Sort.by(
                                        org.springframework.data.domain.Sort.Direction.DESC, "id")))
                .getContent().stream()
                .filter(notification -> notification.getType() == type)
                .findFirst().orElseThrow();
    }

    @Test
    void scanNotifiesAboutMissingMeterReading() {
        as("admin", Role.ADMIN);
        Long roomId = createRoomWithContract(100, "NHA-TB3", null);
        FeeType dien = feeTypeRepository.findByCode("DIEN").orElseThrow();
        MeterReading reading = new MeterReading();
        reading.setRoom(roomRepository.findById(roomId).orElseThrow());
        reading.setPeriod(YearMonth.now().minusMonths(1).toString());
        reading.setFeeType(dien);
        reading.setReading(new BigDecimal("100"));
        meterReadingRepository.save(reading);

        scanService.scan();

        String key = "METER_MISSING:" + roomId + ":DIEN:" + YearMonth.now();
        assertThat(notificationRepository.existsByUserIdAndDedupKey(accountId("admin"), key)).isTrue();
    }
}
