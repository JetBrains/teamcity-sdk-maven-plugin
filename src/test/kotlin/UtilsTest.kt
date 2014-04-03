/**
 * Created by Nikita.Skvortsov
 * date: 03.04.2014.
 */


import org.jetbrains.teamcity.maven.sdk.getBit
import kotlin.test.assertEquals
import org.junit.Test

public class UtilsTest {

    Test public fun testGetBitExtension() {
        assertOne(1.getBit(0))
        assertZero(1.getBit(1))

        assertOne(3.getBit(0))
        assertOne(3.getBit(1))

        assertZero(Integer.MAX_VALUE.getBit(31))
        assertOne(Integer.MAX_VALUE.getBit(0))
        assertOne(Integer.MAX_VALUE.getBit(30))

        assertOne(Integer.MIN_VALUE.getBit(31))
    }

    private fun assertOne(actual: Int) {
        assertEquals(1, actual)
    }

    private fun assertZero(actual: Int) {
        assertEquals(0, actual)
    }
}