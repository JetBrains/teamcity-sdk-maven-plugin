package org.jetbrains.teamcity.maven.sdk

/**
 * Created by Nikita.Skvortsov
 * date: 24.03.2014.
 */


import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import java.io.File
import java.util.Scanner
import org.apache.maven.project.MavenProject
import com.google.common.io.Files
import java.util.Properties
import java.util.jar.JarFile
import java.nio.channels.ReadableByteChannel
import java.net.URL
import java.nio.channels.Channels
import java.io.FileOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.FileInputStream
import java.util.zip.GZIPInputStream
import java.io.BufferedInputStream
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.io.input.CountingInputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import org.apache.commons.io.FileUtils


public abstract class AbstractTeamCityMojo() : AbstractMojo() {
    /**
     * Location of the TeamCity distribution.
     */
    Parameter( defaultValue = "servers/\${teamcity-version}", property = "teamcityDir", required = true )
    protected var teamcityDir: File? = null

    Parameter( defaultValue = "\${teamcity-version}", property = "teamcityVersion", required = true)
    protected var teamcityVersion: String = ""

    Parameter( defaultValue = "false", property = "downloadQuietly", required = true)
    protected var downloadQuietly: Boolean = false

    Parameter( defaultValue = "http://download.jetbrains.com/teamcity", property = "downloadQuietly", required = true)
    protected var teamcitySourceURL: String = ""
    /**
     * The maven project.
     */
    Parameter( defaultValue = "\${project}", readonly = true)
    protected var project : MavenProject? = null

    override fun execute() {
        checkTeamCityDirectory()
        doExecute()
    }

    abstract fun doExecute()

    protected fun checkTeamCityDirectory() {
        val dir = teamcityDir!!
        if (!dir.exists() || !looksLikeTeamCityDir(dir)) {
            getLog() info "TeamCity distribution not found at [${dir.getAbsolutePath()}]"
            if (downloadQuietly || askToDownload()) {
                teamcityDir = downloadTeamCity()
            } else {
                throw MojoExecutionException("TeamCity distribution not found.")
            }
        } else if (wrongTeamCityVersion(dir)) {
            getLog() warn "TeamCity verison at [${dir.getAbsolutePath()}] is [${getTCVersion(dir)}], but project uses [$teamcityVersion]"
        } else {
            getLog() info "TeamCity $teamcityVersion is located at $teamcityDir"
        }
    }

    private fun askToDownload(): Boolean {
        print("Download TeamCity $teamcityVersion to  ./servers/$teamcityVersion?: Y:")
        val s = readLine()
        return s?.length == 0 || s?.toLowerCase()?.first() == 'y'
    }

    private fun downloadTeamCity(targetDir: File = File(project!!.getBasedir(), "servers/$teamcityVersion")): File {
        val sourceURL = "$teamcitySourceURL/TeamCity-$teamcityVersion.tar.gz"

        getLog() info "Downloading and unpacking TeamCity from $sourceURL to ${targetDir.getAbsolutePath()}"

        val source = URL(sourceURL)
        val sourceStream = source.openStream()
        val downloadCounter = CountingInputStream(sourceStream)

        val sourceChannel : ReadableByteChannel = Channels.newChannel(downloadCounter)!!

        val file = File.createTempFile("teamcityDistro", teamcityVersion)
        val fos = FileOutputStream(file);
        try {

            fun createCounter(flag: AtomicBoolean, counter: CountingInputStream): Thread {
                return Thread({
                    while(flag.get()) {
                        Thread.sleep(1000)
                        print("\r" + FileUtils.byteCountToDisplaySize(counter.getByteCount()))
                    }
                    println()
                })
            }

            val counterFlag : AtomicBoolean = AtomicBoolean(true)
            val counter = createCounter(counterFlag, downloadCounter)
            try {
                counter.start()
                getLog() info "Transferring to temp file ${file.getAbsolutePath()}"
                fos.getChannel().transferFrom(sourceChannel, 0, java.lang.Long.MAX_VALUE);
            } finally {
                counterFlag.set(false)
                counter.join(1000)
                fos.close()
                sourceChannel.close()
            }

            getLog() info "Unpacking"
            targetDir.mkdirs()
            val unpackingCounter = CountingInputStream(FileInputStream(file))
            val unpackingCounterThread = createCounter(counterFlag, unpackingCounter)
            counterFlag.set(true)

            val tarInput = TarArchiveInputStream(GZIPInputStream(BufferedInputStream(unpackingCounter)))
            val tarChannel = Channels.newChannel(tarInput)!!
            try {
                unpackingCounterThread.start()
                tarInput.forEntry {
                    val name: String
                    val entryName = it.getName()
                    if (entryName.startsWith("TeamCity")) {
                        name = entryName.substring("TeamCity".length)
                    } else {
                        name = entryName
                    }
                    val destPath = File(targetDir, name)
                    if (it.isDirectory()) {
                        getLog() debug "Creating dir ${destPath.getAbsolutePath()}"
                        destPath.mkdirs()
                    } else {
                        getLog() debug "Creating dir ${destPath.getParentFile()?.getAbsolutePath()}"
                        destPath.getParentFile()?.mkdirs()
                        getLog() debug "Creating file ${destPath.getAbsolutePath()}"
                        destPath.createNewFile()
                        val destOS = FileOutputStream(destPath)
                        try {
                            destOS.getChannel().transferFrom(tarChannel, 0, it.getSize())
                        } finally {
                            destOS.close()
                        }
                    }
                }
            } finally {
                counterFlag.set(false)
                unpackingCounterThread.join(1000)
                tarChannel.close()
            }
        } finally {
            file.delete()
        }
        return targetDir
    }

    protected fun ArchiveInputStream.forEntry(f : (ArchiveEntry) -> Unit) {
        var entry = getNextEntry()
        while (entry != null) {
            f(entry as ArchiveEntry)
            entry = getNextEntry()
        }
    }

    protected fun looksLikeTeamCityDir(dir: File): Boolean = File(dir, "bin/runAll.sh").exists()

    private fun wrongTeamCityVersion(dir: File) = !getTCVersion(dir).equals(teamcityVersion)

    protected fun getTCVersion(teamcityDir: File): String {
        var commonAPIjar = File(teamcityDir, "webapps/ROOT/WEB-INF/lib/common-api.jar")


        if (!commonAPIjar.exists() || !commonAPIjar.isFile()) {
            throw MojoExecutionException("Can not read TeamCity version. Can not access [${commonAPIjar.getAbsolutePath()}]."
            + "Check that [$teamcityDir] points to valid TeamCity installation")
        } else {
            var jarFile = JarFile(commonAPIjar)
            val zipEntry = jarFile.getEntry("serverVersion.properties.xml")
            if (zipEntry == null) {
                throw MojoExecutionException("Failed to read TeamCity's version from [${commonAPIjar.getAbsolutePath()}]. Please, verify your intallation.")
            } else {
                val versionPropertiesStream = jarFile.getInputStream(zipEntry)
                try {
                    val props = Properties()
                    props.loadFromXML(versionPropertiesStream!!)
                    return props.get("Display_Version") as String
                } finally {
                    versionPropertiesStream?.close()
                }
            }
        }
    }

    protected fun readOutput(process: Process): Int {
        val s = Scanner(process.getInputStream()!!)
        while (s.hasNextLine()) {
            getLog() info s.nextLine()
        }
        s.close()
        return process.waitFor()
    }

    protected fun createCommand(vararg params: String): List<String> {
        return if (isWindows())
            listOf("cmd", "/C", "bin\\runAll") + params
        else
            listOf("/bin/bash", "bin/runAll") + params
    }

    protected fun isWindows(): Boolean {
        return System.getProperty("os.name")!!.contains("Windows")
    }
}


Mojo(name = "init", aggregator = true)
public class InitTeamCityMojo() : AbstractTeamCityMojo() {
    override fun doExecute() {
        getLog() info "Init finished"
    }
}

Mojo(name = "start", aggregator = true)
public class RunTeamCityMojo() : AbstractTeamCityMojo() {
    /**
     * Location of the TeamCity data directory.
     */
    Parameter( defaultValue = ".datadir", property = "teamcityDataDir", required = true )
    private var dataDirectory: String = ""

    Parameter( defaultValue = "-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=10111", property = "serverDebugStr", required = true)
    private var serverDebugStr: String = ""

    Parameter( defaultValue = "-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=10112", property = "agentDebugStr", required = true)
    private var agentDebugStr: String = ""

    Parameter( defaultValue = "\${project.artifactId}.zip")
    private var pluginPackageName : String = ""


    override fun doExecute() {
        val proj = project!!

        val packageFile = File(proj.getBuild()!!.getDirectory()!!, pluginPackageName)

        val effectiveDataDir =
                (if (File(dataDirectory).isAbsolute()) {
                    File(dataDirectory)
                } else {
                    File(teamcityDir, dataDirectory)
                }).getAbsolutePath()

        if (packageFile.exists()) {
            Files.copy(packageFile, File(File(effectiveDataDir), "plugins/" + pluginPackageName))
        } else {
            getLog() warn "Target file [${packageFile.getAbsolutePath()}] does not exist. Nothing will be deployed. Did you forget 'package' goal?"
        }

        getLog() info "Starting TC in [${teamcityDir?.getAbsolutePath()}]"
        getLog() info "TC data directory is [${effectiveDataDir}]"

        val procBuilder = ProcessBuilder()
                .directory(teamcityDir)
                .redirectErrorStream(true)
                .command(createCommand("start"))

        procBuilder.environment()?.put("TEAMCITY_DATA_PATH", effectiveDataDir)
        procBuilder.environment()?.put("TEAMCITY_SERVER_OPTS", serverDebugStr)
        procBuilder.environment()?.put("TEAMCITY_AGENT_OPTS", agentDebugStr)


        // runAll start never returns EOF, so just ignore the output...
        // val resultCode = readOutput(procBuilder.start())
        procBuilder.start().waitFor()
    }
}

Mojo(name = "stop", aggregator = true)
public class StopTeamCityMojo() : AbstractTeamCityMojo() {
    override fun doExecute() {
        getLog() info "Stopping TC in [${teamcityDir?.getAbsolutePath()}]"
        val procBuilder = ProcessBuilder()
                .directory(teamcityDir)
                .redirectErrorStream(true)
                .command(createCommand("stop"))
        val resultCode = readOutput(procBuilder.start())
    }
}