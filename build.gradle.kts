import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.springframework.boot") version "3.5.13"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.github.ben-manes.versions") version "0.53.0"
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
    kotlin("jvm") version "2.3.10"
    kotlin("plugin.spring") version "2.3.10"
    kotlin("plugin.jpa") version "2.3.10"
}

group = "no.novari"
version = "0.0.1-SNAPSHOT"

val javaVersion =
    providers
        .gradleProperty("javaVersion")
        .orElse("25")
        .map(String::toInt)
        .get()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
}

kotlin {
    jvmToolchain(javaVersion)
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
    }
}

tasks.jar {
    isEnabled = false
}

repositories {
    mavenCentral()
    maven("https://repo.fintlabs.no/releases")
    mavenLocal()
}

dependencies {
    constraints {
        testImplementation("org.apache.commons:commons-compress:1.27.1") {
            because("Fixes CVE-2024-25710 and CVE-2024-26308 in the Testcontainers transitive dependency")
        }
    }

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("io.hypersistence:hypersistence-utils-hibernate-63:3.11.0")

    implementation("no.novari:flyt-web-resource-server:2.0.0")
    implementation("no.novari:flyt-kafka:4.0.0")

    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    compileOnly("org.springframework.security:spring-security-config")
    compileOnly("org.springframework.security:spring-security-web")
    compileOnly("org.projectlombok:lombok")

    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    runtimeOnly("org.postgresql:postgresql")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    annotationProcessor("org.projectlombok:lombok")

    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-core")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.2.3")
    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.1"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.test {
    useJUnitPlatform {
        excludeTags("performance")
    }
    // Workaround for bug: https://github.com/testcontainers/testcontainers-java/issues/11212
    systemProperty("api.version", "1.44")
}

tasks.register<Test>("performanceTest") {
    useJUnitPlatform {
        includeTags("performance")
    }
}

ktlint {
    version.set("1.8.0")
}

tasks.named("check") {
    dependsOn("ktlintCheck")
}
