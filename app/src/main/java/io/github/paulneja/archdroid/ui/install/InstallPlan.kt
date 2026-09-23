package io.github.paulneja.archdroid.ui.install

object InstallPlan {

    const val MIN_FREE_BYTES = 2_500_000_000L
    const val MIN_PASSWORD_LENGTH = 4

    private val USERNAME = Regex("[a-z_][a-z0-9_-]{0,31}")

    val AVAILABLE_PACKAGES = listOf(
        "base-devel", "git", "vim", "nano", "openssh", "wget", "curl",
        "zsh", "tmux", "htop", "fastfetch", "python",
    )

    fun usernameValid(username: String): Boolean = USERNAME.matches(username)

    fun packagesFor(selected: Set<String>, loginAsRoot: Boolean): List<String> =
        (selected + "sudo").distinct()

    fun canInstall(
        freeBytes: Long,
        username: String,
        password: String,
        loginAsRoot: Boolean,
    ): Boolean {
        if (freeBytes < MIN_FREE_BYTES) return false
        if (loginAsRoot) return true
        return usernameValid(username) && password.length >= MIN_PASSWORD_LENGTH
    }

    fun logLine(step: String, detail: String): String {
        val name = step.lowercase()
        return if (detail.isBlank()) name else "$name: $detail"
    }
}
