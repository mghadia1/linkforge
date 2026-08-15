# Build the React dashboard first so the API image can serve it from the same origin.
FROM node:20-alpine AS web-build
WORKDIR /build/web
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS api-build
WORKDIR /build/api
COPY api/pom.xml ./
COPY api/src ./src
COPY --from=web-build /build/web/dist ./src/main/resources/static
# Testcontainers tests run in CI; a Docker image build has no Docker daemon.
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=api-build /build/api/target/linkforge-api-*.jar app.jar
USER app
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
