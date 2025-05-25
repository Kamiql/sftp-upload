plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "1.9.22"
    `maven-publish`
    id("de.chojo.publishdata") version "1.4.0"
}

repositories {
    maven("https://eldonexus.de/repository/maven-public/")
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.hierynomus:sshj:0.38.0")
    compileOnly(gradleApi())
}

group = "dev.kamiql.gradle"
version = "1.0.0"

publishData {
    useEldoNexusRepos()
    publishComponent("java")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            publishData.configurePublication(this)
        }
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

tasks.jar {
    from(configurations.runtimeClasspath.get().filter {
        it.name.startsWith("sshj")
    }.map { zipTree(it) })
}