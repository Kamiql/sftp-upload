package dev.kamiql

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.Internal
import java.io.File

open class SftpExtension {
    @Input
    var servers: Map<String, Server> = emptyMap()

    @Input
    var targetDir: String = ""

    @Input
    var buildType: BuildType = BuildType.NORMAL

    class Server {
        @Input
        lateinit var host: String

        @Input
        var port: Int = 22

        @Input
        lateinit var username: String

        @Input
        lateinit var password: String
    }
}

enum class BuildType { NORMAL, SHADOW }

abstract class SftpUploadTask : DefaultTask() {
    @get:Internal
    protected val projectObj: Project = project

    @Input
    var config: SftpExtension = SftpExtension()

    init {
        group = "sftp"
        description = "Uploads build artifacts via SFTP (multi-server support, parallel)"
    }

    @TaskAction
    fun upload() = runBlocking {
        logger.lifecycle("=============>")
        logger.lifecycle("SFTP Upload Plugin v${project.version}")
        logger.lifecycle("=============>")
        logger.lifecycle("Starting SFTP Upload (BuildType=${config.buildType})...")

        require(config.servers.isNotEmpty()) { "Es wurden keine Server in 'sftp.servers' konfiguriert." }
        require(config.targetDir.isNotBlank()) { "targetDir (remote path) muss in der Extension konfiguriert sein." }

        val jarFile = when (config.buildType) {
            BuildType.SHADOW -> getShadowJarFile()
            BuildType.NORMAL -> getNormalJarFile()
        }

        require(jarFile.exists()) { "Keine JAR-Datei gefunden, die hochgeladen werden kann." }

        val uploadJobs = config.servers.map { (name, entry) ->
            async {
                uploadToServer(name, entry, jarFile)
            }
        }

        uploadJobs.forEach { it.await() }

        logger.lifecycle("Alle Uploads abgeschlossen.")
    }

    private suspend fun uploadToServer(name: String, entry: SftpExtension.Server, jarFile: File) {
        withContext(Dispatchers.IO) {
            val ssh = SSHClient().apply {
                timeout = 30_000
                addHostKeyVerifier(PromiscuousVerifier())
                connect(entry.host, entry.port)
                authPassword(entry.username, entry.password)
            }

            ssh.use { client ->
                client.newSFTPClient().use { sftp ->
                    logger.lifecycle("[$name] Connected to ${entry.host}:${entry.port} as ${entry.username}")
                    createRemoteDirectory(sftp, config.targetDir)

                    val remotePath = "${config.targetDir}/${jarFile.name}"
                    logger.lifecycle("[$name] Uploading ${jarFile.name} to $remotePath...")

                    sftp.put(jarFile.absolutePath, remotePath)
                    logger.lifecycle("[$name] Successfully uploaded: ${jarFile.name}")
                }
            }
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
            project.tasks.register("uploadSFTP", SftpUploadTask::class.java) {
                config = extension
                when (extension.buildType) {
                    BuildType.SHADOW -> dependsOn("shadowJar")
                    BuildType.NORMAL -> dependsOn("assemble")
                }
            }
        }
    }
}