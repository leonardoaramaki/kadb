package kadb

import javax.xml.bind.DatatypeConverter

fun String.hasPayload(): Boolean {
    return this.length > 4
}

fun String.prefix(): String {
    return if (this.length < 4) "" else this.substring(0, 4)
}

fun String.payload(): String {
    return if (this.hasPayload()) this.substring(4, this.length) else this
}

//fun String.toAscii() = String(DatatypeConverter.parseHexBinary(this))
fun String.toAscii(): String {
    val output = StringBuilder()
    for (i in 0 until this.length step 2) {
        val str = this.substring(i, i + 2)
        output.append(str.toInt(16).toChar())
    }

    return output.toString()
}

// Change byte order from little-endian to big-endian for 16 bit ints
fun Int.toLittleEndian(): Long {
    val bigEndian = this.toLong()
    var littleEndian: Long = (bigEndian and 0x00_00_00_ff shl 24)
    littleEndian = littleEndian or (bigEndian and 0x00_00_ff_00 shl 8)
    littleEndian = littleEndian or (bigEndian and 0x00_ff_00_00 shr 8)
    littleEndian = littleEndian or (bigEndian and 0xff_00_00_00 shr 24)
    return littleEndian
}

fun Int.toFourByteHexString() = this.toString(16).padStart(2, '0').padEnd(8, '0')

fun Int.toLengthForSync(): String {
    return this.toLittleEndian().toString(16).padStart(8, '0')
}