package peek

fun main(args: Array<String>) {
    val adbCli = AdbClient(settings {
        Set device "emulator-5556" withLoggingSetTo false
    })
    adbCli.shell("ls -la")
}