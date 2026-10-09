# Imagen del backend para Coolify/Render (servicio Docker), en dos etapas:
#   1. Compila con Maven y JDK 21 (sin pruebas: ya corrieron en la integración continua).
#   2. Ejecuta el jar con eclipse-temurin:21-jre como usuario sin privilegios.
# Coolify construye esta imagen desde la rama main.

FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY contracts/pom.xml contracts/pom.xml
COPY app/pom.xml app/pom.xml
COPY quality quality
# Descarga las dependencias en una capa propia para aprovechar la caché de Docker.
RUN mvn -B -ntp -q dependency:go-offline -DskipTests || true
COPY contracts/src contracts/src
COPY app/src app/src
RUN mvn -B -ntp -q package -DskipTests -Dquality.skip=true -Djacoco.skip=true

FROM eclipse-temurin:21-jre
RUN groupadd --system cupo && useradd --system --gid cupo --home /app cupo \
    && mkdir -p /app /data && chown cupo:cupo /app /data
WORKDIR /app
COPY --from=build /workspace/app/target/cupo-backend.jar /app/cupo-backend.jar
USER cupo
ENV STORAGE_PATH=/data \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Djava.awt.headless=true"
EXPOSE 8080
# Coolify enruta el dominio al puerto interno 8080.
ENTRYPOINT ["java", "-jar", "/app/cupo-backend.jar"]
