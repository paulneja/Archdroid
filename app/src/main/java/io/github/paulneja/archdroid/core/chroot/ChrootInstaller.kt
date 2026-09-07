package io.github.paulneja.archdroid.core.chroot

import android.content.Context
import io.github.paulneja.archdroid.core.root.RootManager
import io.github.paulneja.archdroid.data.model.InstallConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class ChrootInstaller(private val context: Context) {

    enum class Step { DOWNLOAD, VERIFY, EXTRACT, BASE_CONFIG, USER, PACKAGES, CLEANUP }

    data class Progress(
        val step: Step,
        val detail: String = "",
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = -1L,
    )

    class InstallException(val step: Step, message: String) : Exception(message)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun tarballFile(): File = File(context.filesDir, "rootfs.tar.gz")

    suspend fun install(
        config: InstallConfig,
        onProgress: (Progress) -> Unit,
    ): Result<Unit> = runCatching {
        download(config, onProgress)
        verify(config, onProgress)
        extract(onProgress)
        baseConfig(config, onProgress)
        createUser(config, onProgress)
        installPackages(config, onProgress)
        cleanup(onProgress)
    }

    private suspend fun download(config: InstallConfig, onProgress: (Progress) -> Unit) =
        withContext(Dispatchers.IO) {
            val url = ChrootPaths.rootfsUrl(config.mirror)
            val dest = tarballFile()
            val partial = File(dest.parent, dest.name + ".part")
            onProgress(Progress(Step.DOWNLOAD, url))

            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw InstallException(Step.DOWNLOAD, "HTTP ${response.code} from $url")
                }
                val body = response.body
                    ?: throw InstallException(Step.DOWNLOAD, "empty body from $url")
                val total = body.contentLength()
                dest.delete()
                partial.delete()
                body.byteStream().use { input ->
                    partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L
                        var lastReport = 0L
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (downloaded - lastReport > 512 * 1024) {
                                lastReport = downloaded
                                onProgress(Progress(Step.DOWNLOAD, url, downloaded, total))
                            }
                        }
                    }
                }
                partial.renameTo(dest)
                onProgress(Progress(Step.DOWNLOAD, url, total, total))
            }
        }

    private suspend fun verify(config: InstallConfig, onProgress: (Progress) -> Unit) =
        withContext(Dispatchers.IO) {
            onProgress(Progress(Step.VERIFY))
            val md5Url = ChrootPaths.md5Url(config.mirror)
            val expected = runCatching {
                client.newCall(Request.Builder().url(md5Url).get().build()).execute().use { r ->
                    if (!r.isSuccessful) return@use null
                    r.body?.string()?.trim()?.split(Regex("\\s+"))?.firstOrNull()
                }
            }.getOrNull()

            val file = tarballFile()
            val digest = MessageDigest.getInstance("MD5")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }

            if (expected != null && !expected.equals(actual, ignoreCase = true)) {
                file.delete()
                throw InstallException(Step.VERIFY, "md5 mismatch: expected $expected got $actual")
            }
        }

    private suspend fun extract(onProgress: (Progress) -> Unit) {
        onProgress(Progress(Step.EXTRACT))
        val tarball = tarballFile().absolutePath
        val script = buildString {
            appendLine("C=${ChrootPaths.CHROOT_DIR}")
            appendLine("mkdir -p \"\$C\"")
            appendLine("TAR=\"tar\"")
            appendLine("for bb in /data/adb/magisk/busybox /data/adb/ksu/bin/busybox /data/adb/ap/bin/busybox; do")
            appendLine("  [ -x \"\$bb\" ] && TAR=\"\$bb tar\" && break")
            appendLine("done")
            appendLine("\$TAR -xpf '$tarball' -C \"\$C\" 2>/dev/null || tar -xf '$tarball' -C \"\$C\"")
            appendLine("test -f ${ChrootPaths.MARKER_FILE}")
        }
        val result = RootManager.execScript(script)
        if (!result.isSuccess) {
            throw InstallException(Step.EXTRACT, "tar failed: ${result.err.joinToString()}")
        }
    }

    private suspend fun baseConfig(config: InstallConfig, onProgress: (Progress) -> Unit) {
        onProgress(Progress(Step.BASE_CONFIG))
        val env = config.env
        val script = buildString {
            appendLine("C=${ChrootPaths.CHROOT_DIR}")
            appendLine("printf 'nameserver %s\nnameserver %s\n' '${env.dnsPrimary}' '${env.dnsSecondary}' > ${ChrootPaths.RESOLV_CONF}")
            appendLine("echo '${env.hostname}' > \"\$C/etc/hostname\"")
        }
        RootManager.execScript(script).also {
            if (!it.isSuccess) throw InstallException(Step.BASE_CONFIG, it.err.joinToString())
        }

        MountManager.ensureMounted(io.github.paulneja.archdroid.data.model.MountConfig())

        onProgress(Progress(Step.BASE_CONFIG, "pacman-key --init"))
        chrootBash(Step.BASE_CONFIG, "pacman-key --init && pacman-key --populate archlinuxarm")
        onProgress(Progress(Step.BASE_CONFIG, "pacman -Syu"))
        chrootBash(Step.BASE_CONFIG, "pacman -Syu --noconfirm")
    }

    private suspend fun createUser(config: InstallConfig, onProgress: (Progress) -> Unit) {
        val user = config.username.trim()
        if (user.isEmpty()) return
        onProgress(Progress(Step.USER, user))
        chrootBash(Step.USER, "pacman -S --needed --noconfirm sudo")
        chrootBash(
            Step.USER,
            "useradd -m -G wheel -s /bin/bash ${shq(user)} && echo ${shq("$user:${config.password}")} | chpasswd"
        )
        chrootBash(
            Step.USER,
            "sed -i 's/^# %wheel ALL=(ALL:ALL) ALL/%wheel ALL=(ALL:ALL) ALL/' /etc/sudoers"
        )
    }

    private suspend fun installPackages(config: InstallConfig, onProgress: (Progress) -> Unit) {
        val pkgs = config.packages.filter { it.isNotBlank() }
        if (pkgs.isEmpty()) return
        onProgress(Progress(Step.PACKAGES, pkgs.joinToString(" ")))
        chrootBash(Step.PACKAGES, "pacman -S --needed --noconfirm ${pkgs.joinToString(" ")}")
    }

    private suspend fun cleanup(onProgress: (Progress) -> Unit) {
        onProgress(Progress(Step.CLEANUP))
        tarballFile().delete()
    }

    private suspend fun chrootBash(step: Step, command: String) {
        val result = RootManager.execScript(
            "chroot ${ChrootPaths.CHROOT_DIR} /bin/bash -c ${shq(command)}"
        )
        if (!result.isSuccess) {
            throw InstallException(step, "$command -> ${result.err.joinToString()}")
        }
    }

    private fun shq(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
