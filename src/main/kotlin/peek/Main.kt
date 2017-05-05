package peek

fun main(args: Array<String>) {
    val adbClient = AdbClient()
    try {
        adbClient.batchAndRun(
                "host:version",
                "host:features",
                "host:version",
                "host:transport-any",
                "shell:ls")
    } catch (e: Exception) {
        e.printStackTrace()
    }
    System.exit(0)
}