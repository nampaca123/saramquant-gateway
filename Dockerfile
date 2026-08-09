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

# curl은 ECS 컨테이너 헬스체크가 사용한다.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/build/libs/*.jar /app/app.jar

# DuckDB 확장은 빌드 시 설치·LOAD 검증까지 마쳐 런타임 네트워크 의존을 없앤다.
ENV DUCKDB_EXT_DIR=/duckdb-ext
RUN mkdir -p /duckdb-ext /tmp/duckdb \
    && java -Dloader.main=me.saramquantgateway.infra.duckdb.DuckDbExtensionInstallerKt \
       -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher

ENV PORT=8080
EXPOSE 8080

# 힙 640m + DuckDB 384MB + 네이티브 여유 ≈ 1280MB 컨테이너 한도에 맞춘 예산이다.
ENV JAVA_OPTS="-XX:+UseContainerSupport -Xmx640m -XX:MaxMetaspaceSize=192m"
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -Dserver.port=${PORT} -jar /app/app.jar"]
