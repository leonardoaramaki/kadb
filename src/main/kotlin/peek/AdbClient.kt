package peek

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintStream
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import javax.xml.bind.DatatypeConverter

class AdbClient(settings: Settings) {
    companion object {
        const val CHUNK_MAX_SIZE_PER_SYNC = 65535 // 64K - max chunk size in bytes transferred on a sync message payload
        const val ADB_HOST = "localhost" // adb server running on host machine
        const val ADB_PORT = 5037 // adb default port
        const val OKAY = "OKAY" // default response id when a service request succeeded
        const val FAIL = "FAIL" // default response id when a service request has failed
        const val WORD_SIZE = 4
    }

    internal val config: Settings
    private var serial: String? = null
    private var connected: Boolean = false
    private var fileSyncMode: Boolean = false
    private var writer: PrintStream? = null
    private var reader: BufferedReader? = null
    private var deviceList: MutableList<String> = mutableListOf()
    private var syncLocalPath: String = ""

    init {
        this.config = settings
        this.serial = this.config.serial
    }

    fun batchAndRun(vararg commands: String) {
        var batchCmds: String = ""
        var aggregateCommands = false
        for (cmd in commands) {
            if (cmd == "$TRANSPORT_SERVICE$serial") {
                aggregateCommands = true
                batchCmds = "$TRANSPORT_SERVICE$serial"
                continue
            }
            if (aggregateCommands) {
                batchCmds += ";" + cmd
                continue
            }
            send(cmd)
        }
        if (aggregateCommands) {
            send(batchCmds, true)
        }
    }

    private fun send(payload: String, multiple: Boolean = false, silent: Boolean = false) {
        val client = Socket(ADB_HOST, ADB_PORT)
        writer = PrintStream(client.getOutputStream())
        reader = BufferedReader(InputStreamReader(client.getInputStream()))

        var commands: List<String>
        if (multiple) {
            commands = payload.split(";")
            commands = commands.map {
                prefixLengthHexToPayload(it)
            }
        } else {
            commands = ArrayList<String>()
            commands.add(prefixLengthHexToPayload(payload))
        }

        try {
            for (cmd in commands) {
                var output: String = ""
                if (fileSyncMode) {
                    //TODO: Remove length prefixing for sync subcommands
                    writer?.print(cmd.payload())
                } else {
                    writer?.print(cmd)
                }
                writer?.flush()
                log("-> ${if (config.verbose) if (fileSyncMode) cmd.payload() else cmd else cmd.payload()}")
                if (fileSyncMode) {
                    when (getCommandId(cmd.payload())) {
                        "STAT" -> {
                            val syncData = CharArray(16)
                            var bytesRead: Int = reader?.read(syncData) ?: 0
                            while (bytesRead < 16) {
                                bytesRead += reader?.read(syncData) ?: 0
                            }
                            log("<- ${String(syncData)}")
                        }
                        "RECV" -> {
                            var responseId = ByteArray(WORD_SIZE)
                            client.getInputStream().read(responseId, 0, 4)
                            log("<- ${String(responseId)}")

                            responseId = ByteArray(WORD_SIZE)
                            client.getInputStream().read(responseId, 0, 4)

                            var length = ""
                            String(responseId).forEach {
                                val elem = it.toShort()
                                var tmp = elem.toInt()
                                if (elem < 0) {
                                    tmp = 131 - elem
                                }
                                val hex = tmp.toString(16)
                                if (length.length < 4) {
                                    length += hex
                                }
                            }

                            // Convert from little-endian to big-endian by shifting 24 bytes to the right
                            val fileLength = littleEndianToBigEndian(length.toInt(16))
                            val syncData = ByteArray(fileLength)
                            var maxSizePerRead = if (fileLength > CHUNK_MAX_SIZE_PER_SYNC) CHUNK_MAX_SIZE_PER_SYNC else fileLength
                            var bytesRead: Int = client.getInputStream().read(syncData, 0, maxSizePerRead)
                            log("Read $bytesRead out of $fileLength")
                            while (bytesRead < fileLength) {
                                val remainingBytes = fileLength - bytesRead
                                maxSizePerRead = if (remainingBytes < maxSizePerRead) remainingBytes else maxSizePerRead
                                bytesRead += client.getInputStream().read(syncData, bytesRead, maxSizePerRead)
                                if (bytesRead < 0)
                                    break
                                log("Read $bytesRead out of $fileLength")
                            }
                            val path = Paths.get(syncLocalPath)
                            Files.write(path, syncData, StandardOpenOption.TRUNCATE_EXISTING,
                                StandardOpenOption.CREATE)
                        }
                    }
                } else {
                    val ack = readWord()
                    when (ack) {
                        OKAY -> {
                            when {
                                cmd.payload() == ("$TRANSPORT_SERVICE$serial") -> {
                                    println("Connected with $serial...")
                                    connected = true
                                }
                                cmd.payload() == DEVICES_SERVICE -> {
                                    val len = readWord().toInt(16)
                                    var deviceLine = reader?.readLine()
                                    while (deviceLine != null) {
                                        deviceList.add(deviceLine)
                                        output += "$deviceLine\n"
                                        deviceLine = reader?.readLine()
                                    }
                                }
                                cmd.payload() == SYNC_SERVICE -> {
                                    fileSyncMode = true
                                }
                                connected -> {
                                    var line = reader?.readLine()
                                    while (line != null) {
                                        output += line + "\n"
                                        line = reader?.readLine()
                                    }
                                }
                                else -> {
                                    val len = readWord().toInt(16)
                                    if (config.debuggable) {
                                        var outputLine = reader?.readLine()
                                        while (outputLine != null) {
                                            output += "$outputLine\n"
                                            outputLine = reader?.readLine()
                                        }
                                    }
                                }
                            }
                        }
                        FAIL -> {
                            val len = readWord()
                            output = reader?.readLine() ?: ""
                        }
                    }
                    log("<- $ack")
                }
                if (!silent) println(output)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            log("Closing connection...")
            connected = false
            fileSyncMode = false
            writer?.close()
            reader?.close()
            client.close()
        }
    }

    @Throws(IOException::class)
    private fun readWord(): String {
        val word = CharArray(WORD_SIZE)
        val r = reader
        if (r != null) {
            for (i in word.indices) {
                val c = r.read()
                if (c > -1) word[i] = c.toChar() else break
            }
        }
        return String(word)
    }

    private fun getCommandId(cmd: String): String {
        return cmd.substring(0, 4)
    }

    private fun integerToFourByteHexString(value: Int): String {
        return value.toString(16).padEnd(8, '0')
    }

    private fun fourByteHexStringToAscii(fourByteHexStr: String): String {
        return String(DatatypeConverter.parseHexBinary(fourByteHexStr))
    }

    private fun encodedLengthForSync(len: Int): String {
        return fourByteHexStringToAscii(integerToFourByteHexString(len))
    }

    private fun littleEndianToBigEndian(littleEndian: Int): Int {
        var bigEndian: Int = (littleEndian and 0xff_00) shr 8
        bigEndian = bigEndian or (littleEndian and 0x00_ff shl 8)
        return bigEndian
    }

    private fun prefixLengthHexToPayload(payload: String): String {
        return payload.length.toString(16).padStart(4, '0') + payload
    }

    private fun log(message: Any?) {
        if (config.debuggable) {
            println(message)
        }
    }

    fun pull(remotePath: String, localPath: String? = null, deviceSerial: String? = null) {
        syncLocalPath = localPath ?: Paths.get(remotePath).fileName.toString()
        send(DEVICES_SERVICE, silent = true)
        serial = deviceSerial ?: if (deviceList.size > 0) deviceList[0].split(Regex("\\s"))[0] else config.serial
        batchAndRun(
                VERSION_SERVICE,
                "$TRANSPORT_SERVICE$serial",
                SYNC_SERVICE,
                "STAT${encodedLengthForSync(remotePath.length)}$remotePath",
                "RECV${encodedLengthForSync(remotePath.length)}$remotePath"
        )
    }

    fun devices() {
        send(DEVICES_SERVICE)
    }

    fun shell(cmd: String, deviceSerial: String? = null) {
        send(DEVICES_SERVICE, silent = true)
        serial = deviceSerial ?: if (deviceList.size > 0) deviceList[0].split(Regex("\\s"))[0] else config.serial
        batchAndRun(
                VERSION_SERVICE,
                "$TRANSPORT_SERVICE$serial",
                "$SHELL_SERVICE$cmd"
        )
        deviceList.clear()
    }
}

class Settings {
    inline val Set: Settings
        get() = this
    internal var debuggable: Boolean = true
    internal var verbose: Boolean = false
    internal var serial: String? = null

    infix fun device(serial: String): Settings {
        this.serial = serial
        return this
    }

    infix fun loggingTo(log: Boolean): Settings {
        this.debuggable = log
        return this
    }

    infix fun verboseTo(verbose: Boolean): Settings {
        this.verbose = verbose
        return this
    }
}

fun settings(init: Settings.() -> Unit): Settings {
    val cfg = Settings()
    cfg.init()
    return cfg
}