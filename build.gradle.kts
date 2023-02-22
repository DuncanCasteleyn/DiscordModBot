/*
 * Copyright 2018 Duncan Casteleyn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    val kotlinVersion = "1.8.10"

    id("org.springframework.boot") version "2.7.8"
    id("io.spring.dependency-management") version "1.1.0"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.spring") version kotlinVersion
    kotlin("plugin.jpa") version kotlinVersion
    kotlin("kapt") version kotlinVersion
}


dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    implementation(group = "org.springframework.boot", name = "spring-boot-starter-data-jpa")
    implementation(group = "org.springframework.boot", name = "spring-boot-starter-validation")
    implementation(group = "org.springframework.boot", name = "spring-boot-starter-data-redis")
    implementation(group = "org.flywaydb", name = "flyway-core")
    implementation(group = "org.flywaydb", name = "flyway-mysql")
    implementation(group = "com.fasterxml.jackson.module", name = "jackson-module-kotlin")
    implementation(group = "net.dv8tion", name = "JDA", version = "4.4.0_352") {
        exclude(group = "club.minnced", module = "opus-java")
    }
    implementation(group = "org.apache.commons", name = "commons-collections4", version = "4.4")
    implementation(group = "org.json", name = "json", version = "20220924")

    runtimeOnly(group = "com.h2database", name = "h2")
    runtimeOnly(group = "org.mariadb.jdbc", name = "mariadb-java-client")

    testImplementation(group = "org.springframework.boot", name = "spring-boot-starter-test")
    testImplementation(group = "org.mockito.kotlin", name = "mockito-kotlin", version = "4.1.0")

    annotationProcessor(group = "org.springframework.boot", name = "spring-boot-configuration-processor")
    kapt(group = "org.springframework.boot", name = "spring-boot-configuration-processor")
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
    maven {
        name = "m2-dv8tion"
        setUrl("https://m2.dv8tion.net/releases")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks {
    withType<Test> {
        useJUnitPlatform()
    }
    withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict", "-progressive")
            jvmTarget = "17"
        }
    }
    withType<Wrapper> {
        gradleVersion = "8.0.1"
    }
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:deprecation")
    }
    named<Jar>("jar") {
        enabled = false
    }
    withType<BootJar> {
        requiresUnpack("**/*.jar")
        launchScript()
        archiveFileName.set("DiscordModBot.jar")
    }
}

project.group = "be.duncanc"
project.version = file("version.txt").readText().trim()
