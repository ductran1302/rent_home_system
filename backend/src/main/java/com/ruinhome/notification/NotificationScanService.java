package com.ruinhome.notification;

import com.ruinhome.asset.AssetRepair;
import com.ruinhome.asset.AssetRepairRepository;
import com.ruinhome.billing.Invoice;
import com.ruinhome.billing.InvoiceRepository;
import com.ruinhome.billing.MeterReadingRepository;
import com.ruinhome.contract.Contract;
import com.ruinhome.contract.ContractRepository;
import com.ruinhome.house.House;
import com.ruinhome.room.Room;
import com.ruinhome.user.UserAccount;
import com.ruinhome.user.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
public class NotificationScanService {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DISPLAY_PERIOD = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final Map<String, String> USAGE_LABELS = Map.of("DIEN", "điện", "NUOC", "nước");

    private final NotificationRepository notificationRepository;
    private final UserAccountRepository userAccountRepository;
    private final ContractRepository contractRepository;
    private final InvoiceRepository invoiceRepository;
    private final MeterReadingRepository meterReadingRepository;
    private final AssetRepairRepository assetRepairRepository;

    @Value("${ruinhome.notification.expiring-days:7}")
    private int expiringDays;

    @Value("${ruinhome.notification.pending-days:7}")
    private int pendingDays;

    public NotificationScanService(NotificationRepository notificationRepository,
                                   UserAccountRepository userAccountRepository,
                                   ContractRepository contractRepository,
                                   InvoiceRepository invoiceRepository,
                                   MeterReadingRepository meterReadingRepository,
                                   AssetRepairRepository assetRepairRepository) {
        this.notificationRepository = notificationRepository;
        this.userAccountRepository = userAccountRepository;
        this.contractRepository = contractRepository;
        this.invoiceRepository = invoiceRepository;
        this.meterReadingRepository = meterReadingRepository;
        this.assetRepairRepository = assetRepairRepository;
    }

    @Transactional
    public int scan() {
        notificationRepository.deleteReadOlderThan(LocalDateTime.now().minusDays(90));
        LocalDate today = LocalDate.now();
        int created = 0;
        created += scanExpiringContracts(today);
        created += scanUnpaidInvoices(today);
        created += scanMissingReadings(today);
        created += scanPendingRepairs(today);
        created += scanExpiringManagers(today);
        return created;
    }

    private int scanExpiringContracts(LocalDate today) {
        List<Contract> contracts = contractRepository.findActiveEndingBetween(today, today.plusDays(expiringDays));
        int created = 0;
        for (Contract contract : contracts) {
            Room room = contract.getRoom();
            House house = room.getHouse();
            created += createForArea(house.getAreaAdmin(), NotificationType.CONTRACT_EXPIRING,
                    "CONTRACT_EXPIRING:" + contract.getId() + ":" + contract.getEndDate(),
                    "Hợp đồng sắp hết hạn",
                    "Hợp đồng phòng " + room.getRoomNumber() + " (" + house.getName()
                            + ") hết hạn ngày " + contract.getEndDate().format(DISPLAY_DATE) + ".",
                    "/contracts");
        }
        return created;
    }

    private int scanUnpaidInvoices(LocalDate today) {
        String period = YearMonth.from(today).minusMonths(1).toString();
        List<Invoice> invoices = invoiceRepository.findUnpaidForPeriod(period);
        int created = 0;
        for (Invoice invoice : invoices) {
            Room room = invoice.getRoom();
            House house = room.getHouse();
            long remaining = invoice.getTotalAmount() - invoice.getPaidAmount();
            created += createForArea(house.getAreaAdmin(), NotificationType.INVOICE_UNPAID,
                    "INVOICE_UNPAID:" + invoice.getId(),
                    "Hóa đơn chưa thu đủ",
                    "Hóa đơn phòng " + room.getRoomNumber() + " kỳ "
                            + YearMonth.parse(invoice.getPeriod()).format(DISPLAY_PERIOD)
                            + " còn thiếu " + formatVnd(remaining) + " đ.",
                    "/billing");
        }
        return created;
    }

    private int scanMissingReadings(LocalDate today) {
        YearMonth current = YearMonth.from(today);
        YearMonth previous = current.minusMonths(1);
        int created = 0;
        for (Map.Entry<String, String> usage : USAGE_LABELS.entrySet()) {
            String feeCode = usage.getKey();
            List<Room> rooms = meterReadingRepository.findRoomsMissingReading(
                    current.toString(), previous.toString(), feeCode,
                    current.atDay(1), current.atEndOfMonth());
            for (Room room : rooms) {
                House house = room.getHouse();
                created += createForArea(house.getAreaAdmin(), NotificationType.METER_MISSING,
                        "METER_MISSING:" + room.getId() + ":" + feeCode + ":" + current,
                        "Chưa nhập chỉ số " + usage.getValue(),
                        "Phòng " + room.getRoomNumber() + " (" + house.getName()
                                + ") chưa nhập chỉ số " + usage.getValue() + " kỳ "
                                + current.format(DISPLAY_PERIOD) + ".",
                        "/billing");
            }
        }
        return created;
    }

    private int scanPendingRepairs(LocalDate today) {
        List<AssetRepair> repairs = assetRepairRepository.findPendingSince(today.minusDays(pendingDays));
        int created = 0;
        for (AssetRepair repair : repairs) {
            Room room = repair.getAsset().getRoom();
            House house = room.getHouse();
            created += createForArea(house.getAreaAdmin(), NotificationType.REPAIR_PENDING,
                    "REPAIR_PENDING:" + repair.getId(),
                    "Lần sửa chữa đang chờ xử lý",
                    "Tài sản " + repair.getAsset().getName() + " (" + room.getRoomNumber()
                            + ") chờ xử lý từ " + repair.getReportedAt().format(DISPLAY_DATE) + ".",
                    "/assets");
        }
        return created;
    }

    private int scanExpiringManagers(LocalDate today) {
        List<UserAccount> managers = userAccountRepository
                .findManagersEndingBetween(today, today.plusDays(expiringDays));
        int created = 0;
        for (UserAccount manager : managers) {
            String area = manager.getAreaAdmin() != null ? manager.getAreaAdmin() : manager.getUsername();
            created += createForArea(area, NotificationType.MANAGER_EXPIRING,
                    "MANAGER_EXPIRING:" + manager.getId() + ":" + manager.getManagerEndDate(),
                    "Tài khoản quản lý sắp hết hạn",
                    "Tài khoản " + manager.getUsername() + " hết hạn ngày "
                            + manager.getManagerEndDate().format(DISPLAY_DATE) + ".",
                    "/accounts");
        }
        return created;
    }

    private int createForArea(String area, NotificationType type, String dedupKey,
                              String title, String body, String link) {
        return create(userAccountRepository.findAreaStaff(area), type, dedupKey, title, body, link);
    }

    private int create(Collection<UserAccount> recipients, NotificationType type, String dedupKey,
                       String title, String body, String link) {
        int created = 0;
        for (UserAccount recipient : recipients) {
            if (notificationRepository.existsByUserIdAndDedupKey(recipient.getId(), dedupKey)) {
                continue;
            }
            Notification notification = new Notification();
            notification.setUser(recipient);
            notification.setType(type);
            notification.setTitle(title);
            notification.setBody(body);
            notification.setLink(link);
            notification.setDedupKey(dedupKey);
            notification.setRead(false);
            notificationRepository.save(notification);
            created++;
        }
        return created;
    }

    private String formatVnd(long amount) {
        return NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("vi-VN")).format(amount);
    }
}
