package com.ruinhome.notification;

import com.ruinhome.billing.InvoiceNotifier;
import org.springframework.stereotype.Service;

@Service
public class InvoiceNotifierImpl implements InvoiceNotifier {

    private final NotificationService notificationService;

    public InvoiceNotifierImpl(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void onInvoicePublished(Long invoiceId) {
        notificationService.notifyInvoicePublished(invoiceId);
    }

    @Override
    public void onInvoicePaid(Long invoiceId) {
        notificationService.notifyInvoicePaid(invoiceId);
    }
}
