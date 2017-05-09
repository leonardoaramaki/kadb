package peek

import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.OptionBuilder
import org.apache.commons.cli.Options

fun main(args: Array<String>) {
    val options = Options()

    options.addOption("d", "devices", false, "list connected devices")
    OptionBuilder.hasArgs(2)
    OptionBuilder.withArgName("<REMOTE> <LOCAL>")
    OptionBuilder.withDescription("copy files/dirs from device")
    val pullOpt = OptionBuilder.create("pull")
    options.addOption(pullOpt)

    OptionBuilder.hasArgs(1)
    OptionBuilder.withArgName("<COMMAND>")
    OptionBuilder.withDescription("run remote shell command")
    val shellOpt = OptionBuilder.create("shell")
    options.addOption(shellOpt)
    val parser = DefaultParser()
    val cmd = parser.parse(options, args)

    val adbCli = AdbClient()
    when {
        cmd.hasOption("d") || cmd.hasOption("devices") -> adbCli.devices()
        cmd.hasOption("pull") -> println("AAAAAAAA")
        cmd.hasOption("shell    ") -> println("AAAAAAAA")
        else -> HelpFormatter().printHelp("adb", options)
    }
//    val adbCli = AdbClient(settings {
//        Set device "emulator-5554" loggingTo false verboseTo true
//    })
////    adbCli.shell("run-as org.bitbucket.leoaramaki.onyo sh -c 'ls -la'")
////    adbCli.pull("/sdcard/ic_avatar.png")
//    adbCli.pull("/sdcard/arquivo.txt")
//    adbCli.pull("/sdcard/dsds.png")
}