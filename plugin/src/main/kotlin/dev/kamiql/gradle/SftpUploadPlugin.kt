package dev.kamiql.gradle

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.tasks.Internal
import java.io.File
import java.util.Properties

open class SftpExtension {
    @Input
    var host: String = ""

    @Input
    var port: Int = 22

    @Input
    var username: String = ""

    @Input
    var password: String = ""

    @Input
    var targetDir: String = ""

    @Input
    var buildType: BuildType = BuildType.NORMAL
}

enum class BuildType {
    NORMAL,
    SHADOW
}

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
        require(config.host.isNotBlank()) { "Host must be configured" }
        require(config.username.isNotBlank()) { "Username must be configured" }
        require(config.targetDir.isNotBlank()) { "Target directory must be configured" }

        val jarFile = when (config.buildType) {
            BuildType.SHADOW -> getShadowJarFile()
            BuildType.NORMAL -> getNormalJarFile()
        }

        createSession().let { channel ->
            createRemoteDirectory(channel, config.targetDir)
            val remotePath = "${config.targetDir}/${jarFile.name}"
            channel.put(jarFile.absolutePath, remotePath)
            println("Successfully uploaded ${jarFile.name} to ${config.host}:$remotePath")
        }
    }

    private fun getNormalJarFile(): File {
        @Suppress("DEPRECATION")
        val libsDir = File(projectObj.buildDir, "libs")
        return libsDir.listFiles { it.extension == "jar" }
            ?.maxByOrNull { it.lastModified() }
            ?: throw IllegalStateException("No JAR file found in ${libsDir.absolutePath}")
    }

    private fun getShadowJarFile(): File {
        return projectObj.tasks.named("shadowJar", Task::class.java)
            .get().outputs.files.singleFile
    }

    private fun createSession(): ChannelSftp = JSch().run {
        val configProperties = Properties().apply {
            put("StrictHostKeyChecking", "no")
            put("PreferredAuthentications", "publickey,password")
        }

        getSession(config.username, config.host, config.port).apply {
            setPassword(config.password)
            setConfig(configProperties)
            connect()
        }.openChannel("sftp").apply { connect() } as ChannelSftp
    }

    private fun createRemoteDirectory(channel: ChannelSftp, path: String) {
        var currentPath = ""
        for (dir in path.split("/").filter { it.isNotEmpty() }) {
            currentPath += "/$dir"
            try {
                channel.stat(currentPath)
            } catch (_: Exception) {
                channel.mkdir(currentPath)
            }
        }
    }
}

@Suppress("unused")
class SftpUploadPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("sftp", SftpExtension::class.java)

        project.afterEvaluate {
            project.tasks.register("uploadSFTP", SftpUploadTask::class.java) { task ->
                task.config = extension
                when (extension.buildType) {
                    BuildType.SHADOW -> task.dependsOn("shadowJar")
                    BuildType.NORMAL -> task.dependsOn("assemble")
                }
            }
        }
    }
}