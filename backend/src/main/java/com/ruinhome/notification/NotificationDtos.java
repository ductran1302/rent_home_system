package com.ruinhome.notification;

import java.time.LocalDateTime;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationResponse(
            Long id,
            NotificationType type,
            String title,
            String body,
            String link,
            boolean read,
            LocalDateTime createdAt) {
    }
}
