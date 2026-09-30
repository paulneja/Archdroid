package io.github.paulneja.archdroid.core.init

import io.github.paulneja.archdroid.core.terminal.ProcessShield
import io.github.paulneja.archdroid.core.util.ShellQuote.quote

object SystemdContainer {

    const val CGROUP = "${ProcessShield.CGROUP}/systemd"

    fun bootCommand(chrootDir: String): String {
        val c = quote(chrootDir)
        val init = "mount -t cgroup2 cgroup2 /sys/fs/cgroup && " +
            "exec env container=archdroid /usr/lib/systemd/systemd --system"
        val inner = "mkdir -p $CGROUP; echo \$\$ > $CGROUP/cgroup.procs; " +
            "exec chroot $c /usr/bin/unshare --pid --fork --mount --uts --cgroup --mount-proc " +
            "/bin/sh -c ${quote(init)}"
        return "sh -c ${quote(inner)} </dev/null >/dev/null 2>&1 &"
    }

    fun findPid1Script(chrootDir: String): String {
        val d = '$'
        return """
            for p in /proc/[0-9]*; do
              [ "$d{p}/root" -ef ${quote(chrootDir.trimEnd('/'))} ] || continue
              grep -qE '^NSpid:[[:space:]]+[0-9]+[[:space:]]+1$' "$d{p}/status" 2>/dev/null || continue
              echo "$d{p#/proc/}"; break
            done
        """.trimIndent()
    }

    fun loginCommand(chrootDir: String, pid1: String, user: String?): String {
        val target = if (user == null) "/bin/bash -l" else "/bin/su - ${quote(user)}"
        return "exec chroot ${quote(chrootDir)} /usr/bin/nsenter --target $pid1 --all $target"
    }

    fun poweroffCommand(chrootDir: String, pid1: String): String =
        "chroot ${quote(chrootDir)} /usr/bin/nsenter --target $pid1 --all /usr/bin/systemctl poweroff"
}
