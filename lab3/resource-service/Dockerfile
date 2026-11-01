# ---- Stage 1: build the JAR (this also runs the tests) ----
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
# Download dependencies first, so Docker can cache them between builds
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package

# ---- Stage 2: small image that only contains Java + the JAR ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/app.jar app.jar
EXPOSE 8082
# Database settings (DB_URL, DB_USER, DB_PASSWORD) are given at runtime, never baked into the image.
ENTRYPOINT ["java", "-jar", "app.jar"]
