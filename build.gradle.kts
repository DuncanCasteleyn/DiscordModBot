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

import org.gradle.kotlin.dsl.extra
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    val kotlinVersion = "1.2.71"
    val springBootVersion = "2.0.6.RELEASE"

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
    eclipse
    kotlin("jvm").version("1.2.71")
}


dependencies {
    compile(group = "org.springframework.boot", name = "spring-boot-starter-security")
    compile(group = "org.springframework.boot", name = "spring-boot-starter-web")
    compile(group = "org.springframework.boot", name = "spring-boot-starter-data-jpa")
    runtime(group = "com.h2database", name = "h2")
    runtime(group = "org.mariadb.jdbc", name = "mariadb-java-client", version = "2.2.3")
    compile(group = "com.fasterxml.jackson.module", name = "jackson-module-kotlin")
    compile(group = "org.jetbrains.kotlin", name = "kotlin-stdlib-jdk8")
    compile(group = "org.jetbrains.kotlin", name = "kotlin-reflect")
    testCompile(group = "org.springframework.boot", name = "spring-boot-starter-test")
    testCompile(group = "org.springframework.security", name = "spring-security-test")
    compile(group = "net.dv8tion", name = "JDA", version = "3.8.1_439") {
        exclude(group = "club.minnced", module = "opus-java")
    }
    compile(group = "club.minnced", name = "opus-java", version = "1.0.4")
    compile(group = "org.apache.commons", name = "commons-lang3", version = "3.5")
    compile(group = "javax.xml.bind", name = "jaxb-api", version = "2.3.0")
}

repositories {
    jcenter()
    mavenCentral()
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = "1.8"
        }
    }
    withType<Wrapper> {
        version = "4.10.2"
    }
    withType<JavaCompile> {
        sourceCompatibility = "1.8"
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:deprecation")
    }
}

project.group = "be.duncanc"
project.version = "1.9.0"
