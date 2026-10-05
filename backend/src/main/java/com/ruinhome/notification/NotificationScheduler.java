package com.ruinhome.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationScheduler implements ApplicationListener<ApplicationReadyEvent> {

    private final NotificationScanService scanService;

    @Value("${ruinhome.notification.scan-enabled:true}")
    private boolean scanEnabled;

    public NotificationScheduler(NotificationScanService scanService) {
        this.scanService = scanService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (scanEnabled) {
            runScan("startup");
        }
    }

    @Scheduled(cron = "${ruinhome.notification.cron:0 0 7 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void dailyScan() {
        if (scanEnabled) {
            runScan("daily");
        }
    }

    private void runScan(String trigger) {
        try {
            int created = scanService.scan();
            log.info("Notification scan ({}) created {} notification(s)", trigger, created);
        } catch (Exception e) {
            log.warn("Notification scan ({}) failed: {}", trigger, e.getMessage());
        }
    }
}
