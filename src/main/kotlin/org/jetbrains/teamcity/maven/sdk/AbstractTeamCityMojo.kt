package org.jetbrains.teamcity.maven.sdk

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugins.annotations.Parameter
import java.io.File
import org.apache.maven.project.MavenProject
import org.apache.maven.plugin.MojoExecutionException
import java.util.Properties
import java.util.jar.JarFile


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
            val retriever = TeamCityRetriever(teamcitySourceURL, teamcityVersion,
                    { (message, debug) ->
                        if (debug) {
                            getLog() debug message
                        } else {
                            getLog() info message
                        }
                    })

            retriever.downloadAndUnpackTeamCity(dir)
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

    protected fun createRunCommand(vararg params: String): List<String> {
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

