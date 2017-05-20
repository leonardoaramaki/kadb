package kadb

import java.io.*
import java.net.Socket
import java.util.*
import kotlin.properties.Delegates

class AdbClient(private var config: Settings = settings { Set loggingTo false verboseTo true }) {

    private val devicesConnected: MutableSet<String> = mutableSetOf()
    private lateinit var localFilePath: String
    private lateinit var remoteFilePath: String
    private var socket: Socket by Delegates.notNull<Socket>()
    private var serial: String? = config.serial
    private var reuseSocket = false

    companion object {
        const val MAX_SIZE_PER_SYNC = 1024 * 64   // 64K - max chunk size in per sync request
        const val ADB_HOST = "127.0.0.1" // adb server running on host machine
        const val ADB_PORT = 5037 // adb default port
    }

    private fun query(vararg commands: Command) {
        for (command in commands) {
            if (!reuseSocket) {
                socket = Socket(ADB_HOST, ADB_PORT)
            }

            when (command.id) {
                "sync:" -> {
                    runSync(command, socket)
                }
                "shell:" -> {
                    runShell(command, socket)
                }
                "host:devices" -> {
                    socket.use { sock ->
                        sock.outputStream.write(command.request())
                        assertStatus(sock.inputStream)
                        val hex4 = ByteArray(4)
                        val dis = DataInputStream(sock.inputStream)
                        while (dis.available() > 0) {
                            dis.readSync(hex4)
                            val len = hex4.string.toInt(16)
                            val deviceLine = ByteArray(len)
                            dis.readSync(deviceLine)
                            devicesConnected.add(deviceLine.string.split('\t')[0])
                            screen("${deviceLine.string}\n")
                        }
                    }
                }
                "host:version" -> {
                    socket.use { sock ->
                        sock.outputStream.write(command.request())
                        assertStatus(sock.inputStream)
                        DataInputStream(sock.inputStream).line()
                    }
                }
                else -> {
                    // If we start a transport we need to reuse the socket
                    reuseSocket = command.id.startsWith("host:transport")
                    socket.outputStream.write(command.request())
                    socket.outputStream.flush()
                    assertStatus(socket.inputStream)
                    if (!reuseSocket)
                        socket.close()
                }
            }
        }
    }

    private fun runSync(sync: Command, socket: Socket) {
        socket.use { sock ->
            val hex4 = ByteArray(4)
            val dis = DataInputStream(sock.inputStream)
            val dos = DataOutputStream(sock.outputStream)

            dos.write(sync.request())

            assertStatus(sock.inputStream)
            val subCommands = sync.subCommands ?: fail("sync: with no subcommands")
            val statResult = stat(subCommands[0])
            when (subCommands[1].id) {
                "SEND" -> {
                    val localFile = File(localFilePath)
                    if (statResult) {
                        remoteFilePath += localFile.name
                    }
                    dos.write(Command("SEND", remoteFilePath + ",33204").payloadRequest())
                    val fis = DataInputStream(FileInputStream(localFile))
                    var available = fis.available()
                    var bytesRead = if (available > MAX_SIZE_PER_SYNC) MAX_SIZE_PER_SYNC else available
                    val buffer = ByteArray(bytesRead)
                    fis.readSync(buffer)
                    var offset = bytesRead
                    var numFiles = 0

                    while (available > 0) {
                        val syncLen = bytesRead.toLittleEndianString()
                        dos.write("DATA")
                        syncLen.forEachIndexed { index, value ->
                            if (index % 2 == 0) {
                                val hex = value.toString() + syncLen[index + 1]
                                dos.write(hex.toInt(16))
                            }
                        }
                        dos.write(buffer.string, 0, bytesRead)

                        available = fis.available()
                        bytesRead = if (available > MAX_SIZE_PER_SYNC) MAX_SIZE_PER_SYNC else available
                        fis.readFully(buffer, 0, bytesRead)
                        offset += if (bytesRead > -1) bytesRead else 0
                    }

                    val lastModifiedTimeInMillis = localFile.lastModified().toInt().toLittleEndianString().toAscii()
                    dos.write("DONE$lastModifiedTimeInMillis")
                    dis.readSync(hex4)
                    if (hex4.string == "FAIL") {
                        dis.readSync(hex4)
                        val errorMsgLen = hex4[0].toInt()
                        val errorMsg = ByteArray(errorMsgLen)
                        dis.readSync(errorMsg)
                        fail("adb: error: failed to copy '${localFile.name}' to '$remoteFilePath': remote ${errorMsg.string}")
                    } else {
                        numFiles++
                    }
                    screen("${localFile.name}: $numFiles file(s) pushed. ($offset bytes)")
                }
                "RECV" -> {
                    val msg = subCommands[1].payloadRequest()
                    dos.write(msg)
                    val chunkSizeArray = ByteArray(4)
                    val outputFile = File(localFilePath)
                    if (outputFile.exists()) {
                        outputFile.delete()
                    }
                    outputFile.createNewFile()
                    val fos = DataOutputStream(FileOutputStream(outputFile))
                    var totalBytes = 0
                    var pull = true

                    while (pull) {
                        dis.readSync(hex4)
                        if (hex4.string == "DATA") {
                            // read another 4 hex to get the next chunk length
                            dis.readSync(chunkSizeArray, 0, 4)
                            val bytesLength = chunkSizeArray.toBigEndianInt()
                            val data = ByteArray(bytesLength)
                            dis.readSync(data)

                            totalBytes += bytesLength
                            fos.write(data, 0, bytesLength)
                            fos.flush()
                        } else if (hex4.string == "DONE") {
                            fos.flush()
                            pull = false
                            screen("$remoteFilePath: 1 file pulled. ($totalBytes bytes)")
                        }
                    }
                }
            }
            quit(sock.outputStream)
        }
    }

    /**
     * Make stat request to adb server.
     * Returns a boolean indicating the existence of a remote file/folder
     */
    private fun stat(command: Command): Boolean {
        val dis = DataInputStream(socket.inputStream)
        val dos = DataOutputStream(socket.outputStream)
        val hex4 = ByteArray(4)
        // Get path length, still in plain decimal
        //TODO: test for 1024 max path length
        dos.write(command.payloadRequest())
        dis.readSync(hex4)
        val hex12 = ByteArray(12)
        dis.readSync(hex12)
        val stats = !Arrays.equals(hex12, byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
        return stats
    }

    private fun runShell(command: Command, socket: Socket) {
        socket.use { sock ->
            val hex4 = ByteArray(4)
            val dis = DataInputStream(sock.inputStream)
            val dos = DataOutputStream(sock.outputStream)
            dos.write(command.request())
            dis.readSync(hex4)
            if (hex4.string == "OKAY") {
                var line = dis.line()
                while (line != null) {
                    screen("$line\n")
                    line = dis.line()
                }
            }
            reuseSocket = false
        }
    }

    private fun assertStatus(inputStream: InputStream, message: String? = null): String {
        val hex4 = ByteArray(4)
        val dis = DataInputStream(inputStream)
        dis.readSync(hex4)
        if (hex4.string == "FAIL") {
            if (message == null || message.isEmpty()) {
                dis.readSync(hex4)
                val len = hex4.string.toInt(16)
                val error = ByteArray(len)
                dis.readSync(error)
                fail(error.string)
            } else {
                fail(message)
            }
        }
        return "OKAY"
    }


    private fun quit(outputStream: OutputStream) {
        outputStream.write("QUIT")
        reuseSocket = false
    }

    private fun deviceTo(deviceSerial: String?) {
        config.quiet {
            devices()
        }
        // device serial switch preferred order:
        // serial at argument or else serial set at config or else first serial gotten from a devices check
        serial = deviceSerial ?: if (config.serial.isNullOrEmpty()) devicesConnected.first().split(Regex("\\s"))[0] else config.serial
    }

    private fun screen(message: Any?) = if (config.verbose) println(message) else {
    }

    private fun log(message: Any?) = if (config.logging) println(message) else {
    }

    private fun fail(message: String): Nothing {
        val throwable = Throwable(message)
        Thread.setDefaultUncaughtExceptionHandler { t, e -> System.err.println(e.message) }
        throw throwable
    }

    //region stream extensions
    fun OutputStream.write(data: String) {
        log("-> $data")
        this.write(data.toByteArray())
    }

    fun OutputStream.write(s: String, off: Int, len: Int) {
        log("-> $s")
        this.write(s.toByteArray(), off, len)
    }

    fun DataInputStream.readSync(b: ByteArray, off: Int, len: Int) {
        this.readFully(b, off, len)
        log("<- ${b.string}")
    }

    fun DataInputStream.readSync(b: ByteArray) {
        this.readFully(b)
        log("<- ${b.string}")
    }

    fun DataInputStream.line(): String? {
        val line = this.bufferedReader().readLine()
        if (line != null) log("<- $line")
        return line
    }
    //endregion

    fun pull(remoteFilePath: String, localFilePath: String? = null, deviceSerial: String? = null) {
        this.remoteFilePath = remoteFilePath
        this.localFilePath = localFilePath ?: File(remoteFilePath).name
        deviceTo(deviceSerial)
        query(
                Command("host:version"),
                Command("host:transport:$serial"),
                Command("sync:", subCommands = arrayListOf(
                        Command("STAT", remoteFilePath),
                        Command("RECV", remoteFilePath))
                )
        )
    }

    fun push(localFilePath: String, remoteFilePath: String, deviceSerial: String? = null) {
        if (!File(localFilePath).exists()) fail("adb: error: cannot stat '$localFilePath': No such file or directory")
        this.localFilePath = localFilePath
        this.remoteFilePath = remoteFilePath
        deviceTo(deviceSerial)
        query(
                Command("host:version"),
                Command("host:transport:$serial"),
                Command("sync:", subCommands = arrayListOf(
                        Command("STAT", remoteFilePath),
                        Command("SEND"))
                )
        )
    }

    fun devices() {
        screen("List of devices attached")
        query(Command("host:devices"))
    }

    fun shell(cmd: String, deviceSerial: String? = null) {
        deviceTo(deviceSerial)
        query(
                Command("host:version"),
                Command("host:transport:$serial"),
                Command("shell:", cmd)
        )
    }
}