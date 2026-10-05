package com.ruinhome.notice;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<NoticeDtos.NoticeResponse> list() {
        return noticeService.list();
    }

    @GetMapping("/active")
    public List<NoticeDtos.NoticeResponse> active() {
        return noticeService.active();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public NoticeDtos.NoticeResponse create(@Valid @RequestBody NoticeDtos.NoticeRequest request) {
        return noticeService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public NoticeDtos.NoticeResponse update(@PathVariable Long id,
                                            @Valid @RequestBody NoticeDtos.NoticeRequest request) {
        return noticeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Map<String, String> delete(@PathVariable Long id) {
        noticeService.delete(id);
        return Map.of("message", "Đã xoá thông báo");
    }
}
