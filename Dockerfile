# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Etapa 1: compilación con Maven + JDK 25
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /build

# 1) Copiar solo el POM para cachear las dependencias entre compilaciones.
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests dependency:go-offline

# 2) Copiar el código y empaquetar. Los tests se omiten aquí porque las
#    pruebas de integración necesitan Docker (Testcontainers) y se ejecutan
#    fuera de la imagen con `mvn test`.
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests package

# ---------------------------------------------------------------------------
# Etapa 2: imagen final, solo JRE 25 sobre Alpine
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine AS runtime

# Usuario sin privilegios
RUN addgroup -S app && adduser -S -G app app

WORKDIR /app

# El nombre del JAR lo define el pom.xml; si cambia la versión, actualízalo aquí.
COPY --from=build /build/target/core-banking-lite-0.0.1-SNAPSHOT.jar app.jar
RUN chown -R app:app /app

USER app

# Opciones de JVM ajustables en tiempo de ejecución (docker run -e JAVA_OPTS=...)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

# Comprueba que Tomcat esté escuchando (las conexiones a la BD se validan en el arranque).
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD nc -z 127.0.0.1 8080 || exit 1

# `exec` reemplaza el shell para que la JVM reciba SIGTERM y apague limpio.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
