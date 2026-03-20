import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")

    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    kotlin("kapt")
}

val jdaVersion: String by project
val commonsCollections4Version: String by project
val orgJSONVersion: String by project
val mockitoKotlinVersion: String by project
val jetbrainsAnnotationsVersion: String by project
val springModulithVersion: String by project

val mockitoAgent = configurations.create("mockitoAgent")
dependencies {
    annotationProcessor(group = "org.springframework.boot", name = "spring-boot-configuration-processor")

    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation(kotlin("test"))

    implementation(group = "org.springframework.boot", name = "spring-boot-starter-data-jpa")
    implementation(group = "org.springframework.boot", name = "spring-boot-starter-validation")
    implementation(group = "org.springframework.boot", name = "spring-boot-starter-data-redis")
    implementation(group = "org.springframework.boot", name = "spring-boot-starter-flyway")
    implementation(group = "org.flywaydb", name = "flyway-mysql")
    implementation(group = "com.fasterxml.jackson.module", name = "jackson-module-kotlin")
    implementation(group = "net.dv8tion", name = "JDA", version = jdaVersion) {
        exclude(group = "club.minnced", module = "opus-java")
    }
    implementation(group = "org.apache.commons", name = "commons-collections4", version = commonsCollections4Version)
    implementation(group = "org.json", name = "json", version = orgJSONVersion)

    implementation(group = "org.springframework.modulith", name = "spring-modulith-starter-core")

    compileOnly(group = "org.jetbrains", name = "annotations", version = jetbrainsAnnotationsVersion)

    runtimeOnly(group = "com.h2database", name = "h2")
    runtimeOnly(group = "org.mariadb.jdbc", name = "mariadb-java-client")

    testImplementation(group = "org.springframework.boot", name = "spring-boot-starter-test")
    testImplementation(group = "org.springframework.boot", name = "spring-boot-starter-data-jpa-test")
    testImplementation(group = "org.springframework.boot", name = "spring-boot-starter-data-redis-test")
    testImplementation(group = "org.springframework.boot", name = "spring-boot-starter-flyway-test")
    testImplementation(group = "org.springframework.boot", name = "spring-boot-starter-data-redis-test")
    testImplementation(group = "org.mockito.kotlin", name = "mockito-kotlin", version = mockitoKotlinVersion)
    testImplementation(group = "org.springframework.modulith", name = "spring-modulith-starter-test")
    testImplementation(group = "org.jetbrains.kotlin", name = "kotlin-test-junit5")

    testRuntimeOnly(group = "org.junit.platform", name = "junit-platform-launcher")

    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    mockitoAgent("org.mockito:mockito-core") { isTransitive = false }
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:${springModulithVersion}")
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(JavaVersion.VERSION_21.majorVersion))
    }
}

tasks {
    withType<Test> {
        jvmArgs.add("-javaagent:${mockitoAgent.asPath}")

        useJUnitPlatform()
    }
    withType<KotlinCompile> {
        compilerOptions {
            freeCompilerArgs.add("-Xjsr305=strict")
            freeCompilerArgs.add("-Xannotation-default-target=param-property")
            freeCompilerArgs.add("-progressive")

            jvmTarget = JvmTarget.JVM_21
        }
    }
    withType<Wrapper> {
        gradleVersion = "9.4.1"
    }
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:deprecation")
    }
    named<Jar>("jar") {
        enabled = false
    }
    withType<BootJar> {
        archiveFileName.set("DiscordModBot.jar")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

project.group = "be.duncanc"
project.version = "2.2.3" // x-release-please-version
