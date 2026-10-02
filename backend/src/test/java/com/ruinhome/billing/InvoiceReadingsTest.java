package com.ruinhome.billing;

import com.ruinhome.contract.ContractDtos;
import com.ruinhome.contract.ContractService;
import com.ruinhome.house.HouseDtos;
import com.ruinhome.house.HouseService;
import com.ruinhome.person.PersonDtos;
import com.ruinhome.person.PersonService;
import com.ruinhome.room.RoomDtos;
import com.ruinhome.room.RoomRepository;
import com.ruinhome.room.RoomService;
import com.ruinhome.user.Role;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class InvoiceReadingsTest {

    @Autowired
    private InvoiceService invoiceService;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private HouseService houseService;
    @Autowired
    private PersonService personService;
    @Autowired
    private RoomService roomService;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private ContractService contractService;
    @Autowired
    private FeeTypeRepository feeTypeRepository;
    @Autowired
    private FeeRateRepository feeRateRepository;
    @Autowired
    private MeterReadingRepository meterReadingRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void asRoot() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private Long createRoom(String houseCode, String roomNumber) {
        return createRoom(houseCode, roomNumber, null);
    }

    private Long createRoom(String houseCode, String roomNumber, java.util.Map<String, Long> feePrices) {
        return createRoom(houseCode, roomNumber, LocalDate.of(2030, 1, 1), feePrices);
    }

    private Long createRoom(String houseCode, String roomNumber, LocalDate contractStart,
                            java.util.Map<String, Long> feePrices) {
        var owner = personService.create(new PersonDtos.PersonRequest("Chu Nha " + houseCode, null, null, null));
        var house = houseService.create(new HouseDtos.HouseRequest(
                houseCode, "Nha " + houseCode, "Dia chi " + houseCode, owner.id(), null, null));
        var room = roomService.create(new RoomDtos.RoomRequest(house.id(), roomNumber, null, null));
        contractService.create(new ContractDtos.ContractCreateRequest(
                room.id(), owner.id(), 3_000_000L,
                contractStart, LocalDate.of(2030, 12, 31), null, null, feePrices, null));
        return room.id();
    }

    private void meter(Long roomId, String feeCode, String period, String reading) {
        MeterReading row = new MeterReading();
        row.setRoom(roomRepository.findById(roomId).orElseThrow());
        row.setFeeType(feeTypeRepository.findByCode(feeCode).orElseThrow());
        row.setPeriod(period);
        row.setReading(new BigDecimal(reading));
        meterReadingRepository.save(row);
    }

    private void rate(String feeCode, String period, long price) {
        FeeRate row = new FeeRate();
        row.setFeeType(feeTypeRepository.findByCode(feeCode).orElseThrow());
        row.setPeriod(period);
        row.setPrice(price);
        row.setActive(true);
        feeRateRepository.save(row);
    }

    private Invoice invoiceOf(Long roomId, String period) {
        return invoiceRepository.findByRoomIdAndPeriod(roomId, period).orElseThrow();
    }

    private InvoiceLine dienLine(Invoice invoice) {
        return invoice.getLines().stream()
                .filter(line -> "DIEN".equals(line.getFeeType().getCode()))
                .findFirst()
                .orElse(null);
    }

    @Test
    void generateStoresReadingsAndUsageLine() {
        asRoot();
        Long roomId = createRoom("NHA-RD-1", "R101");
        meter(roomId, "DIEN", "2030-02", "100");
        meter(roomId, "DIEN", "2030-03", "150");
        rate("DIEN", "2030-03", 3500);

        var response = invoiceService.generate("2030-03");
        assertThat(response.created()).isGreaterThanOrEqualTo(1);

        Invoice invoice = invoiceOf(roomId, "2030-03");
        assertThat(invoice.getPreElectReading()).isEqualByComparingTo("100");
        assertThat(invoice.getCurrentElectReading()).isEqualByComparingTo("150");
        InvoiceLine line = dienLine(invoice);
        assertThat(line).isNotNull();
        assertThat(line.getQuantity()).isEqualByComparingTo("50");
        assertThat(line.getAmount()).isEqualTo(175_000L);
        assertThat(invoice.getTotalAmount()).isEqualTo(3_175_000L);
    }

    @Test
    void readingsEnteredAfterGenerateCreateUsageLine() {
        asRoot();
        Long roomId = createRoom("NHA-RD-2", "R102");
        meter(roomId, "DIEN", "2030-02", "100");
        rate("DIEN", "2030-03", 3500);

        invoiceService.generate("2030-03");
        Invoice invoice = invoiceOf(roomId, "2030-03");
        assertThat(invoice.getPreElectReading()).isEqualByComparingTo("100");
        assertThat(invoice.getCurrentElectReading()).isNull();
        assertThat(dienLine(invoice)).isNull();

        var detail = invoiceService.updateReadings(invoice.getId(),
                new BillingDtos.UsageReadingsRequest(new BigDecimal("100"), new BigDecimal("130"), null, null));
        assertThat(detail.currentElectReading()).isEqualByComparingTo("130");
        InvoiceLine line = dienLine(invoiceOf(roomId, "2030-03"));
        assertThat(line.getQuantity()).isEqualByComparingTo("30");
        assertThat(detail.totalAmount()).isEqualTo(3_105_000L);
    }

    @Test
    void updateReadingsRejectsCurrentBelowPre() {
        asRoot();
        Long roomId = createRoom("NHA-RD-3", "R103");
        meter(roomId, "DIEN", "2030-02", "100");
        meter(roomId, "DIEN", "2030-03", "150");
        rate("DIEN", "2030-03", 3500);
        invoiceService.generate("2030-03");
        Long invoiceId = invoiceOf(roomId, "2030-03").getId();

        assertThatThrownBy(() -> invoiceService.updateReadings(invoiceId,
                new BillingDtos.UsageReadingsRequest(new BigDecimal("200"), new BigDecimal("100"), null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void zeroConsumptionRemovesUsageLine() {
        asRoot();
        Long roomId = createRoom("NHA-RD-4", "R104");
        meter(roomId, "DIEN", "2030-02", "100");
        meter(roomId, "DIEN", "2030-03", "150");
        rate("DIEN", "2030-03", 3500);
        invoiceService.generate("2030-03");
        Invoice invoice = invoiceOf(roomId, "2030-03");
        assertThat(dienLine(invoice)).isNotNull();

        invoiceService.updateReadings(invoice.getId(),
                new BillingDtos.UsageReadingsRequest(new BigDecimal("150"), new BigDecimal("150"), null, null));

        Invoice updated = invoiceOf(roomId, "2030-03");
        assertThat(dienLine(updated)).isNull();
        assertThat(updated.getTotalAmount()).isEqualTo(3_000_000L);
    }

    @Test
    void preChainsFromPreviousInvoiceCurrentReading() {
        asRoot();
        Long roomId = createRoom("NHA-RD-5", "R105");
        meter(roomId, "DIEN", "2030-02", "100");
        meter(roomId, "DIEN", "2030-03", "150");
        meter(roomId, "DIEN", "2030-04", "200");
        rate("DIEN", "2030-03", 3500);
        rate("DIEN", "2030-04", 3500);

        invoiceService.generate("2030-03");
        invoiceService.updateReadings(invoiceOf(roomId, "2030-03").getId(),
                new BillingDtos.UsageReadingsRequest(new BigDecimal("100"), new BigDecimal("160"), null, null));

        invoiceService.generate("2030-04");
        Invoice next = invoiceOf(roomId, "2030-04");
        assertThat(next.getPreElectReading()).isEqualByComparingTo("160");
        assertThat(next.getCurrentElectReading()).isEqualByComparingTo("200");
        assertThat(dienLine(next).getQuantity()).isEqualByComparingTo("40");
    }

    @Test
    void paidInvoiceRejectsReadingUpdates() {
        asRoot();
        Long roomId = createRoom("NHA-RD-6", "R106");
        meter(roomId, "DIEN", "2030-02", "100");
        meter(roomId, "DIEN", "2030-03", "150");
        rate("DIEN", "2030-03", 3500);
        invoiceService.generate("2030-03");
        Invoice invoice = invoiceOf(roomId, "2030-03");
        invoiceService.publish(invoice.getId());
        invoiceService.pay(invoice.getId(),
                new BillingDtos.PaymentRequest(invoiceOf(roomId, "2030-03").getTotalAmount()));

        assertThatThrownBy(() -> invoiceService.updateReadings(invoice.getId(),
                new BillingDtos.UsageReadingsRequest(new BigDecimal("100"), new BigDecimal("200"), null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void feeRateOfPeriodWinsOverContractPrice() {
        asRoot();
        Long roomId = createRoom("NHA-RD-7", "R107", java.util.Map.of("DIEN", 4000L));
        meter(roomId, "DIEN", "2030-02", "100");
        meter(roomId, "DIEN", "2030-03", "150");
        rate("DIEN", "2030-03", 3500);

        invoiceService.generate("2030-03");
        InvoiceLine line = dienLine(invoiceOf(roomId, "2030-03"));
        assertThat(line).isNotNull();
        assertThat(line.getUnitPrice()).isEqualTo(3500L);
        assertThat(line.getAmount()).isEqualTo(175_000L);
    }

    @Test
    void contractPriceUsedWhenNoRateForPeriod() {
        asRoot();
        Long roomId = createRoom("NHA-RD-8", "R108", java.util.Map.of("DIEN", 4000L));
        meter(roomId, "DIEN", "2030-02", "100");
        meter(roomId, "DIEN", "2030-03", "150");

        invoiceService.generate("2030-03");
        InvoiceLine line = dienLine(invoiceOf(roomId, "2030-03"));
        assertThat(line).isNotNull();
        assertThat(line.getUnitPrice()).isEqualTo(4000L);

        var detail = invoiceService.updateReadings(invoiceOf(roomId, "2030-03").getId(),
                new BillingDtos.UsageReadingsRequest(new BigDecimal("100"), new BigDecimal("130"), null, null));
        assertThat(detail.totalAmount()).isEqualTo(3_120_000L);
    }

    @Test
    void readingsRejectedWithGenericMessageWhenNoPriceConfigured() {
        asRoot();
        Long roomId = createRoom("NHA-RD-9", "R109");
        meter(roomId, "DIEN", "2030-02", "100");
        invoiceService.generate("2030-03");
        Long invoiceId = invoiceOf(roomId, "2030-03").getId();

        assertThatThrownBy(() -> invoiceService.updateReadings(invoiceId,
                new BillingDtos.UsageReadingsRequest(new BigDecimal("100"), new BigDecimal("130"), null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Chưa có cấu hình giá kỳ 03/2030");
                });
    }

    @Test
    void contractStartingMidPeriodStillProvidesPrice() {
        asRoot();
        Long roomId = createRoom("NHA-RD-10", "R110", LocalDate.of(2030, 3, 5),
                java.util.Map.of("DIEN", 4000L));
        meter(roomId, "DIEN", "2030-02", "100");

        invoiceService.generate("2030-03");
        Invoice invoice = invoiceOf(roomId, "2030-03");

        var detail = invoiceService.updateReadings(invoice.getId(),
                new BillingDtos.UsageReadingsRequest(new BigDecimal("100"), new BigDecimal("130"), null, null));

        InvoiceLine line = dienLine(invoiceOf(roomId, "2030-03"));
        assertThat(line).isNotNull();
        assertThat(line.getUnitPrice()).isEqualTo(4000L);
        assertThat(line.getQuantity()).isEqualByComparingTo("30");
        assertThat(detail.totalAmount()).isEqualTo(3_120_000L);
    }
}
