package com.bank.core.integration;

import com.bank.core.exception.InsufficientBalanceException;
import com.bank.core.model.Account;
import com.bank.core.model.User;
import com.bank.core.repository.AccountRepository;
import com.bank.core.repository.UserRepository;
import com.bank.core.service.TransferService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Concurrencia real sobre PostgreSQL (bloqueo pesimista)")
class TransferConcurrencyIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    private long crearCuenta(String saldoInicial) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setName("Concurrencia " + suffix);
        user.setEmail("conc-" + suffix + "@test.com");
        user.setDocumentNumber("DOC" + suffix);
        userRepository.save(user);

        Account account = new Account();
        account.setAccountNumber("ACC" + suffix);
        account.setCurrency("USD");
        account.setBalance(new BigDecimal(saldoInicial));
        account.setUser(user);
        return accountRepository.save(account).getId();
    }

    private BigDecimal saldo(long accountId) {
        return accountRepository.findById(accountId).orElseThrow().getBalance();
    }

    private void esperarInicio(CountDownLatch ready, CountDownLatch start, int hilos) throws InterruptedException {
        assertThat(ready.await(15, TimeUnit.SECONDS))
                .as("todos los hilos deben estar listos para partir a la vez")
                .isTrue();
        start.countDown();
    }

    @Test
    @DisplayName("20 transferencias simultáneas de 10 sobre saldo 100: solo 10 exitosas y sin sobregiro")
    void transferenciasConcurrentesNoPermitenSobregiro() throws Exception {
        long origen = crearCuenta("100.00");
        long destino = crearCuenta("0.00");
        int hilos = 20;

        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch ready = new CountDownLatch(hilos);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> resultados = new ArrayList<>();

        try {
            for (int i = 0; i < hilos; i++) {
                resultados.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(15, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("No inició la barrera de concurrencia");
                    }
                    try {
                        transferService.transfer(origen, destino, new BigDecimal("10.00"));
                        return true;
                    } catch (InsufficientBalanceException ex) {
                        return false;
                    }
                }));
            }

            esperarInicio(ready, start, hilos);

            int exitosas = 0;
            int rechazadas = 0;
            for (Future<Boolean> resultado : resultados) {
                if (resultado.get(60, TimeUnit.SECONDS)) {
                    exitosas++;
                } else {
                    rechazadas++;
                }
            }

            assertThat(exitosas).as("transferencias exitosas").isEqualTo(10);
            assertThat(rechazadas).as("transferencias rechazadas por fondos").isEqualTo(10);
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(saldo(origen)).isEqualByComparingTo("0.00");
        assertThat(saldo(destino)).isEqualByComparingTo("100.00");

        Integer negativas = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE balance < 0", Integer.class);
        assertThat(negativas).as("cuentas con saldo negativo").isZero();

        Integer transacciones = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE status = 'SUCCESS'", Integer.class);
        assertThat(transacciones).isEqualTo(10);
    }

    @Test
    @DisplayName("Transferencias bidireccionales simultáneas no producen deadlocks")
    void transferenciasBidireccionalesNoProducenDeadlock() throws Exception {
        long cuentaA = crearCuenta("100.00");
        long cuentaB = crearCuenta("100.00");
        int porDireccion = 10;
        int hilos = porDireccion * 2;

        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch ready = new CountDownLatch(hilos);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Void>> resultados = new ArrayList<>();

        try {
            for (int i = 0; i < porDireccion; i++) {
                resultados.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(15, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("No inició la barrera de concurrencia");
                    }
                    transferService.transfer(cuentaA, cuentaB, new BigDecimal("1.00"));
                    return null;
                }));
                resultados.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(15, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("No inició la barrera de concurrencia");
                    }
                    transferService.transfer(cuentaB, cuentaA, new BigDecimal("1.00"));
                    return null;
                }));
            }

            esperarInicio(ready, start, hilos);

            for (Future<Void> resultado : resultados) {
                // Si hubiese deadlock, PostgreSQL abortaría una transacción y get() lanzaría excepción.
                resultado.get(90, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(saldo(cuentaA)).isEqualByComparingTo("100.00");
        assertThat(saldo(cuentaB)).isEqualByComparingTo("100.00");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(balance), 0) FROM accounts", BigDecimal.class))
                .isEqualByComparingTo("200.00");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE status = 'SUCCESS'", Integer.class))
                .isEqualTo(20);
    }
}
