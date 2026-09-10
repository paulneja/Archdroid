package io.github.paulneja.archdroid.core.chroot

import android.content.Context
import io.github.paulneja.archdroid.core.root.RootManager
import io.github.paulneja.archdroid.core.util.RootTar
import io.github.paulneja.archdroid.core.util.ShellQuote.quote
import io.github.paulneja.archdroid.data.model.InstallConfig
import io.github.paulneja.archdroid.data.model.MountConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
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

    private fun partialFile(): File = File(context.filesDir, "rootfs.tar.gz.part")

    suspend fun install(
        config: InstallConfig,
        onProgress: (Progress) -> Unit,
        onLog: (String) -> Unit = {},
    ): Result<Unit> = runCatching {
        log = onLog
        download(config, onProgress)
        verify(config, onProgress)
        extract(onProgress)
        baseConfig(config, onProgress)
        createUser(config, onProgress)
        installPackages(config, onProgress)
        cleanup(onProgress)
    }

    private var log: (String) -> Unit = {}

    /**
     * Downloads the rootfs (~790 MiB) resuming a previous attempt when the server honours
     * a range request, so a dropped connection does not restart the whole transfer.
     */
    private suspend fun download(config: InstallConfig, onProgress: (Progress) -> Unit) =
        withContext(Dispatchers.IO) {
            val url = ChrootPaths.rootfsUrl(config.mirror)
            val dest = tarballFile()
            if (dest.isFile && dest.length() > 0) {
                onProgress(Progress(Step.DOWNLOAD, url, dest.length(), dest.length()))
                return@withContext
            }

            val partial = partialFile()
            val already = if (partial.isFile) partial.length() else 0L
            onProgress(Progress(Step.DOWNLOAD, url, already, -1L))

            val request = Request.Builder().url(url).get()
                .apply { if (already > 0) header("Range", "bytes=$already-") }
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw InstallException(Step.DOWNLOAD, "HTTP ${response.code} from $url")
                }
                val body = response.body
                    ?: throw InstallException(Step.DOWNLOAD, "empty body from $url")

                // 206 means the server resumed; anything else restarts from zero.
                val resumed = response.code == 206 && already > 0
                val base = if (resumed) already else 0L
                if (!resumed && already > 0) partial.delete()

                val total = body.contentLength().let { if (it >= 0) it + base else -1L }
                var downloaded = base
                var lastReport = base

                body.byteStream().use { input ->
                    FileOutputStream(partial, resumed).buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
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
                dest.delete()
                if (!partial.renameTo(dest)) {
                    throw InstallException(Step.DOWNLOAD, "cannot move download into place")
                }
                onProgress(Progress(Step.DOWNLOAD, url, downloaded, downloaded))
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
                // A corrupt archive must not survive into the next attempt.
                file.delete()
                partialFile().delete()
                throw InstallException(Step.VERIFY, "md5 mismatch: expected $expected got $actual")
            }
        }

    private suspend fun extract(onProgress: (Progress) -> Unit) {
        onProgress(Progress(Step.EXTRACT))
        val tarball = quote(tarballFile().absolutePath)
        val script = buildString {
            appendLine("C=${quote(ChrootPaths.CHROOT_DIR)}")
            appendLine("mkdir -p \"\$C\"")
            append(RootTar.RESOLVE)
            appendLine("\$TAR -xpf $tarball -C \"\$C\"")
            appendLine("test -f ${ChrootPaths.MARKER_FILE}")
        }
        val result = RootManager.execStreaming(script, log)
        if (!result.isSuccess) {
            throw InstallException(Step.EXTRACT, "tar failed: ${result.err.joinToString()}")
        }
    }

    private suspend fun baseConfig(config: InstallConfig, onProgress: (Progress) -> Unit) {
        onProgress(Progress(Step.BASE_CONFIG))
        val env = config.env
        val script = buildString {
            appendLine("C=${quote(ChrootPaths.CHROOT_DIR)}")
            appendLine(
                "printf 'nameserver %s\\nnameserver %s\\n' ${quote(env.dnsPrimary)} " +
                    "${quote(env.dnsSecondary)} > ${ChrootPaths.RESOLV_CONF}"
            )
            appendLine("echo ${quote(env.hostname)} > \"\$C/etc/hostname\"")
        }
        RootManager.execScript(script).also {
            if (!it.isSuccess) throw InstallException(Step.BASE_CONFIG, it.err.joinToString())
        }

        val mounted = MountManager.ensureMounted(MountConfig())
        if (!mounted.ok) {
            throw InstallException(Step.BASE_CONFIG, "mount failed: ${mounted.log.joinToString()}")
        }

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
        chrootBash(Step.USER, "useradd -m -G wheel -s /bin/bash ${quote(user)}")
        chrootBash(Step.USER, "echo ${quote("$user:${config.password}")} | chpasswd")
        chrootBash(
            Step.USER,
            "sed -i 's/^# %wheel ALL=(ALL:ALL) ALL/%wheel ALL=(ALL:ALL) ALL/' /etc/sudoers"
        )
    }

    private suspend fun installPackages(config: InstallConfig, onProgress: (Progress) -> Unit) {
        val pkgs = config.packages.filter { it.isNotBlank() }
        if (pkgs.isEmpty()) return
        onProgress(Progress(Step.PACKAGES, pkgs.joinToString(" ")))
        chrootBash(Step.PACKAGES, "pacman -S --needed --noconfirm ${pkgs.joinToString(" ") { quote(it) }}")
    }

    private suspend fun cleanup(onProgress: (Progress) -> Unit) {
        onProgress(Progress(Step.CLEANUP))
        tarballFile().delete()
        partialFile().delete()
    }

    private suspend fun chrootBash(step: Step, command: String) {
        val result = RootManager.execStreaming(
            "chroot ${ChrootPaths.CHROOT_DIR} /bin/bash -lc ${quote(command)}",
            log,
        )
        if (!result.isSuccess) {
            throw InstallException(step, "$command -> ${result.err.joinToString()}")
        }
    }
}
