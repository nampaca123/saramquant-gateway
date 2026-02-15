FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

COPY gradlew .
COPY gradle gradle
RUN chmod +x ./gradlew

COPY settings.gradle.kts build.gradle.kts ./

RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon -x test bootJar || true

COPY src src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon -x test bootJar

FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=builder /app/build/libs/*.jar /app/app.jar

ENV PORT=8080
EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -Dserver.port=${PORT} -jar /app/app.jar"]
