package com.ruinhome.notification;

import com.ruinhome.auth.CurrentUserService;
import com.ruinhome.billing.Invoice;
import com.ruinhome.billing.InvoiceRepository;
import com.ruinhome.billing.InvoiceStatus;
import com.ruinhome.contract.ContractRepository;
import com.ruinhome.room.Room;
import com.ruinhome.user.UserAccount;
import com.ruinhome.user.UserAccountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.NumberFormat;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class NotificationService {

    private static final DateTimeFormatter DISPLAY_PERIOD = DateTimeFormatter.ofPattern("MM/yyyy");

    private final NotificationRepository notificationRepository;
    private final UserAccountRepository userAccountRepository;
    private final InvoiceRepository invoiceRepository;
    private final ContractRepository contractRepository;
    private final CurrentUserService currentUserService;

    public NotificationService(NotificationRepository notificationRepository,
                               UserAccountRepository userAccountRepository,
                               InvoiceRepository invoiceRepository,
                               ContractRepository contractRepository,
                               CurrentUserService currentUserService) {
        this.notificationRepository = notificationRepository;
        this.userAccountRepository = userAccountRepository;
        this.invoiceRepository = invoiceRepository;
        this.contractRepository = contractRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public Page<NotificationDtos.NotificationResponse> list(boolean unread, int page, int size) {
        Long userId = currentUserService.account().getId();
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<Notification> result = unread
                ? notificationRepository.findByUserIdAndReadFalse(userId, pageable)
                : notificationRepository.findByUserId(userId, pageable);
        return result.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notificationRepository.countByUserIdAndReadFalse(currentUserService.account().getId());
    }

    @Transactional
    public NotificationDtos.NotificationResponse markRead(Long id) {
        Long userId = currentUserService.account().getId();
        Notification notification = notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy thông báo"));
        notification.setRead(true);
        return toResponse(notificationRepository.save(notification));
    }

    @Transactional
    public int markAllRead() {
        return notificationRepository.markAllReadByUserId(currentUserService.account().getId());
    }

    @Transactional
    public void notifyInvoicePublished(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId).orElse(null);
        if (invoice == null) {
            return;
        }
        YearMonth period = YearMonth.parse(invoice.getPeriod());
        Room room = invoice.getRoom();
        String title = "Hóa đơn mới";
        String body = "Hóa đơn phòng " + room.getRoomNumber()
                + " kỳ " + period.format(DISPLAY_PERIOD) + " đã được phát hành.";
        String dedupKey = "INVOICE_PUBLISHED:" + invoice.getId();
        for (UserAccount recipient : invoiceRecipients(invoice, period)) {
            if (notificationRepository.existsByUserIdAndDedupKey(recipient.getId(), dedupKey)) {
                continue;
            }
            create(recipient, NotificationType.INVOICE_PUBLISHED, dedupKey, title, body, "/billing");
        }
    }

    @Transactional
    public void notifyInvoicePaid(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId).orElse(null);
        if (invoice == null) {
            return;
        }
        YearMonth period = YearMonth.parse(invoice.getPeriod());
        Room room = invoice.getRoom();
        boolean fullyPaid = invoice.getStatus() == InvoiceStatus.PAID;
        long remaining = invoice.getTotalAmount() - invoice.getPaidAmount();
        String title = "Ghi nhận đóng tiền";
        String body = fullyPaid
                ? "Đã nhận đủ tiền phòng " + room.getRoomNumber()
                        + " kỳ " + period.format(DISPLAY_PERIOD)
                        + " (" + formatVnd(invoice.getPaidAmount()) + " đ.)."
                : "Chủ nhà đã nhận " + formatVnd(invoice.getPaidAmount())
                        + " đ. cho phòng " + room.getRoomNumber()
                        + " kỳ " + period.format(DISPLAY_PERIOD)
                        + ". Còn thiếu " + formatVnd(remaining) + " đ.";
        String dedupKey = "INVOICE_PAYMENT:" + invoice.getId() + ":" + invoice.getPaidAmount();
        for (UserAccount recipient : invoiceRecipients(invoice, period)) {
            if (notificationRepository.existsByUserIdAndDedupKey(recipient.getId(), dedupKey)) {
                continue;
            }
            create(recipient, NotificationType.INVOICE_PAYMENT_RECEIVED, dedupKey, title, body, "/billing");
        }
    }

    private List<UserAccount> invoiceRecipients(Invoice invoice, YearMonth period) {
        var contract = contractRepository.findFirstCoveringPeriod(
                invoice.getRoom().getId(), period.atDay(1), period.atEndOfMonth());
        if (contract.isEmpty()) {
            return List.of();
        }
        Set<Long> personIds = new HashSet<>();
        personIds.add(contract.get().getHolder().getId());
        contract.get().getTenants().forEach(tenant -> personIds.add(tenant.getId()));
        return userAccountRepository.findByPersonIdInAndEnabledTrue(personIds);
    }

    private String formatVnd(long value) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(value);
    }

    private void create(UserAccount recipient, NotificationType type, String dedupKey,
                        String title, String body, String link) {
        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setType(type);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setLink(link);
        notification.setDedupKey(dedupKey);
        notification.setRead(false);
        notificationRepository.save(notification);
    }

    private NotificationDtos.NotificationResponse toResponse(Notification notification) {
        return new NotificationDtos.NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getLink(),
                notification.isRead(),
                notification.getCreatedAt());
    }
}
