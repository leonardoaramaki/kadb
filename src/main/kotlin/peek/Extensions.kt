package peek

fun String.hasPayload(): Boolean {
    return this.length > 4
}

fun String.prefix(): String {
    return if (this.length < 4) "" else this.substring(0, 4)
}

fun String.payload(): String {
    return if (this.hasPayload()) this.substring(8, this.length) else this
}
