package com.ruinhome.house;

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
@RequestMapping("/api/houses")
public class HouseController {

    private final HouseService houseService;

    public HouseController(HouseService houseService) {
        this.houseService = houseService;
    }

    @GetMapping
    public List<HouseDtos.HouseResponse> list() {
        return houseService.list();
    }

    @GetMapping("/{id}")
    public HouseDtos.HouseResponse get(@PathVariable Long id) {
        return houseService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public HouseDtos.HouseResponse create(@Valid @RequestBody HouseDtos.HouseRequest request) {
        return houseService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public HouseDtos.HouseResponse update(@PathVariable Long id,
                                          @Valid @RequestBody HouseDtos.HouseRequest request) {
        return houseService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> delete(@PathVariable Long id) {
        houseService.softDelete(id);
        return Map.of("message", "Đã xoá nhà");
    }
}
