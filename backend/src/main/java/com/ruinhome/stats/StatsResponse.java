package com.ruinhome.stats;

public record StatsResponse(
        Long houseCount,
        Long roomCount,
        Long vacantRoomCount,
        Long activeContractCount,
        Long unpaidInvoiceCount,
        Long outstandingDebt) {
}
