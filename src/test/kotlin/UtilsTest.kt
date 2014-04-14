/**
 * Created by Nikita.Skvortsov
 * date: 03.04.2014.
 */

import org.jetbrains.teamcity.maven.sdk.getBit
import org.junit.Test
import org.apache.commons.io.IOUtils
import org.jetbrains.teamcity.maven.sdk.ThreadedStringReader
import org.assertj.core.api.Assertions.*
import org.assertj.core.api.IntegerAssert
import com.google.common.io.CountingInputStream
import org.assertj.core.api.ListAssert
import java.io.File
import org.jetbrains.teamcity.maven.sdk.test.TestWithTempFiles
import org.jetbrains.teamcity.maven.sdk.TeamCityRetriever

public class UtilsTest(): TestWithTempFiles()  {

    Test public fun testGetBitExtension() {
        assertThat(1.getBit(0)).isOne()
        assertThat(1.getBit(1)).isZero()

        assertThat(3.getBit(0)).isOne()
        assertThat(3.getBit(1)).isOne()

        assertThat(Integer.MAX_VALUE.getBit(31)).isZero()
        assertThat(Integer.MAX_VALUE.getBit(0)).isOne()
        assertThat(Integer.MAX_VALUE.getBit(30)).isOne()

        assertThat(Integer.MIN_VALUE.getBit(31)).isOne()
    }


    Test public fun testThreadedStringReader() {
        assertThatReadOf("SingleString").containsExactly("SingleString")
        assertThatReadOf("OneString\nTwoString").containsExactly("OneString", "TwoString")
        assertThatReadOf("OneString\nTwoString\n").containsExactly("OneString", "TwoString")
        assertThatReadOf("OneString\nTwoString\n\n").containsExactly("OneString", "TwoString", "")

        assertThatReadOf("").isEmpty()
        assertThatReadOf("\n").containsExactly("")
        assertThatReadOf("\n\n").containsExactly("","")
    }

    Test
    public fun testUnpackTeamcityArchive() {
        val tempDirectory : File = myTempFiles.newFolder(System.currentTimeMillis().toString())

        val retriever = TeamCityRetriever("FakeUrl", "FakeVersion", {(m, l) -> println(m)})
        retriever.doExtractTeamCity(File("src/test/resources/sample.tar.gz"), tempDirectory)

        assertThat(File(tempDirectory, "file.txt")).exists().canRead()
    }



    private fun assertThatReadOf(text: String): ListAssert<String> {
        val readerCollectedValues: MutableList<String> = arrayListOf()

        val stream = CountingInputStream(IOUtils.toInputStream(text)!!)
        val reader = ThreadedStringReader(stream) { readerCollectedValues.add(it) }

        reader.start()
        waitFor {
            stream.getCount() == text.size.toLong()
        }

        reader.stop()
        return assertThat(readerCollectedValues)
    }

    private fun waitFor(timeout: Int = 1000, flag: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeout) {
            if (flag()) {
                return
            } else {
                Thread.sleep(100)
            }
        }
    }

    fun IntegerAssert.isOne() {
        isEqualTo(1)
    }
}