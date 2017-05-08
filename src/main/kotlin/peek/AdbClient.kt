package peek

import java.io.*
import java.net.Socket
import java.nio.file.Files
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
        const val WORD_SIZE = 4 // 4 bytes - size of response ids and req/res length
    }

    private val config: Settings
    private var serial: String? = null
    private var fileSyncMode: Boolean = false
    private var writer: PrintStream? = null
    private var reader: BufferedReader? = null
    private var devicesFound: MutableList<String> = mutableListOf()
    private var syncLocalFilePath: String = ""
    private var syncRemoteFilePath: String = ""

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
        writer = PrintStream(client.outputStream)
        reader = BufferedReader(InputStreamReader(client.inputStream))

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
                            val path = Paths.get(syncLocalFilePath)
                            val responseId = ByteArray(WORD_SIZE)
                            client.getInputStream().read(responseId, 0, 4)
                            log("<- ${String(responseId)}")

                            var chunkSize = ByteArray(WORD_SIZE)
                            client.inputStream.read(chunkSize, 0, 4)

                            var chunkBytesLength = littleToBigEndian(unsignWord(chunkSize))
                            var chunkData = ByteArray(chunkBytesLength)
                            var chunkBytesRead = client.inputStream.read(chunkData, 0, chunkBytesLength)
                            stdOut("Read $chunkBytesRead out of $chunkBytesLength")
                            var totalFileBytesRead = chunkBytesRead

                            Files.write(path, chunkData, StandardOpenOption.TRUNCATE_EXISTING,
                                    StandardOpenOption.CREATE, StandardOpenOption.WRITE)

                            var counter = 0
                            while (true) {
                                //region check if 4 hex is 'DATA'
                                if (client.inputStream.available() < 4) {
                                    break
                                }
                                client.inputStream.read(chunkSize, 0, 4)
                                val hex = String(chunkSize)
                                if ("DATA" == hex) {
                                    println(hex)
                                    // read another 4 hex to get the next chunk length
                                    client.inputStream.read(chunkSize, 0, 4)
                                    chunkBytesLength = littleToBigEndian(unsignWord(chunkSize))
                                    chunkData = ByteArray(chunkBytesLength)
                                    chunkBytesRead = client.inputStream.read(chunkData, 0, chunkBytesLength)
                                } else if ("DONE" == hex) {
                                    break
                                } else {
                                    chunkData = ByteArray(chunkBytesLength - 4)
                                    chunkBytesRead = client.inputStream.read(chunkData, 0, chunkBytesLength - 4)
                                    Files.write(path, chunkSize, StandardOpenOption.APPEND)
                                }
                                //endregion

                                totalFileBytesRead += chunkBytesRead
                                Files.write(path, chunkData, StandardOpenOption.APPEND)
                                stdOut("Read $chunkBytesRead out of $chunkBytesLength")
                                stdOut("$syncRemoteFilePath: 1 file pulled. ($totalFileBytesRead bytes)")
                            }

                            stdOut("$syncRemoteFilePath: 1 file pulled. ($totalFileBytesRead bytes)")

                        }
                    }
                } else {
                    val ack = readWord()
                    when (ack) {
                        OKAY -> {
                            when {
                                cmd.payload() == ("$TRANSPORT_SERVICE$serial") -> {
                                    println("Transport established with $serial.")
                                }
                                cmd.payload() == DEVICES_SERVICE -> {
                                    val len = readWord().toInt(16)
                                    var deviceLine = reader?.readLine()
                                    while (deviceLine != null) {
                                        devicesFound.add(deviceLine)
                                        output += "$deviceLine\n"
                                        deviceLine = reader?.readLine()
                                    }
                                }
                                cmd.payload() == VERSION_SERVICE -> log("<- ${reader?.readLine()}")
                                cmd.payload() == SYNC_SERVICE -> fileSyncMode = true
                                cmd.payload().startsWith(SHELL_SERVICE) -> {
                                    var line = reader?.readLine()
                                    while (line != null) {
                                        output += "$line\n"
                                        line = reader?.readLine()
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
                if (!silent && output.isNotEmpty()) println(output)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            log("Closing connection...")
            devicesFound.clear()
            fileSyncMode = false
            writer?.close()
            reader?.close()
            client.close()
        }
    }

    private fun unsignWord(dataSize: ByteArray): Int {
        var length = ""
        String(dataSize).forEach {
            val elem = it.toShort()
            var tmp = elem
            if (elem < 0) {
                tmp = elem
            }
            val hex = it.toInt().toString(16)
            if (length.length < 4) {
                length += hex
            }
        }
        return length.toInt(16)
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

    // Change byte order from little-endian to big-endian for 16 bit ints
    private fun littleToBigEndian(littleEndian: Int): Int {
        var bigEndian: Int = (littleEndian and 0xff_00) shr 8
        bigEndian = bigEndian or (littleEndian and 0x00_ff shl 8)
        return bigEndian
    }

    private fun prefixLengthHexToPayload(payload: String): String {
        return payload.length.toString(16).padStart(4, '0') + payload
    }

    private fun switchToDevice(deviceSerial: String?) {
        send(DEVICES_SERVICE, silent = true)
        serial = deviceSerial ?: if (devicesFound.size > 0) devicesFound[0].split(Regex("\\s"))[0] else config.serial
    }

    private fun stdOut(message: Any?) {
        if (config.verbose) {
            println(message)
        }
    }

    private fun log(message: Any?) {
        if (config.debuggable) {
            println(message)
        }
    }

    fun pull(remoteFilePath: String, localFilePath: String? = null, deviceSerial: String? = null) {
        syncRemoteFilePath = remoteFilePath
        syncLocalFilePath = localFilePath ?: Paths.get(remoteFilePath).fileName.toString()
        switchToDevice(deviceSerial)
        batchAndRun(
                VERSION_SERVICE,
                "$TRANSPORT_SERVICE$serial",
                SYNC_SERVICE,
                "STAT${encodedLengthForSync(remoteFilePath.length)}$remoteFilePath",
                "RECV${encodedLengthForSync(remoteFilePath.length)}$remoteFilePath"
        )
    }

    fun devices() {
        send(DEVICES_SERVICE)
    }

    fun shell(cmd: String, deviceSerial: String? = null) {
        switchToDevice(deviceSerial)
        batchAndRun(
                VERSION_SERVICE,
                "$TRANSPORT_SERVICE$serial",
                "$SHELL_SERVICE$cmd"
        )
    }
}