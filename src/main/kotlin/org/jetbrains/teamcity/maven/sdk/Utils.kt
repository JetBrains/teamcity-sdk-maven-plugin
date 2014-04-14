package org.jetbrains.teamcity.maven.sdk

import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.nio.charset.Charset
import java.io.File
import org.apache.commons.io.FileUtils
import java.net.URL
import org.apache.commons.io.input.CountingInputStream
import java.nio.channels.ReadableByteChannel
import java.nio.channels.Channels
import java.io.FileOutputStream
import java.io.FileInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.util.zip.GZIPInputStream
import java.io.BufferedInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry

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


class TeamCityRetriever(val teamcitySourceURL: String,
                        val teamcityVersion: String,
                        val doLog: (message: String, debug: Boolean) -> Unit) {

    private fun logInfo(message: String) = doLog(message, false)
    private fun logDebug(message: String) = doLog(message, true)

    public fun downloadAndUnpackTeamCity(targetDir: File) {
        var tcDistributionTempFile : File? = null
        logInfo("Downloading")
        try {
            tcDistributionTempFile = doDownloadTeamCity()
            logInfo("Unpacking")
            doExtractTeamCity(tcDistributionTempFile!!, targetDir)
        } finally {
            FileUtils.deleteQuietly(tcDistributionTempFile)
        }
    }

    public fun doDownloadTeamCity(): File {
        val sourceURL = "$teamcitySourceURL/TeamCity-$teamcityVersion.tar.gz"

        logInfo("Downloading TeamCity from $sourceURL")

        val source = URL(sourceURL)
        val sourceStream = source.openStream()
        val downloadCounter = CountingInputStream(sourceStream)

        val sourceChannel : ReadableByteChannel = Channels.newChannel(downloadCounter)!!

        val tempFile = File.createTempFile("teamcityDistro", teamcityVersion)
        val fos = FileOutputStream(tempFile);

        val counterFlag : AtomicBoolean = AtomicBoolean(true)
        val counter = createCounter(counterFlag, downloadCounter)
        try {
            counter.start()
            logInfo("Transferring to temp file ${tempFile.getAbsolutePath()}")
            fos.getChannel().transferFrom(sourceChannel, 0, java.lang.Long.MAX_VALUE);
        } finally {
            counterFlag.set(false)
            counter.join(1000)
            fos.close()
            sourceChannel.close()
        }
        return tempFile
    }

    public fun doExtractTeamCity(archive: File, destination: File): File {
        logInfo("Unpacking TeamCity to ${destination.getAbsolutePath()}")

        destination.mkdirs()
        val counterFlag = AtomicBoolean(true)
        val unpackingCounter = CountingInputStream(FileInputStream(archive))
        val unpackingCounterThread = createCounter(counterFlag, unpackingCounter)

        val tarInput = TarArchiveInputStream(GZIPInputStream(BufferedInputStream(unpackingCounter)))
        val tarChannel = Channels.newChannel(tarInput)!!
        try {
            unpackingCounterThread.start()
            tarInput.eachEntry {
                val name: String
                val entryName = it.getName()
                if (entryName.startsWith("TeamCity")) {
                    name = entryName.substring("TeamCity".length)
                } else {
                    name = entryName
                }
                val destPath = File(destination, name)
                if (it.isDirectory()) {
                    logDebug("Creating dir ${destPath.getAbsolutePath()}")
                    destPath.mkdirs()
                } else {
                    logDebug("Creating dir ${destPath.getParentFile()?.getAbsolutePath()}")
                    destPath.getParentFile()?.mkdirs()
                    logDebug("Creating file ${destPath.getAbsolutePath()}")
                    destPath.createNewFile()
                    val destOS = FileOutputStream(destPath)
                    try {
                        destOS.getChannel().transferFrom(tarChannel, 0, it.getSize())
                        if (it.isExecutable()) {
                            destPath.setExecutable(true)
                        }
                    } finally {
                        destOS.close()
                    }
                }
            }
        } finally {
            counterFlag.set(false)
            unpackingCounterThread.join(1000)
            tarChannel.close()
        }
        return destination
    }

    private fun TarArchiveEntry.isExecutable(): Boolean {
        val EXECUTABLE_BIT_INDEX = 6
        val executableBit = getMode().getBit(EXECUTABLE_BIT_INDEX)
        return executableBit == 1
    }

    protected fun TarArchiveInputStream.eachEntry(f : (TarArchiveEntry) -> Unit) {
        var entry = getNextEntry() as TarArchiveEntry?
        while (entry != null) {
            f(entry as TarArchiveEntry)
            entry = getNextEntry() as TarArchiveEntry?
        }
    }

    private fun createCounter(flag: AtomicBoolean, counter: CountingInputStream): Thread {
        return Thread({
            while(flag.get()) {
                Thread.sleep(1000)
                print("\r" + FileUtils.byteCountToDisplaySize(counter.getByteCount()))
            }
            println()
        })
    }
}

fun Int.getBit(index: Int): Int {
    return this.ushr(index).and(1)
}
