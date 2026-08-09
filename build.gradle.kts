plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.spring") version "2.3.0"
    id("org.springframework.boot") version "4.0.2"
    id("io.spring.dependency-management") version "1.1.7"
}

kotlin {
    jvmToolchain(25)
}

// DuckDB 확장 설치용 main이 따로 있어 애플리케이션 진입점을 명시한다.
springBoot {
    mainClass.set("me.saramquantgateway.SaramquantGatewayApplicationKt")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("com.bucket4j:bucket4j_jdk17-core:8.16.1")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.0")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

    // 관리자 페이징 응답에만 사용하는 Page/Pageable (JPA 아님)
    implementation("org.springframework.data:spring-data-commons")
    implementation("org.springframework:spring-webflux")
    implementation("io.projectreactor.netty:reactor-netty-http")

    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation(platform("software.amazon.awssdk:bom:2.34.0"))
    implementation("software.amazon.awssdk:sesv2")
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:glue")
    implementation("org.duckdb:duckdb_jdbc:1.4.4.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")

    implementation("com.anthropic:anthropic-java:2.19.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    val envFile = rootProject.file(".env")
    if (envFile.exists()) {
        val env = mutableMapOf<String, String>()
        for (line in envFile.readLines()) {
            if (line.isBlank() || line.startsWith("#")) continue
            val idx = line.indexOf("=")
            if (idx < 0) continue
            val k = line.substring(0, idx).trim()
            val v = line.substring(idx + 1).trim().removeSurrounding("\"")
            env[k] = v
        }
        env.forEach { (k, v) -> environment(k, v) }
    }
}

