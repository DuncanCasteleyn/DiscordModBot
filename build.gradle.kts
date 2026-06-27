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

val jdaVersion = project.property("jdaVersion") as String
val commonsCollections4Version = project.property("commonsCollections4Version") as String
val orgJSONVersion = project.property("orgJSONVersion") as String
val mockitoKotlinVersion = project.property("mockitoKotlinVersion") as String
val jetbrainsAnnotationsVersion = project.property("jetbrainsAnnotationsVersion") as String
val springModulithVersion = project.property("springModulithVersion") as String

val mockitoAgent = configurations.create("mockitoAgent")
dependencies {
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation(kotlin("test"))

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.security:spring-security-crypto")
    implementation("org.flywaydb:flyway-mysql")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("net.dv8tion:JDA:$jdaVersion") {
        exclude(group = "club.minnced", module = "opus-java")
    }
    implementation("org.apache.commons:commons-collections4:$commonsCollections4Version")
    implementation("org.json:json:$orgJSONVersion")

    implementation("org.springframework.modulith:spring-modulith-starter-core")

    compileOnly("org.jetbrains:annotations:$jetbrainsAnnotationsVersion")

    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.mariadb.jdbc:mariadb-java-client")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-redis-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-restclient-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:$mockitoKotlinVersion")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

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
        languageVersion.set(JavaLanguageVersion.of(JavaVersion.VERSION_25.majorVersion))
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
            freeCompilerArgs.add("-progressive")

            jvmTarget = JvmTarget.JVM_25
        }
    }
    withType<Wrapper> {
        gradleVersion = "9.6.1"
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
project.version = "2.13.1-SNAPSHOT" // x-release-please-version
