package peek

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintStream
import java.net.Socket

class AdbClient(settings: Settings) {
    companion object {
        const val HOST = "localhost"
        const val PORT = 5037
        const val OKAY = "OKAY"
        const val FAIL = "FAIL"
        const val WORD_SIZE = 4
    }

    internal val config: Settings
    private var serial: String? = null
    private var connected: Boolean = false
    private var writer: PrintStream? = null
    private var reader: BufferedReader? = null
    private var deviceList: MutableList<String> = mutableListOf()

    init {
        this.config = settings
    }

    fun batchAndRun(vararg commands: String) {
        var lastCommand: String? = null
        for (cmd in commands) {
            if (cmd == "host:transport:$serial") {
                lastCommand = cmd
                continue
            }
            if (lastCommand == "host:transport:$serial") {
                lastCommand += ";" + cmd
                send(lastCommand, true)
                lastCommand = null
            } else {
                send(cmd)
            }
        }
    }

    private fun send(payload: String, multiple: Boolean = false, silent: Boolean = false) {
        val client = Socket(HOST, PORT)
        writer = PrintStream(client.getOutputStream())
        reader = BufferedReader(InputStreamReader(client.getInputStream()))

        var commands: List<String>
        if (multiple) {
            commands = payload.split(";")
            commands = commands.map {
                prefixCommand(it)
            }
        } else {
            commands = ArrayList<String>()
            commands.add(prefixCommand(payload))
        }

        try {
            for (cmd in commands) {
                writer?.print(cmd + "\n")
                writer?.flush()
                p("-> ${cmd.payload()}")
                val ack = readWord()
                var output: String = ""
                when (ack) {
                    OKAY -> {
                        when {
                            cmd.payload().startsWith("host:transport") -> {
                                println("Connected with $serial...")
                                connected = true
                            }
                            cmd.payload() == "host:deviceList" -> {
                                val len = readWord().toInt(16)
                                var deviceLine = reader?.readLine()
                                while (deviceLine != null) {
                                    deviceList.add(deviceLine)
                                    output += "$deviceLine\n"
                                    deviceLine = reader?.readLine()
                                }
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
                        val len = readWord().toInt(16)
                        output = reader?.readLine() ?: ""
                    }
                }
                p("<- $ack")
                if (!silent) println(output)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            p("Finished.")
            connected = false
            writer?.close()
            reader?.close()
            client.close()
        }
    }

    @Throws(IOException::class)
    private fun readWord(): String {
        val word = CharArray(WORD_SIZE)
        val r = reader
        r?.let {
            for (i in word.indices) {
                var c = r.read()
                if (c > -1) word[i] = c.toChar() else break
            }
        }
        return String(word)
    }

    private fun prefixCommand(payload: String): String {
        return payload.length.toString(16).padStart(4, '0') + payload
    }

    fun devices() {
        send("host:deviceList")
    }

    fun shell(cmd: String, deviceSerial: String? = null) {
        send("host:deviceList", silent = true)
        serial = deviceSerial ?: if (deviceList.size > 0) deviceList[0].split(Regex("\\s"))[0] else config.serial
        batchAndRun(
                "host:version",
                "host-serial:$serial:features",
                "host:transport:$serial",
                "shell:" + cmd
        )
        deviceList.clear()
    }

    internal inline fun p(message: Any?) {
        if (config.debuggable) {
            println(message)
        }
    }
}

class Settings {
    inline val Set: Settings
        get() = this
    internal var debuggable: Boolean = true
    internal var serial: String? = null

    infix fun device(serial: String): Settings {
        this.serial = serial
        return this
    }

    infix fun withLoggingSetTo(log: Boolean): Settings {
        this.debuggable = log
        return this
    }
}

fun settings(init: Settings.() -> Unit): Settings {
    val cfg = Settings()
    cfg.init()
    return cfg
}