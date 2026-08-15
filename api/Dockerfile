# Build stage: compile and package with Maven, so building the image needs no local Maven.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Copy the POM first so the dependency layer is cached when only sources change.
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
# Tests need a Docker daemon (Testcontainers); they run in CI, not in the image build.
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /build/target/linkforge-api-*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
