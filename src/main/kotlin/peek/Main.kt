package peek

import org.apache.commons.cli.*

fun main(args: Array<String>) {
    val options = Options()

    options.addOption("d", "devices", false, "list connected devices")

    options.addOption(Option.builder("s")
            .hasArg()
            .argName("SERIAL")
            .desc("use device with given serial number")
            .build())

    options.addOption(Option.builder("p")
            .longOpt("pull")
            .numberOfArgs(2)
            .argName("REMOTE")
            .argName("LOCAL")
            .desc("copy files/firs from device")
            .hasArgs()
            .build())

    options.addOption(Option.builder("sh")
            .longOpt("shell")
            .argName("COMMAND")
            .desc("run remote shell command")
            .hasArg()
            .build())

    val cmd = DefaultParser().parse(options, args)

    val serial = if (cmd.hasOption("s") && cmd.getOptionValues("s").isNotEmpty()) cmd.getOptionValues("s")[0] else ""

    val settings = settings {
        Set device serial verboseTo false loggingTo false
    }

    val adbCli = AdbClient(settings)

    when {
        cmd.hasOption("d") || cmd.hasOption("devices") -> adbCli.devices()
        cmd.hasOption("p") -> {
            if (noDeviceSet(settings)) {
                println("No device set")
                return
            }
            if (cmd.getOptionValues("p").size > 1) {
                adbCli.pull(cmd.getOptionValues("p")[0], cmd.getOptionValues("p")[1])
            } else {
                adbCli.pull(cmd.getOptionValues("p")[0])
            }
        }
        cmd.hasOption("pull") -> {
            if (noDeviceSet(settings)) {
                println("No device set")
                return
            }
            if (cmd.getOptionValues("pull").size > 1) {
                adbCli.pull(cmd.getOptionValues("pull")[0], cmd.getOptionValues("pull")[1])
            } else {
                adbCli.pull(cmd.getOptionValues("pull")[0])
            }
        }
        (cmd.hasOption("sh") || cmd.hasOption("shell") && cmd.getOptionValues("sh").isNotEmpty()) -> {
            if (noDeviceSet(settings)) {
                println("No device set")
                return
            }
            adbCli.shell(cmd.getOptionValues("sh")[0])
        }
        else -> HelpFormatter().printHelp("adb", options)
    }
}

fun noDeviceSet(settings: Settings): Boolean = settings.serial.isNullOrEmpty()