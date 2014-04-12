package org.jetbrains.teamcity.maven.sdk

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Parameter
import java.io.File
import org.apache.maven.project.MavenProject
import org.apache.maven.plugin.MojoExecutionException
import java.net.URL
import org.apache.commons.io.input.CountingInputStream
import java.nio.channels.ReadableByteChannel
import java.nio.channels.Channels
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import org.apache.commons.io.FileUtils
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.util.zip.GZIPInputStream
import java.io.BufferedInputStream
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ArchiveEntry
import java.util.Properties
import java.util.Scanner
import java.util.jar.JarFile
import java.io.FileInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry


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
        checkTeamCityDirectory(teamcityDir!!)
        doExecute()
    }

    abstract fun doExecute()

    protected fun checkTeamCityDirectory(dir: File) {
        when (evalTeamCityDirectory(dir)) {
            TCDirectoryState.GOOD -> getLog() info "TeamCity $teamcityVersion is located at $teamcityDir"
            TCDirectoryState.MISVERSION -> getLog() warn "TeamCity verison at [${dir.getAbsolutePath()}] is [${getTCVersion(dir)}], but project uses [$teamcityVersion]"
            TCDirectoryState.BAD -> { getLog() info "TeamCity distribution not found at [${dir.getAbsolutePath()}]"
                                      downloadTeamCity(dir) }
        }
    }

    private fun downloadTeamCity(dir: File) {
        if (downloadQuietly || askToDownload(dir)) {
            teamcityDir = doDownloadTeamCity(dir)
        } else {
            throw MojoExecutionException("TeamCity distribution not found.")
        }
    }

    protected fun evalTeamCityDirectory(dir: File): TCDirectoryState {
        if (!dir.exists() || !looksLikeTeamCityDir(dir)) {
            return TCDirectoryState.BAD
        } else if (wrongTeamCityVersion(dir)) {
            return TCDirectoryState.MISVERSION
        } else {
            return TCDirectoryState.GOOD
        }
    }

    private fun askToDownload(dir: File): Boolean {
        print("Download TeamCity $teamcityVersion to  ${dir.getAbsolutePath()}?: Y:")
        val s = readLine()
        return s?.length == 0 || s?.toLowerCase()?.first() == 'y'
    }

    private fun doDownloadTeamCity(targetDir: File = File(project!!.getBasedir(), "servers/$teamcityVersion")): File {
        val sourceURL = "$teamcitySourceURL/TeamCity-$teamcityVersion.tar.gz"

        getLog() info "Downloading and unpacking TeamCity from $sourceURL to ${targetDir.getAbsolutePath()}"

        val source = URL(sourceURL)
        val sourceStream = source.openStream()
        val downloadCounter = CountingInputStream(sourceStream)

        val sourceChannel : ReadableByteChannel = Channels.newChannel(downloadCounter)!!

        val tempFile = File.createTempFile("teamcityDistro", teamcityVersion)
        val fos = FileOutputStream(tempFile);
        try {

            val counterFlag : AtomicBoolean = AtomicBoolean(true)
            val counter = createCounter(counterFlag, downloadCounter)
            try {
                counter.start()
                getLog() info "Transferring to temp file ${tempFile.getAbsolutePath()}"
                fos.getChannel().transferFrom(sourceChannel, 0, java.lang.Long.MAX_VALUE);
            } finally {
                counterFlag.set(false)
                counter.join(1000)
                fos.close()
                sourceChannel.close()
            }

            getLog() info "Unpacking"
            extractTeamCity(tempFile, targetDir)
        } finally {
            tempFile.delete()
        }
        return targetDir
    }

    protected fun extractTeamCity(archive: File, destination: File) {
        destination.mkdirs()
        val counterFlag = AtomicBoolean(true)
        val unpackingCounter = CountingInputStream(FileInputStream(archive))
        val unpackingCounterThread = createCounter(counterFlag, unpackingCounter)

        val tarInput = TarArchiveInputStream(GZIPInputStream(BufferedInputStream(unpackingCounter)))
        val tarChannel = Channels.newChannel(tarInput)!!
        try {
            unpackingCounterThread.start()
            tarInput.eachEntry {
                val name: String
                val entryName = it.getName()
                if (entryName.startsWith("TeamCity")) {
                    name = entryName.substring("TeamCity".length)
                } else {
                    name = entryName
                }
                val destPath = File(destination, name)
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
                        if (it.isExecutable()) {
                            destPath.setExecutable(true)
                        }
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
    }

    private fun TarArchiveEntry.isExecutable(): Boolean {
        val EXECUTABLE_BIT_INDEX = 6
        val executableBit = getMode().getBit(EXECUTABLE_BIT_INDEX)
        return executableBit == 1
    }

    protected fun TarArchiveInputStream.eachEntry(f : (TarArchiveEntry) -> Unit) {
        var entry = getNextEntry() as TarArchiveEntry?
        while (entry != null) {
            f(entry as TarArchiveEntry)
            entry = getNextEntry() as TarArchiveEntry?
        }
    }

    private fun createCounter(flag: AtomicBoolean, counter: CountingInputStream): Thread {
        return Thread({
            while(flag.get()) {
                Thread.sleep(1000)
                print("\r" + FileUtils.byteCountToDisplaySize(counter.getByteCount()))
            }
            println()
        })
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
                    return props["Display_Version"] as String
                } finally {
                    versionPropertiesStream?.close()
                }
            }
        }
    }

    protected fun readOutput(process: Process): Int {
        val reader = ThreadedStringReader(process.getInputStream()!!) {
            getLog() info it
        }.start()
        val returnValue = process.waitFor()
        reader.stop()
        return returnValue
    }

    protected fun createCommand(vararg params: String): List<String> {
        return if (isWindows())
            listOf("cmd", "/C", "bin\\runAll") + params
        else
            listOf("/bin/bash", "bin/runAll.sh") + params
    }

    protected fun isWindows(): Boolean {
        return System.getProperty("os.name")!!.contains("Windows")
    }
}

public enum class TCDirectoryState {
    GOOD
    MISVERSION
    BAD
}

fun Int.getBit(index: Int): Int {
    return this.ushr(index).and(1)
}
