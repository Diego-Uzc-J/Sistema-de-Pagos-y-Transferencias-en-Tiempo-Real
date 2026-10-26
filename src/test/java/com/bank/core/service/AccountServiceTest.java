package com.bank.core.service;

import com.bank.core.exception.AccountNotFoundException;
import com.bank.core.exception.InsufficientBalanceException;
import com.bank.core.model.Account;
import com.bank.core.model.User;
import com.bank.core.repository.AccountRepository;
import com.bank.core.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AccountService accountService;

    private static User user(long id) {
        User user = new User();
        user.setId(id);
        user.setName("Cliente " + id);
        user.setEmail("cliente" + id + "@test.com");
        user.setDocumentNumber("DOC-" + id);
        return user;
    }

    private static Account account(long id, String balance) {
        Account account = new Account();
        account.setId(id);
        account.setAccountNumber("ACC-" + id);
        account.setBalance(new BigDecimal(balance));
        account.setCurrency("USD");
        return account;
    }

    @Test
    @DisplayName("Crea la cuenta asociada al usuario con el depósito inicial")
    void creaCuenta() {
        User owner = user(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Account result = accountService.createAccount(1L, "ACC-NEW", "USD", new BigDecimal("150.00"));

        assertThat(result.getUser()).isSameAs(owner);
        assertThat(result.getAccountNumber()).isEqualTo("ACC-NEW");
        assertThat(result.getCurrency()).isEqualTo("USD");
        assertThat(result.getBalance()).isEqualByComparingTo("150.00");

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(owner);
    }

    @Test
    @DisplayName("Usa saldo cero cuando el depósito inicial es nulo")
    void depositoInicialNulo() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Account result = accountService.createAccount(1L, "ACC-ZERO", "USD", null);

        assertThat(result.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Lanza AccountNotFoundException cuando el usuario no existe")
    void usuarioInexistente() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.createAccount(99L, "ACC-X", "USD", BigDecimal.TEN))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Usuario no encontrado");

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    @DisplayName("Lanza AccountNotFoundException al consultar una cuenta inexistente")
    void consultaCuentaInexistente() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccount(99L))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Cuenta no encontrada");
    }

    @Test
    @DisplayName("Deposita usando bloqueo pesimista y guarda el nuevo saldo")
    void deposito() {
        Account account = account(1L, "100.00");
        when(accountRepository.findByIdWithLock(1L)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Account result = accountService.deposit(1L, new BigDecimal("25.50"));

        assertThat(result.getBalance()).isEqualByComparingTo("125.50");
        verify(accountRepository).save(account);
    }

    @Test
    @DisplayName("Retira usando bloqueo pesimista y guarda el nuevo saldo")
    void retiro() {
        Account account = account(1L, "100.00");
        when(accountRepository.findByIdWithLock(1L)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Account result = accountService.withdraw(1L, new BigDecimal("40.00"));

        assertThat(result.getBalance()).isEqualByComparingTo("60.00");
        verify(accountRepository).save(account);
    }

    @Test
    @DisplayName("Rechaza el retiro sin modificar el saldo cuando no hay fondos")
    void retiroSinFondos() {
        Account account = account(1L, "10.00");
        when(accountRepository.findByIdWithLock(1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.withdraw(1L, new BigDecimal("10.01")))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("Fondos insuficientes");

        assertThat(account.getBalance()).isEqualByComparingTo("10.00");
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    @DisplayName("Lanza AccountNotFoundException al operar sobre una cuenta inexistente")
    void operacionCuentaInexistente() {
        when(accountRepository.findByIdWithLock(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.deposit(99L, BigDecimal.TEN))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Cuenta no encontrada");
    }
}
