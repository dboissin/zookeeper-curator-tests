FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app
COPY pom.xml .
COPY src ./src

RUN mvn clean package

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=builder /app/target/zookeeper-curator-project-1.0-SNAPSHOT.jar app.jar
COPY --from=builder /app/src/main/resources/logback.xml logback.xml

CMD ["java", "-Dlogback.configurationFile=/app/logback.xml", "-jar", "app.jar", "--worker"]
