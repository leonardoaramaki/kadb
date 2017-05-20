package kadb

/**
 * Converts an hexadecimal String representation to Ascii.
 *
 * It's done by iterating the string on a 2-step basis in order to take a byte, i.e. 2 hex numbers.
 * Then each of those bytes is first converted to an Int and then to a Char. Since on the JVM the Char is the only
 * data type which is unsigned the result is the Ascii representation of the byte.
 * Note that because of this the hex string in question should have even length.
 */
fun String.toAscii(): String {
    val output = StringBuilder()
    for (i in 0 until this.length step 2) {
        val str = this.substring(i, i + 2)
        output.append(str.toInt(16).toChar())
    }

    return output.toString()
}

fun String.lengthInHex(): String = this.length.toString(16).padStart(4, '0')

/**
 * Change an Int byte order to little-endian.
 */
fun Int.toLittleEndian(): Long {
    val bigEndian = this.toLong()
    var littleEndian: Long = (bigEndian and 0x00_00_00_ff shl 24)
    littleEndian = littleEndian or (bigEndian and 0x00_00_ff_00 shl 8)
    littleEndian = littleEndian or (bigEndian and 0x00_ff_00_00 shr 8)
    littleEndian = littleEndian or (bigEndian and 0xff_00_00_00 shr 24)
    return littleEndian
}

/**
 * Reorder int to little-endian, convert it to hex representation ensuring it has an exactly length of 8.
 */
fun Int.toLittleEndianString(): String {
    return this.toLittleEndian().toString(16).padStart(8, '0')
}

fun ByteArray.toBigEndianInt(): Int {
    var bigEndianInt = 0
    this.forEachIndexed { index, byte ->
        if (index % 2 == 0) {
            val i = byte.toInt() and 0xFF
            val j = this[index + 1].toInt() and 0xFF
            if (index == 0)
                bigEndianInt = bigEndianInt or (j.toString(16).padStart(2, '0') + i.toString(16).padStart(2, '0')).toInt(16)
            else
                bigEndianInt = bigEndianInt or ((j.toString(16).padStart(2, '0') + i.toString(16).padStart(2, '0')).toInt(16) shl 16)
        }
    }
    return bigEndianInt
}

val ByteArray.string: String
    get() = String(this)