package com.bank.core.service;

import com.bank.core.exception.AccountNotFoundException;
import com.bank.core.model.Account;
import com.bank.core.model.User;
import com.bank.core.repository.AccountRepository;
import com.bank.core.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class AccountService {
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public AccountService(AccountRepository accountRepository, UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Account createAccount(Long userId, String accountNumber, String currency, BigDecimal initialDeposit) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AccountNotFoundException("Usuario no encontrado"));
        Account account = new Account();
        account.setUser(user);
        account.setAccountNumber(accountNumber);
        account.setCurrency(currency);
        account.setBalance(initialDeposit == null ? BigDecimal.ZERO : initialDeposit);
        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public Account getAccount(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("Cuenta no encontrada"));
    }

    @Transactional
    public Account deposit(Long accountId, BigDecimal amount) {
        Account account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Cuenta no encontrada"));
        account.setBalance(account.getBalance().add(amount));
        return accountRepository.save(account);
    }

    @Transactional
    public Account withdraw(Long accountId, BigDecimal amount) {
        Account account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Cuenta no encontrada"));
        if (account.getBalance().compareTo(amount) < 0) {
            throw new com.bank.core.exception.InsufficientBalanceException("Fondos insuficientes para realizar la transacción");
        }
        account.setBalance(account.getBalance().subtract(amount));
        return accountRepository.save(account);
    }
}
