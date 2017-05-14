package kadb

data class Command(val id: String,
                   val payload: String = "",
                   var subCommands: List<Command>? = null) {

    fun lengthInHex(): String = "$id$payload".lengthInHex()

    fun payloadLength(): String = payload.length.toLittleEndianString().toAscii()

    fun request(): String = "${lengthInHex()}$id$payload"

    fun payloadRequest(): String = "$id${payloadLength()}$payload"
}