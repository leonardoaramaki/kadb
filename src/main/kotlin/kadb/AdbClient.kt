package kadb

import java.io.*
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Paths

class AdbClient(private var config: Settings = settings { Set loggingTo false verboseTo false },
                private var serial: String? = config.serial,
                private var syncMode: Boolean = false,
                private var writer: PrintStream? = null,
                private var reader: BufferedReader? = null,
                private var devicesFound: MutableSet<String> = mutableSetOf(),
                private var syncLocalFilePath: String = "",
                private var syncRemoteFilePath: String = "") {

    companion object {
        const val MAX_SIZE_PER_SYNC = 1024 * 64   // 64K - max chunk size in bytes transferred on a sync message payload
        const val ADB_HOST = "localhost" // adb server running on host machine
        const val ADB_PORT = 5037 // adb default port
        const val OKAY = "OKAY" // default response id when a service request succeeded
        const val FAIL = "FAIL" // default response id when a service request has failed
        const val WORD_SIZE = 4 // 4 bytes - size of response ids and req/res length
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
            var fileNotFound = false
            for (cmd in commands) {
                var output: String = ""
                if (syncMode) {
                    if (fileNotFound) {
                        stdOut("adb: error: remote object '$syncRemoteFilePath' does not exist")
                        break
                    }
                    client.outputStream.write(cmd.payload().toByteArray())
                } else {
                    client.outputStream.write(cmd.toByteArray())
                }
                client.outputStream.flush()
                log("-> ${if (config.verbose) if (syncMode) cmd.payload() else cmd else cmd.payload()}")
                if (syncMode) {
                    when (getSyncId(cmd.payload())) {
                        "STAT" -> {
                            val chunkSize = ByteArray(WORD_SIZE)
                            client.inputStream.read(chunkSize, 0, 4)
                            log("<- ${String(chunkSize)}")
                            val available = client.inputStream.available()
                            val ZEROES = ByteArray(available)
                            val BUFFER = ByteArray(available)
                            client.inputStream.read(BUFFER, 0, available)
                            // adb server returns all zeroes for STAT if file not found remotely
                            fileNotFound = String(BUFFER) == String(ZEROES)
                        }
                        "SEND" -> {
                            val file = Paths.get(syncLocalFilePath)
                            val fileInputStream = Files.newInputStream(file)
                            val buffer = ByteArray(MAX_SIZE_PER_SYNC)
                            var bytesRead = fileInputStream.read(buffer, 0, MAX_SIZE_PER_SYNC)
                            var offset = bytesRead

                            while (bytesRead > 0) {
                                client.outputStream.write("DATA".toByteArray())
                                val syncLen = bytesRead.toLittleEndian().toString(16).padStart(8, '0')
                                syncLen.forEachIndexed { index, value ->
                                    if (index % 2 == 0) {
                                        val hex = value.toString() + syncLen[index + 1]
                                        client.outputStream.write(hex.toInt(16))
                                    }
                                }
                                client.outputStream.write(buffer, 0, bytesRead)

                                bytesRead = fileInputStream.read(buffer, 0, MAX_SIZE_PER_SYNC)
                                offset += if (bytesRead > -1) bytesRead else 0
                            }

                            val lastModifiedTimeInMillis = Files.getLastModifiedTime(file).toInstant().nano
                            client.outputStream.write("DONE${lastModifiedTimeInMillis.toLengthForSync()}".toByteArray())
                            log("-> DONE${lastModifiedTimeInMillis.toLengthForSync()}")
                            stdOut("${file.fileName}: 1 file pushed. (${offset} bytes)")
                        }
                        "RECV" -> {
                            val syncIdArray = ByteArray(WORD_SIZE)
                            val chunkSizeArray = ByteArray(WORD_SIZE)
                            val outputFile = File(syncLocalFilePath)
                            if (outputFile.exists()) {
                                outputFile.delete()
                            }
                            outputFile.createNewFile()
                            val outputStream = FileOutputStream(outputFile)
                            var totalBytes = 0
                            var bytesLength = 0
                            var keepPulling = true

                            val ds = DataInputStream(client.inputStream)
                            while (keepPulling) {
                                ds.readFully(syncIdArray)
                                val syncId = String(syncIdArray)
                                if (syncId == "DATA") {
                                    // read another 4 hex to get the next chunk length
                                    ds.readFully(chunkSizeArray, 0, 4)
                                    bytesLength = 0
                                    chunkSizeArray.forEachIndexed { index, byte ->
                                        if (index % 2 == 0) {
                                            val i = byte.toInt() and 0xFF
                                            val j = chunkSizeArray[index + 1].toInt() and 0xFF
                                            if (index == 0)
                                                bytesLength = bytesLength or (j.toString(16).padStart(2, '0') + i.toString(16).padStart(2, '0')).toInt(16)
                                            else
                                                bytesLength = bytesLength or ((j.toString(16).padStart(2, '0') + i.toString(16).padStart(2, '0')).toInt(16) shl 16)
                                        }
                                    }
                                    log("Next chunk len is $bytesLength bytes")
                                    val data = ByteArray(bytesLength)
                                    ds.readFully(data)

                                    totalBytes += bytesLength
                                    outputStream.write(data, 0, bytesLength)
                                    outputStream.flush()

                                } else if (syncId == "DONE") {
                                    log("<- DONE")
                                    keepPulling = false
                                    outputStream.flush()
                                    outputStream.close()
                                    stdOut("$syncRemoteFilePath: 1 file pulled. ($totalBytes bytes)")
                                }
                            }
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
                                cmd.payload() == SYNC_SERVICE -> syncMode = true
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
            syncMode = false
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

    private fun getSyncId(cmd: String): String {
        return cmd.substring(0, 4)
    }

    private fun prefixLengthHexToPayload(payload: String): String {
        return payload.length.toString(16).padStart(4, '0') + payload
    }

    private fun switchToDevice(deviceSerial: String?) {
        send(DEVICES_SERVICE, silent = true)
        // device serial switch preferred order:
        // serial at argument or else serial set at config or else first serial gotten from a devices check
        serial = deviceSerial ?: if (config.serial.isNullOrEmpty()) devicesFound.first().split(Regex("\\s"))[0] else config.serial
    }

    private fun stdOut(message: Any?) {
        println(message)
    }

    private fun log(message: Any?) {
        if (config.logging) {
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
                "STAT${remoteFilePath.length.toLengthForSync().toAscii()}$remoteFilePath",
                "RECV${remoteFilePath.length.toLengthForSync().toAscii()}$remoteFilePath"
        )
    }

    fun push(localFilePath: String, remoteFilePath: String, deviceSerial: String? = null) {
        if (!Files.exists(Paths.get(localFilePath))) {
            stdOut("adb: error: cannot stat '$localFilePath': No such file or directory")
            return
        }
        syncLocalFilePath = localFilePath
        syncRemoteFilePath = remoteFilePath + Paths.get(localFilePath).fileName
        switchToDevice(deviceSerial)
        val sendPayload = "$syncRemoteFilePath,33204"
        batchAndRun(
                VERSION_SERVICE,
                TRANSPORT_SERVICE + serial,
                SYNC_SERVICE,
                "STAT${remoteFilePath.length.toLengthForSync().toAscii()}$remoteFilePath",
                "SEND${sendPayload.length.toLengthForSync().toAscii()}$sendPayload"
        )
    }

    fun devices() {
        stdOut("List of devices attached")
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