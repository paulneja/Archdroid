package io.github.paulneja.archdroid.core.chroot

import io.github.paulneja.archdroid.core.util.ShellQuote.quote

object UserSetup {

    private val ANDROID_NET_GROUPS = listOf("aid_inet" to 3003, "aid_net_raw" to 3004)

    /** Runs inside the chroot. */
    fun ensureUserCommand(user: String): String {
        val u = quote(user)
        return buildString {
            ANDROID_NET_GROUPS.forEach { (name, gid) ->
                append("getent group $gid >/dev/null || groupadd -g $gid $name; ")
            }
            // Android reserves 6000..7999; 8000..8999 stays outside app UIDs and Doze filters.
            append("id $u >/dev/null 2>&1 || useradd -K UID_MIN=8000 -K UID_MAX=8999 -K GID_MIN=8000 -K GID_MAX=8999 -m -G wheel -s /bin/bash $u; ")
            val groups = ANDROID_NET_GROUPS.joinToString(",") { (_, gid) ->
                "\"\$(getent group $gid | cut -d: -f1)\""
            }
            append("usermod -aG wheel,$groups $u")
        }
    }

    /** Only runs before mounts and user sessions exist. */
    fun migrateLegacyUsersScript(chrootDir: String): String {
        val inner = """
            set -e
            export PATH=/usr/local/sbin:/usr/local/bin:/usr/bin
            while IFS=: read -r name _ old gid _ home shell; do
              [ "${'$'}old" -ge 8000 ] && [ "${'$'}old" -le 8999 ] && continue
              if ! { [ "${'$'}old" -ge 1000 ] && [ "${'$'}old" -lt 10000 ]; } &&
                 ! { [ "${'$'}old" -ge 60000 ] && [ "${'$'}old" -lt 65000 ]; }; then continue; fi
              case "${'$'}shell" in */nologin|*/false|"") continue;; esac
              new=8000
              while getent passwd "${'$'}new" >/dev/null || getent group "${'$'}new" >/dev/null; do
                new=${'$'}((new + 1))
                [ "${'$'}new" -le 8999 ] || exit 1
              done
              newgid=${'$'}gid
              if [ "${'$'}gid" = "${'$'}old" ] && [ "${'$'}(getent group "${'$'}gid" | cut -d: -f1)" = "${'$'}name" ]; then
                groupmod -g "${'$'}new" "${'$'}name"
                newgid=${'$'}new
              fi
              usermod -u "${'$'}new" -g "${'$'}newgid" "${'$'}name"
              find / -xdev -uid "${'$'}old" -exec chown -h "${'$'}new" {} +
              if [ "${'$'}newgid" != "${'$'}gid" ]; then
                find / -xdev -gid "${'$'}gid" -exec chgrp -h "${'$'}newgid" {} +
              fi
            done < /etc/passwd
        """.trimIndent()
        val root = quote(chrootDir)
        return "awk -v root=$root '" +
            "${'$'}2 == root || index(${'$'}2, root \"/\") == 1 { found=1 } END { exit found }' /proc/mounts || exit 0\n" +
            "chroot $root /bin/bash -c ${quote(inner)}"
    }

    fun setPasswordScript(chrootDir: String, user: String, password: String): String {
        val line = quote("$user:$password")
        return "if command -v print >/dev/null 2>&1; then print -r -- $line; else echo $line; fi | " +
            "chroot ${quote(chrootDir)} /usr/bin/chpasswd"
    }
}
