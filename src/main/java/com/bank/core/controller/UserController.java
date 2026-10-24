package com.bank.core.controller;

import com.bank.core.dto.UserRequestDto;
import com.bank.core.dto.UserResponseDto;
import com.bank.core.model.User;
import com.bank.core.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponseDto> createUser(@Valid @RequestBody UserRequestDto request) {
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setDocumentNumber(request.getDocumentNumber());
        User created = userService.createUser(user);
        UserResponseDto response = new UserResponseDto();
        response.setId(created.getId());
        response.setName(created.getName());
        response.setEmail(created.getEmail());
        response.setDocumentNumber(created.getDocumentNumber());
        response.setCreatedAt(created.getCreatedAt());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
