package com.bank.core.controller;

import com.bank.core.dto.AccountRequestDto;
import com.bank.core.dto.AccountResponseDto;
import com.bank.core.model.Account;
import com.bank.core.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponseDto> createAccount(@Valid @RequestBody AccountRequestDto request) {
        Account account = accountService.createAccount(request.getUserId(), request.getAccountNumber(), request.getCurrency(), request.getInitialDeposit());
        AccountResponseDto response = new AccountResponseDto();
        response.setId(account.getId());
        response.setAccountNumber(account.getAccountNumber());
        response.setBalance(account.getBalance());
        response.setCurrency(account.getCurrency());
        response.setUserId(account.getUser() != null ? account.getUser().getId() : null);
        response.setCreatedAt(account.getCreatedAt());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponseDto> getAccount(@PathVariable Long id) {
        Account account = accountService.getAccount(id);
        AccountResponseDto response = new AccountResponseDto();
        response.setId(account.getId());
        response.setAccountNumber(account.getAccountNumber());
        response.setBalance(account.getBalance());
        response.setCurrency(account.getCurrency());
        response.setUserId(account.getUser() != null ? account.getUser().getId() : null);
        response.setCreatedAt(account.getCreatedAt());
        return ResponseEntity.ok(response);
    }
}
