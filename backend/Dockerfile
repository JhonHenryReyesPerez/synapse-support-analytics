# Etapa 1: Build de la aplicación Spring Boot
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copiar archivos de configuración de Gradle para aprovechar caché de capas
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle

# Normalizar fin de línea (evitar fallo de CRLF en Windows) y pre-descargar dependencias
RUN sed -i 's/\r$//' ./gradlew && chmod +x ./gradlew && ./gradlew dependencies --no-daemon || true

# Copiar código fuente y compilar el archivo JAR ejecutable
COPY src ./src
RUN ./gradlew bootJar -x test --no-daemon

# Etapa 2: Imagen final liviana de ejecución
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Crear usuario sin privilegios por seguridad
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copiar el JAR generado desde la etapa de compilación
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8051

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
