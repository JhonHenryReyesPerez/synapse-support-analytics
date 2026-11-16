# Etapa 1: Build de la aplicación Spring Boot
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copiar archivos de configuración de Gradle para aprovechar caché de capas
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle

# Pre-descargar wrapper y dependencias de Gradle en una capa cacheada
RUN chmod +x ./gradlew && ./gradlew dependencies --no-daemon --configuration compileClasspath --configuration runtimeClasspath || true

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
