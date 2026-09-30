package com.ruinhome.room;

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
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping("/by-house/{houseId}")
    public List<RoomDtos.RoomResponse> listByHouse(@PathVariable Long houseId) {
        return roomService.listByHouse(houseId);
    }

    @GetMapping("/{id}")
    public RoomDtos.RoomResponse get(@PathVariable Long id) {
        return roomService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public RoomDtos.RoomResponse create(@Valid @RequestBody RoomDtos.RoomRequest request) {
        return roomService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public RoomDtos.RoomResponse update(@PathVariable Long id,
                                        @Valid @RequestBody RoomDtos.RoomRequest request) {
        return roomService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> delete(@PathVariable Long id) {
        roomService.softDelete(id);
        return Map.of("message", "Đã xoá phòng");
    }
}
