# 🏦 Sistema de Pagos y Transferencias en Tiempo Real

<div align="center">

[![Java](https://img.shields.io/badge/Java-25%20(LTS)-orange?style=for-the-badge&logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-brightgreen?style=for-the-badge&logo=springboot)](https://spring.io/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Database-blue?style=for-the-badge&logo=postgresql)](https://www.postgresql.org/)
[![Flyway](https://img.shields.io/badge/Flyway-Migrations-red?style=for-the-badge&logo=flyway)](https://flywaydb.org/)
[![Testcontainers](https://img.shields.io/badge/Testcontainers-Integration%20Tests-orange?style=for-the-badge&logo=docker)](https://www.testcontainers.org/)

*Una API REST robusta, transancional y de alta concurrencia diseñada para replicar el núcleo financiero de un sistema bancario moderno.*

</div>

---

## 🚀 El Reto Técnico (¿Por qué este proyecto?)

En la industria financiera (*Fintech* y Banca Core), el fallo más crítico no es una interfaz rota, sino la **inconsistencia de datos monetarios** (ej. dinero que desaparece entre cuentas o sobregiros por condiciones de carrera). 

Este proyecto fue desarrollado bajo una metodología de **Spec-Driven Development (SDD)** para resolver problemas complejos del backend moderno:
- **Transacciones Atómicas:** Uso estricto de `@Transactional` para asegurar un *rollback* completo ante cualquier fallo, garantizando que el dinero debitado en la cuenta origen siempre llegue a la cuenta destino.
- **Control de Concurrencia (Race Conditions):** Implementación de **Bloqueos Pesimistas (`PESSIMISTIC_WRITE`)** a nivel de base de datos para evitar que múltiples hilos retiren fondos simultáneamente de una misma cuenta generando saldos negativos.
- **Prevención de Deadlocks:** Ordenamiento estricto de cuentas por ID antes de aplicar bloqueos cruzados en transferencias bidireccionales.

---

## 🛠️ Stack Tecnológico

- **Core:** Java 25+, Spring Boot 4.x
- **Persistencia:** Spring Data JPA / Hibernate, PostgreSQL
- **Migraciones de BD:** Flyway
- **Validación de Datos:** Bean Validation API
- **Testing:** JUnit 6, Mockito, **Testcontainers** (Docker para pruebas de integración reales)

---

## 📂 Estructura de Directorios

El proyecto sigue una arquitectura modular en capas limpia y escalable:

```text
src/
├── main/
│   ├── java/com/bank/core/
│   │   ├── controller/      # Endpoints REST (UserController, AccountController, TransactionController)
│   │   ├── service/         # Lógica de negocio, transacciones y control de concurrencia
│   │   ├── repository/      # Interfaces Spring Data JPA con consultas custom de bloqueo
│   │   ├── model/           # Entidades JPA (User, Account, Transaction)
│   │   ├── dto/             # Objetos de transferencia de datos con validaciones
│   │   └── exception/       # Manejo global de errores (@ControllerAdvice)
│   └── resources/
│       ├── db/migration/    # Scripts SQL versionados con Flyway (V1__init_schema.sql)
│       └── application.yml  # Configuración del datasource y perfiles
└── test/
    └── java/com/bank/core/
        ├── service/         # Pruebas unitarias (JUnit 5 + Mockito)
        └── integration/     # Pruebas de integración y concurrencia con Testcontainers
```

---

## 📊 Modelo de Datos (Esquema Relacional)
El sistema utiliza tres tablas principales optimizadas con tipos de datos monetarios exactos (DECIMAL(15,2) para prevenir errores de punto flotante):

- **users:** Almacena la información de los clientes.
- **accounts:** Gestiona los números de cuenta, saldos y divisas asociadas a cada usuario.
- **transactions:** Registra la trazabilidad histórica de depósitos, retiros y transferencias con sus respectivos estados (SUCCESS, FAILED).

---

🔌 Contratos de la API (Endpoints Principales)

| Método | Endpoint | Descripción |
| :--- | :--- | :--- |
| `POST` | `/api/v1/users` | Registra un nuevo cliente en el sistema. |
| `POST` | `/api/v1/accounts` | Abre una nueva cuenta bancaria con depósito inicial. |
| `GET` | `/api/v1/accounts/{id}` | Consulta el saldo actual y detalles de una cuenta. |
| `POST` | `/api/v1/transactions/transfer` | Ejecuta una transferencia monetaria inmediata, atómica y segura entre cuentas. |

...

🧪 Estrategia de Pruebas y Calidad

La aplicación cuenta con una sólida pirámide de testing:

* **Pruebas Unitarias:** Aislamiento de la lógica de negocio en servicios mediante **Mockito**.
* **Pruebas de Integración y Concurrencia:** Uso de **Testcontainers** para levantar un contenedor efímero de PostgreSQL y simular hilos concurrentes (`ExecutorService`) que validan la efectividad del bloqueo pesimista ante intentos de sobregiro simultáneo.

---

🏗️ Ejecución del Proyecto por Hitos (SDD)

El desarrollo de este repositorio fue estructurado de manera incremental en 5 hitos auditados mediante Git Tags:

* `v0.1-milestone1` : Configuración e infraestructura Flyway.
* `v0.2-milestone2` : Persistencia JPA y Bloqueos Pesimistas.
* `v0.3-milestone3` : Lógica transaccional y prevención de Deadlocks.
* `v0.4-milestone4` : Exposición de Endpoints REST y Bean Validation.
* `v1.0-release` : Suite completa de pruebas con Testcontainers y Concurrencia.

---
*Creado por Ing. Diego A. Uzcátegui J. | www.linkedin.com/in/diego-uzc-j | Portafolio Profesional*
