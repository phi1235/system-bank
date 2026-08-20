# syntax=docker/dockerfile:1
# Build from backend/ context. Layered POMs + Maven cache; only this service (+ common-lib) sources.
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

COPY pom.xml .
COPY common-lib/pom.xml common-lib/
COPY discovery-server/pom.xml discovery-server/
COPY api-gateway/pom.xml api-gateway/
COPY auth-service/pom.xml auth-service/
COPY customer-service/pom.xml customer-service/
COPY account-service/pom.xml account-service/
COPY transaction-service/pom.xml transaction-service/
COPY notification-service/pom.xml notification-service/
COPY corporate-service/pom.xml corporate-service/

COPY common-lib/src common-lib/src
COPY auth-service/src auth-service/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -pl auth-service -am package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache wget && addgroup -S bank && adduser -S bank -G bank
COPY --from=build /workspace/auth-service/target/auth-service-*.jar app.jar
USER bank
ENTRYPOINT ["java", "-jar", "app.jar"]
