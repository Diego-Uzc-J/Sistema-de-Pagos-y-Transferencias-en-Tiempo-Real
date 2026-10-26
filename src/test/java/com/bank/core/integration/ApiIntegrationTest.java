package com.bank.core.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("API REST contra PostgreSQL real")
class ApiIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    private ResultActions postJson(String path, String body) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode json(String body) throws Exception {
        return JSON.readTree(body);
    }

    private long crearUsuario(String email, String documento) throws Exception {
        String response = postJson("/api/v1/users", """
                {"name":"Cliente de Prueba","email":"%s","documentNumber":"%s"}
                """.formatted(email, documento))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json(response).get("id").asLong();
    }

    private long crearCuenta(long userId, String numero, String moneda, String deposito) throws Exception {
        String response = postJson("/api/v1/accounts", """
                {"userId":%d,"accountNumber":"%s","currency":"%s","initialDeposit":%s}
                """.formatted(userId, numero, moneda, deposito))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json(response).get("id").asLong();
    }

    private BigDecimal saldo(long accountId) throws Exception {
        String response = mockMvc.perform(get("/api/v1/accounts/{id}", accountId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json(response).get("balance").decimalValue();
    }

    @Test
    @DisplayName("Flujo completo: usuario -> cuentas -> transferencia -> saldos actualizados")
    void flujoCompletoDeTransferencia() throws Exception {
        long ana = crearUsuario("ana@test.com", "DOC-ANA");
        long luis = crearUsuario("luis@test.com", "DOC-LUIS");
        long origen = crearCuenta(ana, "ACC-ORIGEN", "USD", "100.00");
        long destino = crearCuenta(luis, "ACC-DESTINO", "USD", "50.00");

        String response = postJson("/api/v1/transactions/transfer", """
                {"sourceAccountId":%d,"targetAccountId":%d,"amount":30.00}
                """.formatted(origen, destino))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.amount").value(30.00))
                .andExpect(jsonPath("$.sourceAccountId").value(origen))
                .andExpect(jsonPath("$.targetAccountId").value(destino))
                .andExpect(jsonPath("$.transactionId").isNumber())
                .andReturn().getResponse().getContentAsString();

        assertThat(json(response).get("timestamp").isTextual()).isTrue();
        assertThat(saldo(origen)).isEqualByComparingTo("70.00");
        assertThat(saldo(destino)).isEqualByComparingTo("80.00");

        Integer transacciones = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE status = 'SUCCESS'", Integer.class);
        assertThat(transacciones).isEqualTo(1);
    }

    @Test
    @DisplayName("La migración de Flyway quedó aplicada")
    void flywayAplicoLaMigracion() {
        Integer migraciones = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        assertThat(migraciones).isEqualTo(1);

        Integer tablas = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name IN ('users', 'accounts', 'transactions')
                """, Integer.class);
        assertThat(tablas).isEqualTo(3);
    }

    @Test
    @DisplayName("Email duplicado responde 409 con mensaje de conflicto")
    void emailDuplicadoDevuelve409() throws Exception {
        crearUsuario("duplicado@test.com", "DOC-1");

        postJson("/api/v1/users", """
                {"name":"Otro","email":"duplicado@test.com","documentNumber":"DOC-2"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Ya existe un usuario con ese email"))
                .andExpect(jsonPath("$.path").value("/api/v1/users"));
    }

    @Test
    @DisplayName("Documento duplicado responde 409")
    void documentoDuplicadoDevuelve409() throws Exception {
        crearUsuario("primero@test.com", "DOC-UNICO");

        postJson("/api/v1/users", """
                {"name":"Otro","email":"segundo@test.com","documentNumber":"DOC-UNICO"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ya existe un usuario con ese número de documento"));
    }

    @Test
    @DisplayName("Número de cuenta duplicado responde 409")
    void cuentaDuplicadaDevuelve409() throws Exception {
        long userId = crearUsuario("cuentas@test.com", "DOC-CUENTAS");
        crearCuenta(userId, "ACC-DUP", "USD", "10.00");

        postJson("/api/v1/accounts", """
                {"userId":%d,"accountNumber":"ACC-DUP","currency":"USD","initialDeposit":5.00}
                """.formatted(userId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ya existe una cuenta con ese número"));
    }

    @Test
    @DisplayName("Validación responde 400 con el detalle por campo")
    void validacionDevuelve400ConDetalle() throws Exception {
        postJson("/api/v1/users", """
                {"name":"","email":"no-es-email","documentNumber":""}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Error de validación en la petición"))
                .andExpect(jsonPath("$.details.name").exists())
                .andExpect(jsonPath("$.details.email").exists())
                .andExpect(jsonPath("$.details.documentNumber").exists());
    }

    @Test
    @DisplayName("Transferencia sin fondos responde 400 y deja los saldos intactos")
    void transferenciaSinFondosNoModificaSaldos() throws Exception {
        long userId = crearUsuario("fondos@test.com", "DOC-FONDOS");
        long origen = crearCuenta(userId, "ACC-POBRE", "USD", "10.00");
        long destino = crearCuenta(userId, "ACC-RICA", "USD", "0.00");

        postJson("/api/v1/transactions/transfer", """
                {"sourceAccountId":%d,"targetAccountId":%d,"amount":50.00}
                """.formatted(origen, destino))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Fondos insuficientes para realizar la transacción en la cuenta origen."));

        assertThat(saldo(origen)).isEqualByComparingTo("10.00");
        assertThat(saldo(destino)).isEqualByComparingTo("0.00");
        Integer transacciones = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class);
        assertThat(transacciones).isZero();
    }

    @Test
    @DisplayName("Transferencia entre monedas distintas responde 400")
    void monedasDistintasDevuelve400() throws Exception {
        long userId = crearUsuario("monedas@test.com", "DOC-MONEDAS");
        long usd = crearCuenta(userId, "ACC-USD", "USD", "100.00");
        long eur = crearCuenta(userId, "ACC-EUR", "EUR", "100.00");

        postJson("/api/v1/transactions/transfer", """
                {"sourceAccountId":%d,"targetAccountId":%d,"amount":10.00}
                """.formatted(usd, eur))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Las cuentas deben tener la misma moneda"));
    }

    @Test
    @DisplayName("Transferencia a la misma cuenta responde 400")
    void mismaCuentaDevuelve400() throws Exception {
        long userId = crearUsuario("misma@test.com", "DOC-MISMA");
        long cuenta = crearCuenta(userId, "ACC-MISMA", "USD", "100.00");

        postJson("/api/v1/transactions/transfer", """
                {"sourceAccountId":%d,"targetAccountId":%d,"amount":10.00}
                """.formatted(cuenta, cuenta))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No se permite transferir a la misma cuenta"));
    }

    @Test
    @DisplayName("Monto inválido responde 400 por Bean Validation")
    void montoInvalidoDevuelve400() throws Exception {
        long userId = crearUsuario("monto@test.com", "DOC-MONTO");
        long origen = crearCuenta(userId, "ACC-MONTO-A", "USD", "100.00");
        long destino = crearCuenta(userId, "ACC-MONTO-B", "USD", "0.00");

        postJson("/api/v1/transactions/transfer", """
                {"sourceAccountId":%d,"targetAccountId":%d,"amount":-1.00}
                """.formatted(origen, destino))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.amount").exists());
    }

    @Test
    @DisplayName("Cuenta inexistente responde 404")
    void cuentaInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Cuenta no encontrada"));
    }

    @Test
    @DisplayName("Transferencia desde cuenta inexistente responde 404")
    void transferenciaDesdeCuentaInexistenteDevuelve404() throws Exception {
        long userId = crearUsuario("inexistente@test.com", "DOC-INEX");
        long destino = crearCuenta(userId, "ACC-EXISTE", "USD", "10.00");

        postJson("/api/v1/transactions/transfer", """
                {"sourceAccountId":999999,"targetAccountId":%d,"amount":1.00}
                """.formatted(destino))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Cuenta no encontrada"));
    }
}
