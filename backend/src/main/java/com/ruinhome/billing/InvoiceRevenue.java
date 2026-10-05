package com.ruinhome.billing;

public record InvoiceRevenue(String period, long collected, long outstanding) {
}
