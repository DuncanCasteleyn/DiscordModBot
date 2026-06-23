pluginManagement {
    val kotlinVersion = providers.gradleProperty("kotlinVersion").get()
    val springBootVersion = providers.gradleProperty("springBootVersion").get()
    val springBootDependencyManagementVersion = providers.gradleProperty("springBootDependencyManagementVersion").get()

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    plugins {
        id("org.springframework.boot") version springBootVersion
        id("io.spring.dependency-management") version springBootDependencyManagementVersion
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.spring") version kotlinVersion
        kotlin("plugin.jpa") version kotlinVersion
        kotlin("kapt") version kotlinVersion
    }
}

rootProject.name = "DiscordModBot"
