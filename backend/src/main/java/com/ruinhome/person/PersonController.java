package com.ruinhome.person;

import com.ruinhome.person.PersonDtos.PersonRequest;
import com.ruinhome.person.PersonDtos.PersonResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/persons")
public class PersonController {

    private final PersonService personService;

    public PersonController(PersonService personService) {
        this.personService = personService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Map<String, Object> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = personService.list(q, page, Math.min(size, 100));
        return Map.of(
                "items", result.getContent(),
                "total", result.getTotalElements(),
                "page", result.getNumber(),
                "size", result.getSize());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public PersonResponse get(@PathVariable Long id) {
        return personService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public PersonResponse create(@Valid @RequestBody PersonRequest request) {
        return personService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public PersonResponse update(@PathVariable Long id, @Valid @RequestBody PersonRequest request) {
        return personService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Map<String, String> delete(@PathVariable Long id) {
        personService.softDelete(id);
        return Map.of("message", "Đã xoá người");
    }
}
