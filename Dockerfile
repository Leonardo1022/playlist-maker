FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B || true
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21.0.5_11-jre-jammy
WORKDIR /app
RUN groupadd -r appgroup && useradd -r -g appgroup -u 1000 appuser && \
    mkdir -p /data && chown -R appuser:appgroup /data
USER appuser
COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8888
ENV APP_DATABASE_URL=jdbc:sqlite:/data/playlistmaker.db
VOLUME ["/data"]
ENTRYPOINT ["java", "-jar", "app.jar"]
