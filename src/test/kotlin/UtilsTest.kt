/**
 * Created by Nikita.Skvortsov
 * date: 03.04.2014.
 */


import org.jetbrains.teamcity.maven.sdk.getBit
import kotlin.test.assertEquals
import org.junit.Test

public class UtilsTest {

    Test public fun testGetBitExtension() {
        assertIsOne(1.getBit(0))
        assertIsZero(1.getBit(1))

        assertIsOne(3.getBit(0))
        assertIsOne(3.getBit(1))

        assertIsZero(Integer.MAX_VALUE.getBit(31))
        assertIsOne(Integer.MAX_VALUE.getBit(0))
        assertIsOne(Integer.MAX_VALUE.getBit(30))

        assertIsOne(Integer.MIN_VALUE.getBit(31))
    }

    private fun assertIsOne(actual: Int) {
        assertEquals(1, actual)
    }

    private fun assertIsZero(actual: Int) {
        assertEquals(0, actual)
    }
}