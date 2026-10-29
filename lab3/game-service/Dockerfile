# ---------- Stage 1: build the jar with Maven (no Java/Maven needed on your laptop) ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom.xml first and download dependencies: this layer is cached
# and only re-runs when pom.xml changes -> much faster rebuilds.
COPY pom.xml .
RUN mvn -q dependency:go-offline

COPY src ./src
RUN mvn -q package -DskipTests

# ---------- Stage 2: small runtime image with only the JRE + jar ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/game-service.jar app.jar

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]

