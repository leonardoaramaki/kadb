package peek

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * Created by leo on 04/05/17.
 */

fun main(args: Array<String>) {
    val process = Runtime.getRuntime().exec("adb")
    Thread({
        val stream: Any
        if (process.inputStream.available() > 0)
            stream = process.inputStream
        else
            stream = process.errorStream
        val input = BufferedReader(InputStreamReader(stream))
        var line: String? = input.readLine()
        try {
            while (line != null) {
                println(line)
                line = input.readLine()
            }
            val exitVal = process.waitFor()
            println("Exit code: $exitVal")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }).start()
}