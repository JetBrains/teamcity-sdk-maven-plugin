/*
 * Copyright 2000-2020 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.teamcity.maven.sdk

import org.apache.commons.io.FileUtils
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.*
import java.util.jar.JarFile


public abstract class AbstractTeamCityMojo() : AbstractMojo() {
    /**
     * Location of the TeamCity distribution.
     */
    @Parameter( defaultValue = "servers/\${teamcity-version}", property = "teamcityDir", required = true )
    protected var teamcityDir: File? = null

    @Parameter( defaultValue = "\${teamcity-version}", property = "teamcityVersion", required = true)
    protected var teamcityVersion: String = ""

    @Parameter( defaultValue = "false", property = "downloadQuietly", required = true)
    protected var downloadQuietly: Boolean = false

    @Parameter( defaultValue = "http://download.jetbrains.com/teamcity", property = "teamcitySourceURL", required = true)
    protected var teamcitySourceURL: String = ""

    @Parameter( defaultValue = "\${project.artifactId}.zip")
    protected var pluginPackageName : String = ""

    @Parameter ( defaultValue = "true", property = "startAgent")
    protected var startAgent: Boolean = true

    @Parameter(defaultValue = "localhost:8111")
    protected var serverAddress = ""

    @Parameter(defaultValue = "")
    protected var username = ""

    @Parameter(defaultValue = "")
    protected var password = ""

    /**
     * Location of the TeamCity data directory.
     */
    @Parameter( defaultValue = ".datadir", property = "dataDirectory", required = true )
    protected var dataDirectory: String = ""

    /**
     * The maven project.
     */
    @Parameter( defaultValue = "\${project}", readonly = true)
    protected var project : MavenProject? = null

    override fun execute() {
        checkTeamCityDirectory(teamcityDir!!)
        doExecute()
    }

    abstract fun doExecute()

    protected fun checkTeamCityDirectory(dir: File) {
        when (evalTeamCityDirectory(dir)) {
            TCDirectoryState.GOOD -> log.info("TeamCity $teamcityVersion is located at $teamcityDir")
            TCDirectoryState.MISVERSION -> log.warn("TeamCity version at [${dir.absolutePath}] is [${getTCVersion(dir)}], but project uses [$teamcityVersion]")
            TCDirectoryState.BAD -> {
                log.info("TeamCity distribution not found at [${dir.absolutePath}]")
                                      downloadTeamCity(dir) }
        }
    }

    private fun downloadTeamCity(dir: File) {
        if (downloadQuietly || askToDownload(dir)) {
            val retriever = TeamCityRetriever(teamcitySourceURL, teamcityVersion,
                    { message, debug ->
                        if (debug) {
                            log.debug(message)
                        } else {
                            log.info(message)
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
        print("Download TeamCity $teamcityVersion to  ${dir.absolutePath}?: Y:")
        val s = readLine()
        return s?.length == 0 || s?.toLowerCase()?.first() == 'y'
    }

    protected fun looksLikeTeamCityDir(dir: File): Boolean = File(dir, "bin/runAll.sh").exists()

    private fun wrongTeamCityVersion(dir: File) = !getTCVersion(dir).equals(teamcityVersion)

    protected fun getTCVersion(teamcityDir: File): String {
        var commonAPIjar = File(teamcityDir, "webapps/ROOT/WEB-INF/lib/common-api.jar")

        if (!commonAPIjar.exists() || !commonAPIjar.isFile) {
            throw MojoExecutionException("Can not read TeamCity version. Can not access [${commonAPIjar.absolutePath}]."
            + "Check that [$teamcityDir] points to valid TeamCity installation")
        } else {
            var jarFile = JarFile(commonAPIjar)
            val zipEntry = jarFile.getEntry("serverVersion.properties.xml")
            if (zipEntry == null) {
                throw MojoExecutionException("Failed to read TeamCity's version from [${commonAPIjar.absolutePath}]. Please, verify your intallation.")
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
        val reader = ThreadedStringReader(process.inputStream!!) {
            log.info(it)
        }.start()
        val returnValue = process.waitFor()
        reader.stop()
        return returnValue
    }

    protected fun createRunCommand(vararg params: String): List<String> {
        val fileName = getStartScriptName()
        return if (isWindows())
            listOf("cmd", "/C", "bin\\$fileName") + params
        else
            listOf("/bin/bash", "bin/$fileName.sh") + params
    }

    private fun getStartScriptName(): String {
        return if (startAgent)
            "runAll"
        else
            "teamcity-server"
    }

    protected fun isWindows(): Boolean {
        return System.getProperty("os.name")!!.contains("Windows")
    }

    protected fun uploadPluginAgentZip(): String {
        val proj = project!!

        val packageFile = File(proj.build!!.directory!!, pluginPackageName)

        val effectiveDataDir = getDataDir().absolutePath

        if (packageFile.exists()) {
            val dataDirFile = File(effectiveDataDir)
            FileUtils.copyFile(packageFile, File(dataDirFile, "plugins/" + pluginPackageName))
        } else {
            log.warn("Target file [${packageFile.absolutePath}] does not exist. Nothing will be deployed. Did you forget 'package' goal?")
        }
        return effectiveDataDir
    }

    protected fun reloadPluginInRuntime(): Boolean {
        if (username.isEmpty()) {
            log.debug("No username provided, looking for maintenance token")
            val tokenFile = File(getDataDir(), "system${File.separator}pluginData${File.separator}superUser${File.separator}token.txt")
            if (!tokenFile.isFile) {
                log.warn("Neither username provider nor maintenance token file exists. Cannot send plugin reload request. Check that server has already started with '-Dteamcity.superUser.token.saveToFile=true' parameter or provide username and password.")
                return false
            } else {
                try {
                    password = tokenFile.readText().toLong().toString()
                    log.debug("Using $password maintenance token to authenticate")
                } catch (ex: NumberFormatException) {
                    log.warn("Malformed maintenance token: should contain a number")
                    return false
                }
            }
        }

        val authToken = "Basic " + String(Base64.getEncoder().encode("$username:$password".toByteArray(Charset.forName("UTF-8"))))

        val disableAction = getPluginReloadURL(false)
        log.debug("Sending " + disableAction.toString() + "...")
        val disableRequest = disableAction.openConnection()
        (disableRequest as HttpURLConnection).requestMethod = "POST"
        disableRequest.setRequestProperty ("Authorization", authToken)

        try {
            disableRequest.getInputStream().use {
                val result = it.buffered().bufferedReader(Charset.defaultCharset()).readLine()
                if (!result.contains("Plugin unloaded successfully")) {
                    if (result.contains("Plugin unloaded partially")) {
                        log.warn("Plugin unloaded partially - some parts could still be running. Server restart could be needed. Check all resources are released and threads are stopped on server shutdown. ")
                    } else {
                        log.warn(result)
                        return false
                    }
                } else {
                    log.info("Plugin successfully unloaded")
                }
            }
        } catch (ex: ConnectException) {
            log.warn("Cannot find running server on http://$serverAddress. Is server started?")
            return false
        } catch (ex: IOException) {
            when (disableRequest.responseCode) {
                401 -> log.warn("Cannot authenticate server on http://$serverAddress with " +
                        if (username.isEmpty())
                            "maintenance token $password. Check that server has already started with '-Dteamcity.superUser.token.saveToFile=true' parameter or provide username and password."
                        else
                            "provided credentials.")
            }
            log.warn("Cannot connect to the server on http://$serverAddress: ${disableRequest.responseCode}", ex)
            return false
        }

        uploadPluginAgentZip()

        val enableAction = getPluginReloadURL(true)
        log.debug("Sending " + enableAction.toString() + "...")
        val enableRequest = enableAction.openConnection()
        (enableRequest as HttpURLConnection).requestMethod = "POST"
        enableRequest.setRequestProperty ("Authorization", authToken)
        try {
            enableRequest.getInputStream().use {
                val result = it.buffered().bufferedReader(Charset.defaultCharset()).readLine()

                if (!result.contains("Plugin loaded successfully")) {
                    log.warn(result)
                    return false
                } else {
                    log.info("Plugin successfully loaded")
                }
            }
        } catch(ex: IOException) {
            log.warn("Cannot connect to the server on http://$serverAddress: ${disableRequest.responseCode}", ex)
            return false
        }

        return true
    }

    private fun getPluginReloadURL(action: Boolean) =
            URL("http://$serverAddress/httpAuth/admin/plugins.html?action=setEnabled&enabled=$action&pluginPath=%3CTeamCity%20Data%20Directory%3E/plugins/$pluginPackageName")

    private fun getDataDir(): File {
        return if (File(dataDirectory).isAbsolute) {
            File(dataDirectory)
        } else {
            File(teamcityDir, dataDirectory)
        }
    }

    protected fun isPluginReloadable(): Boolean {
        val pluginDescriptor = File(project!!.basedir, "teamcity-plugin.xml")
        if (!pluginDescriptor.exists()) {
            log.warn("Plugin descriptor wan't found in ${pluginDescriptor.absolutePath}")
            return false
        }

        return pluginDescriptor.readText(Charset.forName("UTF-8")).contains("allow-runtime-reload=\"true\"")
    }
}

public enum class TCDirectoryState {
    GOOD,
    MISVERSION,
    BAD
}

