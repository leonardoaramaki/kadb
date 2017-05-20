package kadb

fun String.toAscii(): String {
    val output = StringBuilder()
    for (i in 0 until this.length step 2) {
        val str = this.substring(i, i + 2)
        output.append(str.toInt(16).toChar())
    }

    return output.toString()
}

fun String.lengthInHex(): String = this.length.toString(16).padStart(4, '0')

// Change byte order from little-endian to big-endian for ints
fun Int.toLittleEndian(): Long {
    val bigEndian = this.toLong()
    var littleEndian: Long = (bigEndian and 0x00_00_00_ff shl 24)
    littleEndian = littleEndian or (bigEndian and 0x00_00_ff_00 shl 8)
    littleEndian = littleEndian or (bigEndian and 0x00_ff_00_00 shr 8)
    littleEndian = littleEndian or (bigEndian and 0xff_00_00_00 shr 24)
    return littleEndian
}

/**
 * Reorder int to little-endian, convert it to its hex request representation ensuring it has exactly length 8.
 */
fun Int.toLittleEndianString(): String {
    return this.toLittleEndian().toString(16).padStart(8, '0')
}