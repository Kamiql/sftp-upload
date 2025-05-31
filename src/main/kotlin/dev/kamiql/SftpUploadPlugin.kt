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
    /**
     * Beispiel-Aufbau in build.gradle.kts:
     *
     * sftp {
     *   servers = mapOf(
     *     "staging" to Entry().apply {
     *       host = "staging.example.com"
     *       port = 22
     *       username = "user_stage"
     *       password = "pass_stage"
     *     },
     *     "production" to Entry().apply {
     *       host = "prod.example.com"
     *       port = 2222
     *       username = "user_prod"
     *       password = "pass_prod"
     *     }
     *   )
     *   targetDir = "/plugins"
     *   buildType = dev.kamiql.BuildType.SHADOW
     * }
     */
    @Input
    var servers: Map<String, Entry> = emptyMap()

    @Input
    var targetDir: String = ""

    @Input
    var buildType: BuildType = BuildType.NORMAL

    class Entry {
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
    @Optional
    var config: SftpExtension = SftpExtension()

    @Input
    @Optional
    var serverKey: String? = null

    init {
        group = "sftp"
        description = "Uploads build artifacts via SFTP (multi-server support)"
    }

    @TaskAction
    fun upload() {
        logger.lifecycle("=============>")
        logger.lifecycle("SFTP Upload Plugin v${project.version}")
        logger.lifecycle("=============>")
        logger.lifecycle("Starting SFTP Upload (BuildType=${config.buildType})...")

        require(config.servers.isNotEmpty()) { "Es wurden keine Server in 'sftp.servers' konfiguriert." }

        val key = serverKey
            ?: projectObj.findProperty("ftp.server")?.toString()
            ?: throw IllegalArgumentException("Kein 'ftp.server'-Key angegeben. Bitte mit '-Pftp.server=<deinKey>' aufrufen.")

        val entry = config.servers[key]
            ?: throw IllegalArgumentException("Kein Server-Entry für den Key '$key' gefunden. Verfügbare Keys: ${config.servers.keys}")

        require(config.targetDir.isNotBlank()) { "targetDir (remote path) muss in der Extension konfiguriert sein." }

        val jarFile = when (config.buildType) {
            BuildType.SHADOW -> getShadowJarFile()
            BuildType.NORMAL -> getNormalJarFile()
        }
        require(jarFile.exists()) { "Keine JAR-Datei gefunden, die hochgeladen werden kann." }

        createClient(entry).use { sftp ->
            logger.lifecycle("Connected to ${entry.host}:${entry.port} as ${entry.username}")
            createRemoteDirectory(sftp, config.targetDir)

            val remotePath = "${config.targetDir}/${jarFile.name}"
            logger.lifecycle("Uploading ${jarFile.name} to $remotePath...")

            sftp.put(jarFile.absolutePath, remotePath)
            logger.lifecycle("Successfully uploaded: ${jarFile.name}")
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

    private fun createClient(entry: SftpExtension.Entry): SFTPClient {
        val ssh = SSHClient().apply {
            addHostKeyVerifier(PromiscuousVerifier())
            connect(entry.host, entry.port)
            authPassword(entry.username, entry.password)
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