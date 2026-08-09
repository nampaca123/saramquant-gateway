FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk AS builder
WORKDIR /app

COPY gradlew .
COPY gradle gradle
RUN chmod +x ./gradlew

COPY settings.gradle.kts build.gradle.kts ./

RUN ./gradlew --no-daemon -x test bootJar || true

COPY src src
RUN ./gradlew --no-daemon -x test bootJar

FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=builder /app/build/libs/*.jar /app/app.jar

# 확장 캐시 디렉토리는 호스트 볼륨으로 마운트되어 재기동 시 오프라인 로드된다.
ENV DUCKDB_EXT_DIR=/duckdb-ext
RUN mkdir -p /duckdb-ext /tmp/duckdb

ENV PORT=8080
EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -Dserver.port=${PORT} -jar /app/app.jar"]
