# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml .
COPY common-lib common-lib
COPY discovery-server discovery-server
COPY api-gateway api-gateway
COPY auth-service auth-service
COPY customer-service customer-service
COPY account-service account-service
COPY transaction-service transaction-service
COPY notification-service notification-service
RUN mvn -q -pl auth-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache wget && addgroup -S bank && adduser -S bank -G bank
COPY --from=build /workspace/auth-service/target/auth-service-*.jar app.jar
USER bank
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
