/**
 * Created by Nikita.Skvortsov
 * date: 27.03.2014.
 */

import org.jetbrains.teamcity.maven.sdk.AbstractTeamCityMojo
import org.junit.Test
import java.io.File
import org.jetbrains.teamcity.maven.sdk.TCDirectoryState
import kotlin.test.assertEquals
import org.jetbrains.teamcity.maven.sdk.test.TestWithTempFiles


public class TCMojoTest(): TestWithTempFiles() {

    val default_8_0_2_path = "src/test/resources/defaultServerLocation/servers/8.0.2"


    Test
    public fun checkTeamCityByDefaultPath() {
        assertEquals(TCDirectoryState.GOOD, MockTCMojo().checkDir(File(default_8_0_2_path)))
    }

    Test
    public fun checkTeamCityWithWrongVersion() {
        assertEquals(TCDirectoryState.MISVERSION, MockTCMojo("8.1").checkDir(File(default_8_0_2_path)))
    }

    Test
    public fun checkTeamCityWrongDirectory() {
        assertEquals(TCDirectoryState.BAD, MockTCMojo().checkDir(File("unexistingLocation")))
    }

    Test
    public fun silentDownloadTeamCity() {
        val tcDir = myTempFiles.newFolder("TC_DIR")
        val mockTCMojo = MockTCMojo().setSilentDownload(true)
                                     .setDownloadSource(File("src/test/resources").getAbsoluteFile().toURI().toURL().toString())
                                     .setTeamCityDir(tcDir)

        mockTCMojo.execute()
        assertEquals(TCDirectoryState.GOOD, MockTCMojo().checkDir(tcDir))
    }
}


class MockTCMojo(ver: String = "8.0.2") : AbstractTeamCityMojo() {
    {
        teamcityVersion = ver
    }
    override fun doExecute() {
        // do nothing
    }

    public fun checkDir(dir: File = teamcityDir!!): TCDirectoryState {
        return evalTeamCityDirectory(dir)
    }

    public fun setSilentDownload(flag: Boolean): MockTCMojo {
        downloadQuietly = flag
        return this
    }

    public fun setDownloadSource(urlStr: String): MockTCMojo {
        teamcitySourceURL = urlStr
        return this
    }

    public fun setTeamCityDir(file: File): MockTCMojo {
        teamcityDir = file
        return this
    }
}
