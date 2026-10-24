package com.bank.core.service;

import com.bank.core.exception.AccountNotFoundException;
import com.bank.core.exception.InsufficientBalanceException;
import com.bank.core.model.Account;
import com.bank.core.model.Transaction;
import com.bank.core.repository.AccountRepository;
import com.bank.core.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class TransferService {
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public TransferService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public Transaction transfer(Long sourceAccountId, Long targetAccountId, BigDecimal amount) {
        if (sourceAccountId.equals(targetAccountId)) {
            throw new InsufficientBalanceException("No se permite transferir a la misma cuenta");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InsufficientBalanceException("El monto debe ser mayor a cero");
        }

        // Ordenar por ID para prevenir deadlocks
        List<Long> ids = new ArrayList<>();
        ids.add(sourceAccountId);
        ids.add(targetAccountId);
        ids.sort(Comparator.naturalOrder());

        Account account1 = accountRepository.findByIdWithLock(ids.get(0))
                .orElseThrow(() -> new AccountNotFoundException("Cuenta no encontrada"));
        Account account2 = accountRepository.findByIdWithLock(ids.get(1))
                .orElseThrow(() -> new AccountNotFoundException("Cuenta no encontrada"));

        Account sourceAccount;
        Account targetAccount;
        if (account1.getId().equals(sourceAccountId)) {
            sourceAccount = account1;
            targetAccount = account2;
        } else {
            sourceAccount = account2;
            targetAccount = account1;
        }

        if (!sourceAccount.getCurrency().equals(targetAccount.getCurrency())) {
            throw new InsufficientBalanceException("Las cuentas deben tener la misma moneda");
        }

        if (sourceAccount.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Fondos insuficientes para realizar la transacción en la cuenta origen.");
        }

        sourceAccount.setBalance(sourceAccount.getBalance().subtract(amount));
        targetAccount.setBalance(targetAccount.getBalance().add(amount));
        accountRepository.save(sourceAccount);
        accountRepository.save(targetAccount);

        Transaction transaction = new Transaction();
        transaction.setSourceAccount(sourceAccount);
        transaction.setTargetAccount(targetAccount);
        transaction.setAmount(amount);
        transaction.setType("TRANSFER");
        transaction.setStatus("SUCCESS");
        return transactionRepository.save(transaction);
    }
}
