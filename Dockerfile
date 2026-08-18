# --- Build stage -------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

# --- Run stage -----------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S leaseguard && adduser -S leaseguard -G leaseguard

COPY --from=build /workspace/target/leaseguard.jar app.jar
COPY data/ data/

RUN chown -R leaseguard:leaseguard /app
USER leaseguard

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
