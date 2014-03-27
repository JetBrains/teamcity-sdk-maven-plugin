/**
 * Created by Nikita.Skvortsov
 * date: 27.03.2014.
 */

import org.jetbrains.teamcity.maven.sdk.AbstractTeamCityMojo
import org.junit.Test
import java.io.File
import org.jetbrains.teamcity.maven.sdk.TCDirectoryState
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.teamcity.maven.sdk.test.TestWithTempFiles


public class TCMojoTest() : TestWithTempFiles() {

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
    public fun unpackTeamcityArchive() {
        val mockTCMojo = MockTCMojo()
        val tempDirectory : File = myTempFiles.newFolder(System.currentTimeMillis().toString())

        mockTCMojo.unpack(File("src/test/resources/sample.tar.gz"), tempDirectory)
        assertTrue(File(tempDirectory, "file.txt").exists())
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

    public fun unpack(archive: File, destination: File) {
        extractTeamCity(archive, destination)
    }
}
