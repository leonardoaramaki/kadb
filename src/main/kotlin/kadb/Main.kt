package kadb

import org.apache.commons.cli.*

fun main(args: Array<String>) {
    val options = Options()

    options.addOption("d", "devices", false, "list connected devices")

    options.addOption(Option.builder("s")
            .hasArg()
            .argName("SERIAL")
            .desc("use device with given serial number")
            .build())

    options.addOption(Option.builder()
            .longOpt("push")
            .numberOfArgs(2)
            .hasArg()
            .argName("LOCAL")
            .argName("REMOTE")
            .desc("copy local files/directories to device")
            .hasArgs()
            .build())

    options.addOption(Option.builder()
            .longOpt("pull")
            .numberOfArgs(2)
            .argName("REMOTE")
            .argName("LOCAL")
            .desc("copy files/dirs from device")
            .hasArgs()
            .build())

    options.addOption(Option.builder("sh")
            .longOpt("shell")
            .argName("COMMAND")
            .desc("run remote shell command")
            .hasArg()
            .build())

    val cmd = DefaultParser().parse(options, args)
    val serial = if (cmd.hasOption("s") && cmd.getOptionValues("s").isNotEmpty()) cmd.getOptionValues("s")[0] else null
    val adbCli = AdbClient(settings { Set device serial })
    when {
        cmd.hasOption("d") || cmd.hasOption("devices") -> adbCli.devices()
        cmd.hasOption("push") -> {
            if (cmd.getOptionValues("push").isEmpty()) {
                showUsage(options)
                return
            }
            if (cmd.getOptionValues("push").size > 1) {
                adbCli.push(cmd.getOptionValues("push")[0], cmd.getOptionValues("push")[1])
            } else {
                showUsage(options)
            }
        }
        cmd.hasOption("pull") -> {
            if (cmd.getOptionValues("pull").isEmpty()) {
                showUsage(options)
                return
            }
            if (cmd.getOptionValues("pull").size > 1) {
                adbCli.pull(cmd.getOptionValues("pull")[0], cmd.getOptionValues("pull")[1])
            } else {
                adbCli.pull(cmd.getOptionValues("pull")[0])
            }
        }
        (cmd.hasOption("sh") || cmd.hasOption("shell") && cmd.getOptionValues("sh").isNotEmpty()) -> {
            adbCli.shell(cmd.getOptionValues("sh")[0])
        }
        else -> showUsage(options)
    }
}

fun showUsage(options: Options) {
    HelpFormatter().printHelp(1024, "adb", "----------", options, null)
}