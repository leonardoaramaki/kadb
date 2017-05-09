package peek

class Settings {
    inline val Set: Settings
        get() = this
    internal var logging: Boolean = true
    internal var verbose: Boolean = false
    internal var serial: String? = null

    infix fun device(serial: String): Settings {
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
}

fun settings(init: Settings.() -> Unit): Settings {
    val cfg = Settings()
    cfg.init()
    return cfg
}
