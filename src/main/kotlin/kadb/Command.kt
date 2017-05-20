package kadb

/**
 * Representation of a command to be sent to the adb server running outside a device.
 */
data class Command(val id: String,
                   val payload: String = "",
                   var subCommands: List<Command>? = null) {

    /**
     * Return the length of a command in hexadecimal.
     */
    fun lengthInHex(): String = "$id$payload".lengthInHex()

    /**
     * Returns the payload length in Ascii since all requests that requires a payload have their length in
     * little-endian order and Ascii-encoded for best debugging.
     */
    fun payloadLength(): String = payload.length.toLittleEndianString().toAscii()

    /**
     * Bake request as a string to be sent through a socket.
     *
     * Format: [4-byte length in hex][4-byte command id][subcommands or data payload if any]
     */
    fun request(): String = "${lengthInHex()}$id$payload"

    /**
     * Bake a payload request as string to be sent through a socket.
     *
     * Format: [4-byte command id][payload length little-ended in Ascii][data payload if any]
     */
    fun payloadRequest(): String = "$id${payloadLength()}$payload"
}