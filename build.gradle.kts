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

buildscript {
    val kotlinVersion = "1.3.20"
    val springBootVersion = "2.1.3.RELEASE"

    repositories {
        mavenCentral()
    }
    dependencies {
        classpath(group = "org.springframework.boot", name = "spring-boot-gradle-plugin", version = springBootVersion)
        classpath(group = "org.jetbrains.kotlin", name = "kotlin-gradle-plugin", version = kotlinVersion)
        classpath(group = "org.jetbrains.kotlin", name = "kotlin-allopen", version = kotlinVersion)
    }
}

apply(plugin = "kotlin-spring")
apply(plugin = "org.springframework.boot")
apply(plugin = "io.spring.dependency-management")

plugins {
    java
    idea
    kotlin("jvm").version("1.3.20")
}


dependencies {
    implementation(group = "org.springframework.boot", name = "spring-boot-starter-security")
    implementation(group = "org.springframework.boot", name = "spring-boot-starter-web")
    implementation(group = "org.springframework.boot", name = "spring-boot-starter-data-jpa")
    runtime(group = "com.h2database", name = "h2")
    runtime(group = "org.mariadb.jdbc", name = "mariadb-java-client", version = "2.2.3")
    implementation(group = "com.fasterxml.jackson.module", name = "jackson-module-kotlin")
    implementation(group = "org.jetbrains.kotlin", name = "kotlin-stdlib-jdk8")
    implementation(group = "org.jetbrains.kotlin", name = "kotlin-reflect")
    testImplementation(group = "org.springframework.boot", name = "spring-boot-starter-test")
    testImplementation(group = "org.springframework.security", name = "spring-security-test")
    implementation(group = "net.dv8tion", name = "JDA", version = "3.8.3_460") {
        exclude(group = "club.minnced", module = "opus-java")
    }
    implementation(group = "org.apache.commons", name = "commons-collections4", version = "4.2")
    implementation(group = "org.json", name = "json", version = "20180813")
    implementation(group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version = "1.1.0")
    implementation(group = "org.jetbrains.kotlin", name = "kotlin-script-runtime")
    implementation(group = "org.jetbrains.kotlin", name = "kotlin-compiler-embeddable")
    implementation(group = "org.jetbrains.kotlin", name = "kotlin-script-util")
}

repositories {
    jcenter()
    mavenCentral()
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict", "-progressive")
            jvmTarget = "1.8"
        }
    }
    withType<Wrapper> {
        gradleVersion = "5.0"
    }
    withType<JavaCompile> {
        sourceCompatibility = "1.8"
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:deprecation")
    }
    withType<BootJar> {
        requiresUnpack("**/*.jar")
        launchScript()
    }
}

project.group = "be.duncanc"
project.version = "1.11.10"
