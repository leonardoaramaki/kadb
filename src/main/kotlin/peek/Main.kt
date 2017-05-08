package peek

fun main(args: Array<String>) {
    val adbCli = AdbClient(settings {
        Set device "emulator-5554" loggingTo true verboseTo true
    })
//    adbCli.shell("run-as org.bitbucket.leoaramaki.onyo sh -c 'ls -la'")
//    adbCli.pull("/sdcard/ic_avatar.png")
//    adbCli.pull("/sdcard/arquivo.txt")
    adbCli.pull("/sdcard/streaming.png")
}