/**
 * Created by Nikita.Skvortsov
 * date: 03.04.2014.
 */


import org.jetbrains.teamcity.maven.sdk.getBit
import kotlin.test.assertEquals
import org.junit.Test
import java.io.StringBufferInputStream
import java.io.StringReader
import org.apache.commons.io.IOUtils
import org.jetbrains.teamcity.maven.sdk.ThreadedStringReader
import kotlin.test.assertFalse
import org.assertj.core.api.Assertions.*
import org.assertj.core.api.IntegerAssert
import java.util.concurrent.atomic.AtomicInteger
import com.google.common.io.CountingInputStream
import org.assertj.core.api.ListAssert

public class UtilsTest {

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