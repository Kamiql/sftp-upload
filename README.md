# sftpupload Gradle Plugin

**Plugin ID:** `dev.kamiql.sftpupload`

A simple Gradle plugin to upload build artifacts (JARs) via SFTP, with optional shadow JAR support.

---

## 📦 Repository

| Repository                               | URL                                                                                                              |
|-------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| Maven Releases (main/master)              | `https://eldonexus.de/repository/maven-releases/`                                                               |
| Maven Dev (`dev` branch)                  | `https://eldonexus.de/repository/maven-dev/`                                                                    |
| Maven Snapshots (other branches)          | `https://eldonexus.de/repository/maven-snapshots/`                                                              |

Search components on Nexus: [EldoNexus Search](https://eldonexus.de/#browse/search=:NX.coreui.model.Component-22)

---

## ⚙️ Dependency

Add the plugin to your `settings.gradle.kts` or `settings.gradle`:

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://eldonexus.de/repository/maven-releases/")
        // or dev / snapshots depending on branch
    }
}
````

In your module's `build.gradle.kts`:

```kotlin
plugins {
    id("dev.kamiql.sftpupload") version "version"
}
```

Or via the `plugins` DSL in Groovy:

```groovy
dependencies {
    classpath "dev.kamiql:sftpupload:version"
}
apply plugin: "dev.kamiql.sftpupload"
```

---

## 🚀 Usage

Configure the `sftp` extension in your `build.gradle.kts`:

```kotlin
sftp {
    host = "example.com"
    port = 22                   // optional, default = 22
    username = "user"
    password = "secret"
    targetDir = "/remote/path"
    buildType = BuildType.NORMAL // or BuildType.SHADOW
}
```

Invoke the upload task:

```bash
# Uploads the latest JAR (assemble or shadowJar)
./gradlew uploadSFTP
```

---

## ⚙️ GitHub Actions

If you look forward to use github, to publish your project, just define a workflow

Example workflow (`.github/workflows/ci.yml`):

```yaml
name: CI

on:
  push:
    branches: [ main, dev ]

jobs:
  build-and-upload:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: 17
          distribution: temurin

      - name: Build
        run: ./gradlew assemble shadowJar

      - name: SFTP Upload
        env:
          SFTP_HOST: ${{ secrets.SFTP_HOST }}
          SFTP_USER: ${{ secrets.SFTP_USER }}
          SFTP_PASSWORD: ${{ secrets.SFTP_PASSWORD }}
          GITHUB_REF: ${{ github.ref }}
        run: |
          ./gradlew uploadSFTP \
            -Psftp.host=$SFTP_HOST \
            -Psftp.username=$SFTP_USER \
            -Psftp.password=$SFTP_PASSWORD \
            -Psftp.targetDir=/deploy/path
```

---

## 🎓 Extension Properties

| Property    | Type        | Required | Default  | Description                                         |
| ----------- | ----------- | -------- | -------- | --------------------------------------------------- |
| `host`      | `String`    | ✅        | *none*   | SFTP server hostname or IP                          |
| `port`      | `Int`       | ❌        | `22`     | SFTP server port                                    |
| `username`  | `String`    | ✅        | *none*   | Username for authentication                         |
| `password`  | `String`    | ✅        | *none*   | Password for authentication                         |
| `targetDir` | `String`    | ✅        | *none*   | Remote directory to upload the artifact to          |
| `buildType` | `BuildType` | ❌        | `NORMAL` | `NORMAL` uses `assemble`, `SHADOW` uses `shadowJar` |

---

## 🛠️ License

This project is licensed under the [MIT License](./LICENSE).
