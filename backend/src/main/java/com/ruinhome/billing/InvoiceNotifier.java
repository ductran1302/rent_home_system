package com.ruinhome.billing;

public interface InvoiceNotifier {

    void onInvoicePublished(Long invoiceId);

    void onInvoicePaid(Long invoiceId);
}
