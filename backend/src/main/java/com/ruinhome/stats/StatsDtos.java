package com.ruinhome.stats;

import java.util.List;

public final class StatsDtos {

    private StatsDtos() {
    }

    public record MonthRevenue(String period, long collected, long outstanding) {
    }

    public record RevenueResponse(int year, List<MonthRevenue> months) {
    }
}
