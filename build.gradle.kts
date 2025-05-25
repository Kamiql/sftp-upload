plugins {
    kotlin("jvm") version "2.0.0-RC1"
    `kotlin-dsl`
    `maven-publish`
    `java-gradle-plugin`
}

group = "dev.kamiql"
version = "1.0.0"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.hierynomus:sshj:0.38.0")
    compileOnly(gradleApi())
    implementation(kotlin("stdlib"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
    withJavadocJar()
}

gradlePlugin {
    plugins {
        create("sftpUpload") {
            id = "dev.kamiql.sftpupload"
            implementationClass = "dev.kamiql.SftpUploadPlugin"
        }
    }
}

publishing {
    repositories {
        maven {
            group = project.group as String
            authentication {
                credentials(PasswordCredentials::class) {
                    username = System.getenv("NEXUS_USERNAME")
                    password = System.getenv("NEXUS_PASSWORD")
                }
            }

            val branch = System.getenv("GITHUB_REF")?.replace("refs/heads/", "") ?: ""

            url = uri(when (branch) {
                "main", "master" -> "https://eldonexus.de/repository/maven-releases/"
                "dev" -> "https://eldonexus.de/repository/maven-dev/"
                else -> "https://eldonexus.de/repository/maven-snapshots/"
            })

            version = when (branch) {
                "main", "master" -> version
                "dev" -> version.toString().plus("-DEV")
                else -> version.toString().plus("-SNAPSHOT")
            }
            name = "EldoNexus"
        }
    }
}

tasks {
    processResources {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
}


tasks.jar {
    from(configurations.runtimeClasspath.get().filter {
        it.name.startsWith("sshj")
    }.map { zipTree(it) })
}