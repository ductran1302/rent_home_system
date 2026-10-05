package com.ruinhome.stats;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Year;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping
    public StatsResponse stats() {
        return statsService.stats();
    }

    @GetMapping("/revenue")
    public StatsDtos.RevenueResponse revenue(@RequestParam(required = false) Integer year) {
        return statsService.revenue(year != null ? year : Year.now().getValue());
    }
}
