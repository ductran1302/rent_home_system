package com.ruinhome.user;

import java.time.LocalDate;

public final class ManagerPeriod {

    private ManagerPeriod() {
    }

    public static boolean isActiveToday(UserAccount account) {
        if (account.getRole() != Role.MANAGER) {
            return true;
        }
        LocalDate today = LocalDate.now();
        LocalDate start = account.getManagerStartDate();
        LocalDate end = account.getManagerEndDate();
        if (start != null && today.isBefore(start)) {
            return false;
        }
        return end == null || !today.isAfter(end);
    }

    public static String inactiveMessage(UserAccount account) {
        LocalDate today = LocalDate.now();
        LocalDate start = account.getManagerStartDate();
        if (start != null && today.isBefore(start)) {
            return "Tài khoản chưa đến ngày bắt đầu quản lý";
        }
        return "Tài khoản đã hết thời hạn quản lý";
    }
}
