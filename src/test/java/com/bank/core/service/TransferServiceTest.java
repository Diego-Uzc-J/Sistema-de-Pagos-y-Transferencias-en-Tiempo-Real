package com.bank.core.service;

import com.bank.core.exception.AccountNotFoundException;
import com.bank.core.exception.InsufficientBalanceException;
import com.bank.core.model.Account;
import com.bank.core.model.Transaction;
import com.bank.core.repository.AccountRepository;
import com.bank.core.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private TransferService transferService;

    private static Account account(long id, String balance, String currency) {
        Account account = new Account();
        account.setId(id);
        account.setAccountNumber("ACC-" + id);
        account.setBalance(new BigDecimal(balance));
        account.setCurrency(currency);
        return account;
    }

    private void stubAccounts(Account first, Account second) {
        when(accountRepository.findByIdWithLock(first.getId())).thenReturn(Optional.of(first));
        when(accountRepository.findByIdWithLock(second.getId())).thenReturn(Optional.of(second));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction transaction = invocation.getArgument(0);
            transaction.setId(99L);
            return transaction;
        });
    }

    @Test
    @DisplayName("Transfiere el monto, actualiza ambos saldos y registra la transacción como SUCCESS")
    void transferenciaExitosa() {
        Account source = account(1L, "100.00", "USD");
        Account target = account(2L, "50.00", "USD");
        stubAccounts(source, target);

        Transaction result = transferService.transfer(1L, 2L, new BigDecimal("30.00"));

        assertThat(source.getBalance()).isEqualByComparingTo("70.00");
        assertThat(target.getBalance()).isEqualByComparingTo("80.00");

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction saved = captor.getValue();
        assertThat(saved.getSourceAccount()).isSameAs(source);
        assertThat(saved.getTargetAccount()).isSameAs(target);
        assertThat(saved.getAmount()).isEqualByComparingTo("30.00");
        assertThat(saved.getType()).isEqualTo("TRANSFER");
        assertThat(saved.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("Bloquea las cuentas en orden ascendente de ID para evitar deadlocks")
    void bloqueaCuentasEnOrdenAscendente() {
        Account target = account(3L, "50.00", "USD");
        Account source = account(7L, "100.00", "USD");
        stubAccounts(target, source);

        transferService.transfer(7L, 3L, new BigDecimal("10.00"));

        InOrder order = inOrder(accountRepository);
        order.verify(accountRepository).findByIdWithLock(3L);
        order.verify(accountRepository).findByIdWithLock(7L);
        assertThat(source.getBalance()).isEqualByComparingTo("90.00");
        assertThat(target.getBalance()).isEqualByComparingTo("60.00");
    }

    @Test
    @DisplayName("Rechaza la transferencia sin modificar saldos cuando no hay fondos")
    void fondosInsuficientes() {
        Account source = account(1L, "10.00", "USD");
        Account target = account(2L, "50.00", "USD");
        when(accountRepository.findByIdWithLock(1L)).thenReturn(Optional.of(source));
        when(accountRepository.findByIdWithLock(2L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> transferService.transfer(1L, 2L, new BigDecimal("20.00")))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("Fondos insuficientes");

        assertThat(source.getBalance()).isEqualByComparingTo("10.00");
        assertThat(target.getBalance()).isEqualByComparingTo("50.00");
        verify(accountRepository, never()).save(any(Account.class));
        verifyNoInteractions(transactionRepository);
    }

    @Test
    @DisplayName("Rechaza transferir a la misma cuenta sin tocar la base de datos")
    void mismaCuenta() {
        assertThatThrownBy(() -> transferService.transfer(1L, 1L, new BigDecimal("10.00")))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("misma cuenta");

        verifyNoInteractions(accountRepository, transactionRepository);
    }

    @Test
    @DisplayName("Rechaza montos nulos, cero o negativos")
    void montosInvalidos() {
        assertThatThrownBy(() -> transferService.transfer(1L, 2L, null))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("mayor a cero");

        assertThatThrownBy(() -> transferService.transfer(1L, 2L, BigDecimal.ZERO))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("mayor a cero");

        assertThatThrownBy(() -> transferService.transfer(1L, 2L, new BigDecimal("-5.00")))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("mayor a cero");

        verifyNoInteractions(accountRepository, transactionRepository);
    }

    @Test
    @DisplayName("Rechaza transferencias entre monedas distintas")
    void monedasDistintas() {
        Account source = account(1L, "100.00", "USD");
        Account target = account(2L, "100.00", "EUR");
        when(accountRepository.findByIdWithLock(1L)).thenReturn(Optional.of(source));
        when(accountRepository.findByIdWithLock(2L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> transferService.transfer(1L, 2L, new BigDecimal("10.00")))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("misma moneda");

        assertThat(source.getBalance()).isEqualByComparingTo("100.00");
        assertThat(target.getBalance()).isEqualByComparingTo("100.00");
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    @DisplayName("Lanza AccountNotFoundException cuando la cuenta origen no existe")
    void cuentaOrigenInexistente() {
        when(accountRepository.findByIdWithLock(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.transfer(1L, 2L, new BigDecimal("10.00")))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Cuenta no encontrada");

        verifyNoInteractions(transactionRepository);
    }

    @Test
    @DisplayName("Lanza AccountNotFoundException cuando la cuenta destino no existe")
    void cuentaDestinoInexistente() {
        when(accountRepository.findByIdWithLock(1L)).thenReturn(Optional.of(account(1L, "100.00", "USD")));
        when(accountRepository.findByIdWithLock(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.transfer(1L, 2L, new BigDecimal("10.00")))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Cuenta no encontrada");

        verifyNoInteractions(transactionRepository);
    }
}
