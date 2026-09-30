package com.ruinhome.billing;

import com.ruinhome.auth.CurrentUserService;
import com.ruinhome.contract.Contract;
import com.ruinhome.contract.ContractFeePrice;
import com.ruinhome.contract.ContractFeePriceRepository;
import com.ruinhome.contract.ContractRepository;
import com.ruinhome.house.House;
import com.ruinhome.room.Room;
import com.ruinhome.room.RoomRepository;
import com.ruinhome.user.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class InvoiceService {

    private static final BigDecimal ONE = BigDecimal.ONE;

    private final InvoiceRepository invoiceRepository;
    private final ContractRepository contractRepository;
    private final ContractFeePriceRepository contractFeePriceRepository;
    private final RoomRepository roomRepository;
    private final FeeTypeRepository feeTypeRepository;
    private final FeeRateRepository feeRateRepository;
    private final MeterReadingRepository meterReadingRepository;
    private final CurrentUserService currentUserService;

    public InvoiceService(InvoiceRepository invoiceRepository, ContractRepository contractRepository,
                          ContractFeePriceRepository contractFeePriceRepository,
                          RoomRepository roomRepository, FeeTypeRepository feeTypeRepository,
                          FeeRateRepository feeRateRepository,
                          MeterReadingRepository meterReadingRepository,
                          CurrentUserService currentUserService) {
        this.invoiceRepository = invoiceRepository;
        this.contractRepository = contractRepository;
        this.contractFeePriceRepository = contractFeePriceRepository;
        this.roomRepository = roomRepository;
        this.feeTypeRepository = feeTypeRepository;
        this.feeRateRepository = feeRateRepository;
        this.meterReadingRepository = meterReadingRepository;
        this.currentUserService = currentUserService;
    }

    private record Usage(BigDecimal qty, String warning) {
    }

    @Transactional
    public Page<BillingDtos.InvoiceResponse> search(String period, Long houseId, InvoiceStatus status,
                                                    int page, int size) {
        if (period != null) {
            BillingSupport.validatePeriod(period);
        }
        Long ownerScope = null;
        Long userScope = null;
        var account = currentUserService.account();
        if (account.getRole() == Role.MANAGER) {
            ownerScope = currentUserService.personId();
        } else if (account.getRole() == Role.USER) {
            if (account.getPerson() == null) {
                return Page.empty(PageRequest.of(page, size));
            }
            userScope = account.getPerson().getId();
        }
        var pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "period").and(Sort.by("id")));
        return invoiceRepository.search(period, houseId, status, ownerScope, userScope, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public BillingDtos.InvoiceDetailResponse get(Long id) {
        Invoice invoice = findVisible(id);
        return toDetailResponse(invoice);
    }

    @Transactional
    public BillingDtos.GenerateResponse generate(String period) {
        BillingSupport.validatePeriod(period);
        Long ownerScope = null;
        if (currentUserService.account().getRole() == Role.MANAGER) {
            ownerScope = currentUserService.personId();
        }

        contractRepository.expireOverdue(com.ruinhome.contract.ContractStatus.ACTIVE,
                com.ruinhome.contract.ContractStatus.EXPIRED, LocalDate.now());

        YearMonth yearMonth = YearMonth.parse(period);
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();

        Map<String, FeeType> feeTypes = feeTypeRepository.findByActiveTrueOrderByCodeAsc().stream()
                .collect(java.util.stream.Collectors.toMap(FeeType::getCode, fee -> fee, (a, b) -> a));
        Map<String, FeeRate> rates = new HashMap<>();
        for (FeeRate rate : feeRateRepository.findByPeriod(period)) {
            rates.put(rate.getFeeType().getCode(), rate);
        }

        List<Contract> contracts = contractRepository.findActiveForPeriod(start, end, ownerScope);
        Map<Long, Map<String, Long>> pricesByContract = new HashMap<>();
        for (ContractFeePrice row : contractFeePriceRepository.findByContractIdIn(
                contracts.stream().map(Contract::getId).toList())) {
            pricesByContract.computeIfAbsent(row.getContract().getId(), key -> new HashMap<>())
                    .put(row.getFeeCode(), row.getPrice());
        }
        List<BillingDtos.GenerateSkip> skipped = new ArrayList<>();
        int created = 0;

        for (Contract contract : contracts) {
            Room room = contract.getRoom();
            if (invoiceRepository.existsByRoomIdAndPeriod(room.getId(), period)) {
                skipped.add(new BillingDtos.GenerateSkip(room.getId(), room.getRoomNumber(),
                        "Đã có hóa đơn kỳ này"));
                continue;
            }
            Invoice invoice = new Invoice();
            invoice.setRoom(room);
            invoice.setPeriod(period);
            invoice.setStatus(InvoiceStatus.DRAFT);
            invoice.setTotalAmount(0L);

            Map<String, Long> feePrices = pricesByContract.getOrDefault(contract.getId(), Map.of());

            FeeType roomFeeType = feeTypes.get("PHONG");
            if (roomFeeType != null) {
                addLine(invoice, roomFeeType, "Tiền phòng tháng " + period, ONE, contract.getMonthlyRent());
            }

            appendUsageLine(invoice, feeTypes.get("DIEN"), rates.get("DIEN"), feePrices.get("DIEN"),
                    room, period, skipped);
            appendUsageLine(invoice, feeTypes.get("NUOC"), rates.get("NUOC"), feePrices.get("NUOC"),
                    room, period, skipped);
            appendFlatLine(invoice, feeTypes.get("MANG"), rates.get("MANG"), feePrices.get("MANG"), period);
            appendFlatLine(invoice, feeTypes.get("DICH_VU"), rates.get("DICH_VU"),
                    feePrices.get("DICH_VU"), period);

            invoice.setTotalAmount(totalOf(invoice));
            invoiceRepository.save(invoice);
            created++;
        }
        return new BillingDtos.GenerateResponse(created, skipped);
    }

    @Transactional
    public BillingDtos.InvoiceDetailResponse publish(Long id) {
        Invoice invoice = findManageable(id);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Chỉ phát hành được hóa đơn nháp");
        }
        invoice.setStatus(InvoiceStatus.UNPAID);
        return toDetailResponse(invoiceRepository.save(invoice));
    }

    @Transactional
    public BillingDtos.InvoiceDetailResponse pay(Long id, BillingDtos.PaymentRequest request) {
        Invoice invoice = findManageable(id);
        if (invoice.getStatus() == InvoiceStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Hãy phát hành hóa đơn trước khi ghi nhận đóng tiền");
        }
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hóa đơn đã được đóng đủ");
        }
        long newPaid = invoice.getPaidAmount() + request.amount();
        invoice.setPaidAmount(Math.min(newPaid, invoice.getTotalAmount()));
        if (newPaid >= invoice.getTotalAmount()) {
            invoice.setStatus(InvoiceStatus.PAID);
        } else {
            invoice.setStatus(InvoiceStatus.PARTIAL);
        }
        return toDetailResponse(invoiceRepository.save(invoice));
    }

    @Transactional
    public BillingDtos.InvoiceDetailResponse addLine(Long id, BillingDtos.InvoiceLineRequest request) {
        Invoice invoice = findManageable(id);
        checkEditable(invoice);
        FeeType feeType = feeTypeRepository.findById(request.feeTypeId())
                .filter(FeeType::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy loại phí"));
        String description = request.description() != null && !request.description().isBlank()
                ? request.description().trim()
                : feeType.getName() + " kỳ " + invoice.getPeriod();
        addLine(invoice, feeType, description, request.quantity(), request.unitPrice());
        invoice.setTotalAmount(totalOf(invoice));
        recomputeStatus(invoice);
        return toDetailResponse(invoiceRepository.save(invoice));
    }

    @Transactional
    public BillingDtos.InvoiceDetailResponse updateLine(Long id, Long lineId,
                                                        BillingDtos.InvoiceLineUpdateRequest request) {
        Invoice invoice = findManageable(id);
        checkEditable(invoice);
        InvoiceLine line = invoice.getLines().stream()
                .filter(item -> item.getId().equals(lineId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy dòng tiền"));
        line.setQuantity(request.quantity());
        line.setUnitPrice(request.unitPrice());
        line.setAmount(amountOf(request.quantity(), request.unitPrice()));
        invoice.setTotalAmount(totalOf(invoice));
        recomputeStatus(invoice);
        return toDetailResponse(invoiceRepository.save(invoice));
    }

    @Transactional
    public BillingDtos.InvoiceDetailResponse deleteLine(Long id, Long lineId) {
        Invoice invoice = findManageable(id);
        checkEditable(invoice);
        InvoiceLine line = invoice.getLines().stream()
                .filter(item -> item.getId().equals(lineId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy dòng tiền"));
        invoice.getLines().remove(line);
        invoice.setTotalAmount(totalOf(invoice));
        recomputeStatus(invoice);
        return toDetailResponse(invoiceRepository.save(invoice));
    }

    @Transactional
    public BillingDtos.InvoiceDetailResponse updateRoomPrice(Long id, BillingDtos.RoomPriceRequest request) {
        Invoice invoice = findManageable(id);
        checkEditable(invoice);
        InvoiceLine roomLine = invoice.getLines().stream()
                .filter(line -> "PHONG".equals(line.getFeeType().getCode()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Hóa đơn không có dòng tiền phòng"));
        roomLine.setUnitPrice(request.amount());
        roomLine.setAmount(amountOf(roomLine.getQuantity(), request.amount()));
        invoice.setRoomPriceNote(request.note());
        invoice.setTotalAmount(totalOf(invoice));
        recomputeStatus(invoice);
        return toDetailResponse(invoiceRepository.save(invoice));
    }

    private void appendUsageLine(Invoice invoice, FeeType feeType, FeeRate rate, Long contractPrice,
                                 Room room, String period, List<BillingDtos.GenerateSkip> skipped) {
        if (feeType == null || !feeType.isActive()) {
            return;
        }
        Long price = contractPrice != null ? contractPrice : (rate != null ? rate.getPrice() : null);
        if (price == null) {
            skipped.add(new BillingDtos.GenerateSkip(room.getId(), room.getRoomNumber(),
                    "Chưa cấu hình giá " + feeType.getName().toLowerCase() + " kỳ " + period));
            return;
        }
        Usage usage = computeUsage(room.getId(), feeType.getId(), period);
        if (usage.warning() != null) {
            skipped.add(new BillingDtos.GenerateSkip(room.getId(), room.getRoomNumber(), usage.warning()));
        }
        if (usage.qty() == null || usage.qty().signum() <= 0) {
            return;
        }
        addLine(invoice, feeType, feeType.getName() + " tháng " + period, usage.qty(), price);
    }

    private void appendFlatLine(Invoice invoice, FeeType feeType, FeeRate rate, Long contractPrice,
                                String period) {
        if (feeType == null || !feeType.isActive()) {
            return;
        }
        Long price = contractPrice != null ? contractPrice : (rate != null ? rate.getPrice() : null);
        if (price == null) {
            return;
        }
        addLine(invoice, feeType, feeType.getName() + " tháng " + period, ONE, price);
    }

    private Usage computeUsage(Long roomId, Long feeTypeId, String period) {
        var current = meterReadingRepository.findByRoomIdAndFeeTypeIdAndPeriod(roomId, feeTypeId, period);
        if (current.isEmpty()) {
            return new Usage(null, null);
        }
        var previous = meterReadingRepository.findByRoomIdAndFeeTypeIdAndPeriod(
                roomId, feeTypeId, BillingSupport.prevPeriod(period));
        if (previous.isEmpty()) {
            return new Usage(null, "Kỳ trước chưa có chỉ số, chỉ số kỳ này là chỉ số đầu");
        }
        BigDecimal delta = current.get().getReading().subtract(previous.get().getReading());
        if (delta.signum() < 0) {
            return new Usage(null, "Chỉ số kỳ này nhỏ hơn kỳ trước, vui lòng kiểm tra");
        }
        return new Usage(delta, null);
    }

    private void addLine(Invoice invoice, FeeType feeType, String description, BigDecimal quantity, Long unitPrice) {
        InvoiceLine line = new InvoiceLine();
        line.setInvoice(invoice);
        line.setFeeType(feeType);
        line.setDescription(description);
        line.setQuantity(quantity);
        line.setUnitPrice(unitPrice);
        line.setAmount(amountOf(quantity, unitPrice));
        invoice.getLines().add(line);
    }

    private long amountOf(BigDecimal quantity, Long unitPrice) {
        return quantity.multiply(BigDecimal.valueOf(unitPrice))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    private long totalOf(Invoice invoice) {
        return invoice.getLines().stream()
                .mapToLong(InvoiceLine::getAmount)
                .sum();
    }

    private void recomputeStatus(Invoice invoice) {
        if (invoice.getStatus() == InvoiceStatus.DRAFT) {
            return;
        }
        if (invoice.getPaidAmount() <= 0) {
            invoice.setStatus(InvoiceStatus.UNPAID);
        } else if (invoice.getPaidAmount() >= invoice.getTotalAmount()) {
            invoice.setStatus(InvoiceStatus.PAID);
        } else {
            invoice.setStatus(InvoiceStatus.PARTIAL);
        }
    }

    private void checkEditable(Invoice invoice) {
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Hóa đơn đã đóng đủ, không thể sửa");
        }
    }

    private Invoice findVisible(Long id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy hóa đơn"));
        var account = currentUserService.account();
        if (account.getRole() == Role.ADMIN) {
            return invoice;
        }
        if (account.getRole() == Role.MANAGER) {
            checkHouseVisible(invoice.getRoom().getHouse());
            return invoice;
        }
        Long personId = currentUserService.personIdOrNull();
        if (personId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền với hóa đơn này");
        }
        final Long scopePersonId = personId;
        boolean related = contractRepository.existsForRoomAndPerson(invoice.getRoom().getId(), scopePersonId);
        if (!related) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền với hóa đơn này");
        }
        return invoice;
    }

    private Invoice findManageable(Long id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy hóa đơn"));
        if (currentUserService.isAdmin()) {
            return invoice;
        }
        checkHouseVisible(invoice.getRoom().getHouse());
        return invoice;
    }

    private void checkHouseVisible(House house) {
        if (currentUserService.isAdmin()) {
            return;
        }
        Long personId = currentUserService.personId();
        if (!personId.equals(house.getOwner().getId()) && !personId.equals(house.getManager().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền với nhà này");
        }
    }

    private BillingDtos.InvoiceResponse toResponse(Invoice invoice) {
        Room room = invoice.getRoom();
        return new BillingDtos.InvoiceResponse(
                invoice.getId(),
                room.getId(),
                room.getHouse().getId(),
                room.getHouse().getName(),
                room.getRoomNumber(),
                invoice.getPeriod(),
                invoice.getTotalAmount(),
                invoice.getPaidAmount(),
                invoice.getStatus(),
                invoice.getNote(),
                invoice.getLines().size());
    }

    private BillingDtos.InvoiceDetailResponse toDetailResponse(Invoice invoice) {
        Room room = invoice.getRoom();
        var lines = invoice.getLines().stream()
                .map(line -> new BillingDtos.InvoiceLineResponse(
                        line.getId(),
                        line.getFeeType().getId(),
                        line.getFeeType().getCode(),
                        line.getDescription(),
                        line.getQuantity(),
                        line.getUnitPrice(),
                        line.getAmount()))
                .toList();
        Long contractRent = contractRepository
                .findFirstCoveringDate(room.getId(), YearMonth.parse(invoice.getPeriod()).atDay(1))
                .map(Contract::getMonthlyRent)
                .orElse(null);
        return new BillingDtos.InvoiceDetailResponse(
                invoice.getId(),
                room.getId(),
                room.getHouse().getId(),
                room.getHouse().getName(),
                room.getRoomNumber(),
                invoice.getPeriod(),
                invoice.getTotalAmount(),
                invoice.getPaidAmount(),
                invoice.getStatus(),
                invoice.getNote(),
                invoice.getRoomPriceNote(),
                contractRent,
                lines);
    }
}
