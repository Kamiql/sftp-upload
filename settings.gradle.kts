rootProject.name = "SFTPUpload"
include("plugin")

pluginManagement {
    repositories {
        maven("https://eldonexus.de/repository/maven-public/")
        gradlePluginPortal()
        mavenCentral()
    }
}
