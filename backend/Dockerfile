# Etapa 1: Build de la aplicación Spring Boot
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copiar archivos de configuración de Gradle
COPY gradlew .
COPY gradle ./gradle
COPY build.gradle .
COPY settings.gradle .

# Descargar dependencias en caché
RUN chmod +x ./gradlew && ./gradlew dependencies --no-daemon || true

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

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
