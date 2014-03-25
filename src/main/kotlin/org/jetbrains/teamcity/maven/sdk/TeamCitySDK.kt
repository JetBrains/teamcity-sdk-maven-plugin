package org.jetbrains.teamcity.maven.sdk

/**
 * Created by Nikita.Skvortsov
 * date: 24.03.2014.
 */


import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.ResolutionScope
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.Scanner
import org.apache.maven.project.MavenProject
import com.google.common.io.Files

/**
 * Goal which touches a timestamp file.
 *
 * @deprecated Don't use!
 */
Mojo( name = "touch", defaultPhase = LifecyclePhase.PROCESS_SOURCES )
public class MyMojo() : AbstractMojo() {
    /**
     * Location of the file.
     */
    Parameter( defaultValue = "\${project.build.directory}", property = "outputDir", required = true )
    private var outputDirectory: File? = null

    override fun execute() {
        val f = outputDirectory!!

        if (!f.exists()) {
            f.mkdirs()
        }

        val touch = File(f, "touch.txt")

        var w: FileWriter? = null
        try {
            w = FileWriter(touch)
            w?.write("touch.txt")
        } catch (e: IOException) {
            throw MojoExecutionException("Error creating file " + touch, e)
        } finally {
            try {
                w?.close()
            } catch (e: IOException) {
                // ignore
            }
        }
    }
}


public abstract class AbstractTeamCityMojo() : AbstractMojo() {
    /**
     * Location of the TeamCity distribution.
     */
    Parameter( defaultValue = "\${project.build.directory}/servers/\${teamcity-version}", property = "teamcityDir", required = true )
    protected var teamcityDir: File? = null


    protected fun readOutput(process: Process): Int {
        val s = Scanner(process.getInputStream()!!)
        while (s.hasNextLine()) {
            getLog()?.info(s.nextLine())
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

    /**
     * The maven project.
     */
    Parameter( defaultValue = "\${project}", readonly = true)
    private var project : MavenProject? = null

    Parameter( defaultValue = "\${project.artifactId}.zip")
    private var pluginPackageName : String = ""


    override fun execute() {

        val proj = project!!

        val packageFile = File(proj.getBuild()!!.getDirectory()!!, pluginPackageName)

        val effectiveDataDir =
                (if (File(dataDirectory).isAbsolute()) {
                    File(dataDirectory)
                } else {
                    File(teamcityDir, dataDirectory)
                }).getAbsolutePath()


        Files.copy(packageFile, File(File(effectiveDataDir), "plugins/" + pluginPackageName))

        getLog()?.info("Starting TC in [${teamcityDir?.getAbsolutePath()}]")
        getLog()?.info("TC data directory is [${effectiveDataDir}]")

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
    override fun execute() {
        getLog()?.info("Stopping TC in [${teamcityDir?.getAbsolutePath()}]")
        val procBuilder = ProcessBuilder()
                .directory(teamcityDir)
                .redirectErrorStream(true)
                .command(createCommand("stop"))
        val resultCode = readOutput(procBuilder.start())
    }
}