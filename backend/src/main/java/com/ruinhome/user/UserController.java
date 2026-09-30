package com.ruinhome.user;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserDtos.UserResponse> list() {
        return userService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDtos.UserResponse create(@Valid @RequestBody UserDtos.UserCreateRequest request) {
        return userService.create(request);
    }

    @PutMapping("/{id}")
    public UserDtos.UserResponse update(@PathVariable Long id,
                                        @Valid @RequestBody UserDtos.UserUpdateRequest request) {
        return userService.update(id, request);
    }
}
