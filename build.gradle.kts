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
    val kotlinVersion = "1.4.10"

    id("org.springframework.boot") version "2.3.5.RELEASE"
    id("io.spring.dependency-management") version "1.0.9.RELEASE"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.spring") version kotlinVersion
    kotlin("plugin.jpa") version kotlinVersion
}


dependencies {
    implementation(group = "org.springframework.boot", name = "spring-boot-starter-data-jpa")
    implementation(group = "org.springframework.boot", name = "spring-boot-starter-validation")
    implementation(group = "org.springframework.boot", name = "spring-boot-starter-data-redis")
    implementation(group = "org.flywaydb", name = "flyway-core")
    implementation(group = "com.fasterxml.jackson.module", name = "jackson-module-kotlin")
    implementation(group = "org.jetbrains.kotlin", name = "kotlin-stdlib-jdk8")
    implementation(group = "org.jetbrains.kotlin", name = "kotlin-reflect")
    implementation(group = "net.dv8tion", name = "JDA", version = "4.2.0_215") {
        exclude(group = "club.minnced", module = "opus-java")
    }
    implementation(group = "org.apache.commons", name = "commons-collections4", version = "4.2")
    implementation(group = "org.json", name = "json", version = "20190722")

    runtimeOnly(group = "com.h2database", name = "h2")
    runtimeOnly(group = "org.mariadb.jdbc", name = "mariadb-java-client")

    testImplementation(group = "org.springframework.boot", name = "spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation(group = "com.nhaarman.mockitokotlin2", name = "mockito-kotlin", version = "2.2.0")

    annotationProcessor(group = "org.springframework.boot", name = "spring-boot-configuration-processor")
}

repositories {
    jcenter()
    mavenCentral()
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_14
    targetCompatibility = JavaVersion.VERSION_14
}
tasks {
    withType<Test> {
        useJUnitPlatform()
    }
    withType<KotlinCompile> {
        dependsOn(processResources)
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict", "-progressive")
            jvmTarget = "14"
        }
    }
    withType<Wrapper> {
        gradleVersion = "6.7"
    }
    withType<JavaCompile> {
        dependsOn(processResources)
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:deprecation")
    }
    withType<BootJar> {
        requiresUnpack("**/*.jar")
        launchScript()
        archiveFileName.set("DiscordModBot.jar")
    }
}

project.group = "be.duncanc"
project.version = "1.11.14"
