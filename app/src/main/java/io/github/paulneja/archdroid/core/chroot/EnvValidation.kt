package io.github.paulneja.archdroid.core.chroot

object EnvValidation {
    private val HOSTNAME_LABEL = Regex("[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?")
    private val IPV4 = Regex("""((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)""")
    private val IPV6 = Regex("[0-9A-Fa-f:.]{2,45}")
    private val LANG = Regex("[A-Za-z]{2,3}(_[A-Za-z]{2})?(\\.[A-Za-z0-9-]+)?(@[A-Za-z0-9]+)?|C|POSIX|C\\.UTF-8")

    fun isHostname(value: String): Boolean =
        value.length in 1..253 && value.split('.').all { HOSTNAME_LABEL.matches(it) }

    fun isDnsServer(value: String): Boolean =
        IPV4.matches(value) || (':' in value && IPV6.matches(value))

    fun isLang(value: String): Boolean = LANG.matches(value)
}
