package com.bank.core.controller;

import com.bank.core.dto.TransferRequestDto;
import com.bank.core.dto.TransferResponseDto;
import com.bank.core.model.Transaction;
import com.bank.core.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {
    private final TransferService transferService;

    public TransactionController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransferResponseDto> transfer(@Valid @RequestBody TransferRequestDto request) {
        Transaction transaction = transferService.transfer(request.getSourceAccountId(), request.getTargetAccountId(), request.getAmount());
        TransferResponseDto response = new TransferResponseDto();
        response.setTransactionId(transaction.getId());
        response.setSourceAccountId(transaction.getSourceAccount() != null ? transaction.getSourceAccount().getId() : null);
        response.setTargetAccountId(transaction.getTargetAccount() != null ? transaction.getTargetAccount().getId() : null);
        response.setAmount(transaction.getAmount());
        response.setType(transaction.getType());
        response.setStatus(transaction.getStatus());
        response.setTimestamp(transaction.getTimestamp());
        return ResponseEntity.ok(response);
    }
}
