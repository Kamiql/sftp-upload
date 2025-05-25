plugins {
    id("de.chojo.publishdata") version "1.4.0"
    kotlin("jvm") version "2.0.0-RC1"
    `kotlin-dsl`
    `maven-publish`
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.hierynomus:sshj:0.38.0")
    compileOnly(gradleApi())
}

group = "dev.kamiql"
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
        create("sftpUpload") {
            id = "dev.kamiql.sftpupload"
            implementationClass = "dev.kamiql.SftpUploadPlugin"
        }
    }
}

tasks.jar {
    from(configurations.runtimeClasspath.get().filter {
        it.name.startsWith("sshj")
    }.map { zipTree(it) })
}