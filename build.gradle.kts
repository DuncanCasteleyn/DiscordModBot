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

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    implementation(group = "org.springframework.boot", name = "spring-boot-starter-data-jpa")
    implementation(group = "org.springframework.boot", name = "spring-boot-starter-validation")
    implementation(group = "org.springframework.boot", name = "spring-boot-starter-data-redis")
    implementation(group = "org.flywaydb", name = "flyway-core")
    implementation(group = "org.flywaydb", name = "flyway-mysql")
    implementation(group = "com.fasterxml.jackson.module", name = "jackson-module-kotlin")
    implementation(group = "net.dv8tion", name = "JDA", version = jdaVersion) {
        exclude(group = "club.minnced", module = "opus-java")
    }
    implementation(group = "org.apache.commons", name = "commons-collections4", version = commonsCollections4Version)
    implementation(group = "org.json", name = "json", version = orgJSONVersion)

    runtimeOnly(group = "com.h2database", name = "h2")
    runtimeOnly(group = "org.mariadb.jdbc", name = "mariadb-java-client")

    testImplementation(group = "org.springframework.boot", name = "spring-boot-starter-test")
    testImplementation(group = "org.mockito.kotlin", name = "mockito-kotlin", version = mockitoKotlinVersion)

    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
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
        useJUnitPlatform()
    }
    withType<KotlinCompile> {
        compilerOptions {
            freeCompilerArgs.add("-Xjsr305=strict")
            freeCompilerArgs.add("-progressive")

            jvmTarget = JvmTarget.JVM_21
        }
    }
    withType<Wrapper> {
        gradleVersion = "8.10.2"
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
project.version = "2.1.1-SNAPSHOT" // x-release-please-version
