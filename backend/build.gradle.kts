import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spotless)
}

group = "com.cognition"
version = "0.1.0-SNAPSHOT"
description = "Enablement Session Tracker API"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.java.get())
    }
}

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    annotationProcessor(platform(SpringBootPlugin.BOM_COORDINATES))

    implementation(libs.boot.webmvc)
    implementation(libs.boot.validation)
    implementation(libs.boot.security)
    implementation(libs.boot.oauth2.resource.server)
    implementation(libs.boot.data.jpa)
    implementation(libs.boot.flyway)
    implementation(libs.boot.actuator)
    implementation(libs.boot.restclient)
    implementation(libs.boot.jackson)
    implementation(libs.springdoc.ui)
    implementation(libs.google.calendar)
    implementation(libs.google.auth)
    implementation(libs.google.api.client)
    implementation(libs.bucket4j)

    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.h2)
    runtimeOnly(libs.postgresql)

    annotationProcessor(libs.boot.configuration.processor)

    testImplementation(libs.boot.webmvc.test)
    testImplementation(libs.boot.security.test)
    testImplementation(libs.boot.data.jpa.test)
    testImplementation(libs.boot.flyway.test)
    testImplementation(libs.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.archunit)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-processing,-serial"))
    options.encoding = "UTF-8"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Testcontainers-backed tests are tagged; skip them unless Docker is available or explicitly requested.
    if (System.getenv("EST_RUN_CONTAINER_TESTS") == null) {
        systemProperty("junit.jupiter.tags.exclude", "containers")
        exclude("**/*ContainersIT.class")
    }
    testLogging { events("failed", "skipped"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
    maxHeapSize = "1g"
}

tasks.bootJar {
    layered { enabled = true }
}

spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat("1.27.0").aosp()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
