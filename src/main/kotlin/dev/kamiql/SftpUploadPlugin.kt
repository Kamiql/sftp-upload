package dev.kamiql

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.Internal
import java.io.File

open class SftpExtension {
    @Input var host: String = ""
    @Input var port: Int = 22
    @Input var username: String = ""
    @Input var password: String = ""
    @Input var targetDir: String = ""
    @Input var buildType: BuildType = BuildType.NORMAL
}

enum class BuildType { NORMAL, SHADOW }

abstract class SftpUploadTask : DefaultTask() {
    @Input
    @Optional
    var config: SftpExtension = SftpExtension()

    @get:Internal
    protected val projectObj: Project = project

    init {
        group = "sftp"
        description = "Uploads build artifacts via SFTP"
    }

    @TaskAction
    fun upload() {
        logger.lifecycle("=============>")
        logger.lifecycle("🔌 SFTP Upload Plugin v${project.version}")
        logger.lifecycle("=============>")
        logger.lifecycle("\n🚀 Starting SFTP Upload (${config.buildType})...")

        require(config.host.isNotBlank())     { "❌ Host must be configured" }
        require(config.username.isNotBlank()) { "❌ Username must be configured" }
        require(config.targetDir.isNotBlank()) { "❌ Target directory must be configured" }

        val jarFile = when (config.buildType) {
            BuildType.SHADOW -> getShadowJarFile()
            BuildType.NORMAL -> getNormalJarFile()
        }
        require(jarFile.exists()) { "❌ No JAR file found to upload" }

        createClient().use { sftp ->
            logger.lifecycle("\n🔗 Connected to ${config.host}:${config.port} as ${config.username}")
            createRemoteDirectory(sftp, config.targetDir)

            val remotePath = "${config.targetDir}/${jarFile.name}"
            logger.lifecycle("\n⬆️ Uploading ${jarFile.name} to $remotePath...")

            sftp.put(jarFile.absolutePath, remotePath)
            logger.lifecycle("\n✅ Successfully uploaded: ${jarFile.name}")
        }
    }

    private fun getNormalJarFile(): File {
        @Suppress("DEPRECATION")
        val libsDir = File(projectObj.buildDir, "libs")
        return libsDir.listFiles { f -> f.extension == "jar" }
            ?.maxByOrNull { it.lastModified() }
            ?: throw IllegalStateException("No JAR files found in build/libs")
    }

    private fun getShadowJarFile(): File {
        return projectObj.tasks
            .named("shadowJar", Task::class.java)
            .get().outputs.files.singleFile
    }

    private fun createClient(): SFTPClient {
        val ssh = SSHClient().apply {
            addHostKeyVerifier(PromiscuousVerifier())
            connect(config.host, config.port)
            authPassword(config.username, config.password)
        }
        return ssh.newSFTPClient()
    }

    private fun createRemoteDirectory(client: SFTPClient, path: String) {
        var current = ""
        for (part in path.split("/").filter { it.isNotEmpty() }) {
            current += "/$part"
            try {
                client.stat(current)
            } catch (_: Exception) {
                logger.lifecycle("📁 Creating directory: $current")
                client.mkdir(current)
            }
        }
    }
}

@Suppress("unused")
class SftpUploadPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("sftp", SftpExtension::class.java)
        project.afterEvaluate {
            project.tasks.register("uploadSFTP", SftpUploadTask::class.java, {
                config = extension
                when (extension.buildType) {
                    BuildType.SHADOW -> dependsOn("shadowJar")
                    BuildType.NORMAL -> dependsOn("assemble")
                }
            })
        }
    }
}