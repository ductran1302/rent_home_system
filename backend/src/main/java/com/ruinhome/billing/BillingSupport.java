package com.ruinhome.billing;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

public final class BillingSupport {

    private static final Pattern PERIOD_PATTERN = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");
    private static final DateTimeFormatter DISPLAY_PERIOD = DateTimeFormatter.ofPattern("MM/yyyy");

    private BillingSupport() {
    }

    public static void validatePeriod(String period) {
        if (period == null || !PERIOD_PATTERN.matcher(period).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Kỳ không hợp lệ, định dạng phải là YYYY-MM");
        }
    }

    public static String prevPeriod(String period) {
        return YearMonth.parse(period).minusMonths(1).toString();
    }

    public static String displayPeriod(String period) {
        return YearMonth.parse(period).format(DISPLAY_PERIOD);
    }
}
