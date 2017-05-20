package kadb

fun main(args: Array<String>) {
    val adb = AdbClient(settings { Set loggingTo true })
    adb.push("settings.gradle", "/sdcard/")
    adb.shell("ls -la")
    adb.pull("/sdcard/streaming.png")
}