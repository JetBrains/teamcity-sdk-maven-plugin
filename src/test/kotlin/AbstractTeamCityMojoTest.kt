/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

/**
 * Created by Nikita.Skvortsov
 * date: 27.03.2014.
 */

import org.jetbrains.teamcity.maven.sdk.AbstractTeamCityMojo
import org.jetbrains.teamcity.maven.sdk.TCDirectoryState
import org.jetbrains.teamcity.maven.sdk.test.TestWithTempFiles
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


public class TCMojoTest(): TestWithTempFiles() {

    val default_8_0_2_path = "src/test/resources/defaultServerLocation/servers/8.0.2"

    @Test
    public fun checkTeamCityByDefaultPath() {
        assertEquals(TCDirectoryState.GOOD, MockTCMojo().checkDir(File(default_8_0_2_path)))
    }

    @Test
    public fun checkTeamCityWithWrongVersion() {
        assertEquals(TCDirectoryState.MISVERSION, MockTCMojo("8.1").checkDir(File(default_8_0_2_path)))
    }

    @Test
    public fun checkTeamCityWrongDirectory() {
        assertEquals(TCDirectoryState.BAD, MockTCMojo().checkDir(File("unexistingLocation")))
    }

    @Test
    public fun silentDownloadTeamCity() {
        val tcDir = myTempFiles.newFolder("TC_DIR")
        val mockTCMojo = MockTCMojo().setSilentDownload(true)
                .setDownloadSource(File("src/test/resources").absoluteFile.toURI().toURL().toString())
                .setTeamCityDir(tcDir)

        mockTCMojo.execute()
        assertEquals(TCDirectoryState.GOOD, MockTCMojo().checkDir(tcDir))
    }

    @Test
    public fun runServerOnly() {
        val tcDir = myTempFiles.newFolder("TC_DIR")
        val mockTCMojo = MockTCMojo().setSilentDownload(true)
                .setDownloadSource(File("src/test/resources").absoluteFile.toURI().toURL().toString())
                .setTeamCityDir(tcDir)
                .setStartAgent(false)
        mockTCMojo.execute()
        assertEquals(TCDirectoryState.GOOD, MockTCMojo().checkDir(tcDir))
        val log = mockTCMojo.dumpLog()
        assertTrue(log.contains("teamcity-server"))
        assertFalse(log.contains("runAll"))
    }
}


class MockTCMojo: AbstractTeamCityMojo {

    private val logStub = StringBuilder()

    constructor(ver: String = "8.0.2") {
        teamcityVersion = ver
    }

    override fun doExecute() {
        logStub.append(createRunCommand()).append("\n");
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

    public fun setStartAgent(flag: Boolean): MockTCMojo {
        startAgent = flag
        return this
    }

    public fun dumpLog() : String = logStub.toString()
}
