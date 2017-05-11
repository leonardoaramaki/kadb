package kadb

fun String.hasPayload(): Boolean {
    return this.length > 4
}

fun String.prefix(): String {
    return if (this.length < 4) "" else this.substring(0, 4)
}

fun String.payload(): String {
    return if (this.hasPayload()) this.substring(4, this.length) else this
}

// Change byte order from little-endian to big-endian for 16 bit ints
fun Int.toBigEndian(): Int {
    var bigEndian: Int = (this and 0xff_00) shr 8
    bigEndian = bigEndian or (this and 0x00_ff shl 8)
    return bigEndian
}

fun Int.toFourByteHexString(): String {
    return this.toString(16).padStart(2, '0').padEnd(8, '0')
}