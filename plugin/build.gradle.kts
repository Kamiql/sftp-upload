plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
    id("de.chojo.publishdata") version "1.4.0"
}

repositories {
    maven("https://eldonexus.de/repository/maven-public/")
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.github.mwiede:jsch:0.2.15")
}

group = "dev.kamiql.gradle"
version = "1.0.0"

publishData {
    useEldoNexusRepos()

    publishComponent("java")
}

publishing {
    publications.create<MavenPublication>("maven") {
        publishData.configurePublication(this)
    }

    repositories.maven {
        authentication {
            credentials(PasswordCredentials::class) {
                username = System.getenv("NEXUS_USERNAME")
                password = System.getenv("NEXUS_PASSWORD")
            }
        }

        name = "eldonexus"
        url = uri(publishData.getRepository())
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

gradlePlugin {
    plugins {
        create("sftpUploadPlugin") {
            id = "dev.kamiql.gradle.sftp-upload"
            implementationClass = "dev.kamiql.gradle.SftpUploadPlugin"
            displayName = "SFTP Upload Plugin"
            description = "Provides uploadSFTP and shadowUploadSFTP tasks via SFTP"
        }
    }
}