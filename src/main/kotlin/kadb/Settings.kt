package kadb

class Settings {
    inline val Set: Settings
        get() = this
    internal var logging: Boolean = false
    internal var verbose: Boolean = true
    internal var serial: String? = null

    infix fun device(serial: String?): Settings {
        this.serial = serial
        return this
    }

    infix fun loggingTo(log: Boolean): Settings {
        this.logging = log
        return this
    }

    infix fun verboseTo(verbose: Boolean): Settings {
        this.verbose = verbose
        return this
    }

    fun quiet(block: () -> Unit) {
        this.verbose = false
        block()
        this.verbose = true
    }
}

fun settings(init: Settings.() -> Unit): Settings {
    val cfg = Settings()
    cfg.init()
    return cfg
}
