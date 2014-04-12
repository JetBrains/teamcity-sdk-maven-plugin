package org.jetbrains.teamcity.maven.sdk

import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import sun.nio.cs.StandardCharsets
import java.nio.charset.Charset

/**
 * Created by Nikita.Skvortsov
 * date: 12.04.2014.
 */


class ThreadedStringReader(val stream: InputStream, handler: (String) -> Unit) {

    private val BUFFER_SIZE = 4096
    private val continueFlag = AtomicBoolean(true)
    private val thread : Thread = Thread({

        val buffer = ByteArray(BUFFER_SIZE)
        val stringBuffer = StringBuffer()
        while (continueFlag.get()) {
            val available = stream.available()
            if (available == 0) {
                Thread.sleep(100)
            } else {
                val read = stream.read(buffer, 0, available)
                stringBuffer.append(String(buffer, 0, read, Charset.forName("UTF-8")))

                var eoStr = stringBuffer.indexOf("\n")
                while (eoStr > -1) {
                    val substring = stringBuffer.substring(0, eoStr)
                    handler(substring)
                    stringBuffer.delete(0, eoStr + 1)
                    eoStr = stringBuffer.indexOf("\n")
                }
            }
        }

        if (stringBuffer.size > 0) {
            handler(stringBuffer.toString())
        }

    }, "Input stream reader")

    fun start(): ThreadedStringReader {
        thread.start()
        return this
    }

    fun stop(timeout: Long = 1000) {
        continueFlag.set(false)
        thread.join(timeout)
    }
}