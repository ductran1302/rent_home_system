package com.ruinhome.notice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class NoticeDtos {

    private NoticeDtos() {
    }

    public record NoticeRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 1000) String content,
            Long houseId,
            LocalDateTime startsAt,
            LocalDateTime endsAt) {
    }

    public record NoticeResponse(
            Long id,
            String title,
            String content,
            Long houseId,
            String houseName,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            boolean active,
            LocalDateTime createdAt) {
    }
}
