package peek

import com.sun.org.apache.xpath.internal.operations.Bool
import com.sun.xml.internal.fastinfoset.util.StringArray
import java.io.DataInputStream
import java.io.IOException
import java.io.PrintStream
import java.net.Socket

class AdbClient {
    companion object {
        const val HOST = "localhost"
        const val PORT = 5037
        const val OKAY = "OKAY"
        const val FAIL = "FAIL"
    }

    private var running: Boolean = false

    fun batchAndRun(vararg commands: String) {
        var lastCommand: String? = null
        for (cmd in commands) {
            if (cmd == "host:transport-any") {
                lastCommand = cmd
                continue
            }
            if (lastCommand == "host:transport-any") {
                lastCommand += ";" + cmd
                send(lastCommand, true)
                lastCommand = null
            } else{
                send(cmd)
            }
        }
    }

    @Throws(IOException::class)
    fun send(payload: String, multiple: Boolean = false) {
        val client = Socket(HOST, PORT)
        val writeStream = PrintStream(client.getOutputStream())
        val readStream = DataInputStream(client.getInputStream())

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

        for (cmd in commands) {
            writeStream.print(cmd)
            println("-> $cmd")
            var responseLine: String? = readStream.readLine()
            while (responseLine != null) {
                val ack: String? = responseLine.prefix()
                var output = ""
                when (ack) {
                    OKAY -> output = if (responseLine.payload().isEmpty()) "SUCCESS" else responseLine.payload()
                    FAIL -> output = responseLine.payload()
                }
//            println(output)
                println("<- LOG: $responseLine")
                responseLine = readStream.readLine()
            }
        }
        println("Finished.")
        writeStream.close()
        readStream.close()
        client.close()
    }

    fun prefixCommand(payload: String): String {
        return payload.length.toString(16).padStart(4, '0') + payload
    }
}