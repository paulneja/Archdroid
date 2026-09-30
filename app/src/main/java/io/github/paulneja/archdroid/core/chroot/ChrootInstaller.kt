package io.github.paulneja.archdroid.core.chroot

import android.content.Context
import io.github.paulneja.archdroid.R
import io.github.paulneja.archdroid.core.init.DefaultAccounts
import io.github.paulneja.archdroid.core.root.RootManager
import io.github.paulneja.archdroid.core.util.RootTar
import io.github.paulneja.archdroid.core.util.ShellQuote.quote
import io.github.paulneja.archdroid.data.model.InstallConfig
import io.github.paulneja.archdroid.data.model.MountConfig
import io.github.paulneja.archdroid.data.model.RootfsDownloadMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class ChrootInstaller(private val context: Context) {

    enum class Step { DOWNLOAD, VERIFY, EXTRACT, BASE_CONFIG, USER, PACKAGES, CLEANUP }

    data class Progress(
        val step: Step,
        val detail: String = "",
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = -1L,
    )

    class InstallException(val step: Step, message: String, cause: Throwable? = null) :
        Exception(message, cause)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private fun tarballFile(): File = InstallFiles.tarball(context.filesDir)

    private fun partialFile(): File = InstallFiles.partial(context.filesDir)

    private fun signatureFile(): File = InstallFiles.signature(context.filesDir)

    private fun verificationFile(): File = InstallFiles.verification(context.filesDir)

    private fun checkpointFile(): File = InstallFiles.checkpoint(context.filesDir)

    suspend fun install(
        config: InstallConfig,
        onProgress: (Progress) -> Unit,
        onLog: (String) -> Unit = {},
    ): Result<Unit> {
        return try {
            log = onLog
            val start = resumeStep(config)
            InstallResumePlan.stepsFrom(start).forEach { step ->
                saveCheckpoint(config, step)
                when (step) {
                    Step.DOWNLOAD -> download(config, onProgress)
                    Step.VERIFY -> verify(onProgress)
                    Step.EXTRACT -> extract(onProgress)
                    Step.BASE_CONFIG -> baseConfig(config, onProgress)
                    Step.USER -> createUser(config, onProgress)
                    Step.PACKAGES -> installPackages(config, onProgress)
                    Step.CLEANUP -> cleanup(onProgress)
                }
                InstallResumePlan.next(step)?.let { saveCheckpoint(config, it) }
            }
            checkpointFile().delete()
            Result.success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            if (failure is InstallException && failure.step == Step.VERIFY && !tarballFile().isFile) {
                saveCheckpoint(config, Step.DOWNLOAD)
            }
            Result.failure(failure)
        }
    }

    private fun resumeStep(config: InstallConfig): Step {
        val lines = checkpointFile().takeIf(File::isFile)?.readLines().orEmpty()
        val savedMode = lines.getOrNull(0)
        val savedStep = lines.getOrNull(1)?.let { name ->
            Step.entries.firstOrNull { it.name == name }
        }
        if (savedMode != null && savedMode != config.downloadMode.name) {
            discardUntrustedDownload()
            checkpointFile().delete()
            return Step.DOWNLOAD
        }
        if (savedStep != null && savedStep != Step.DOWNLOAD && !tarballFile().isFile) {
            return Step.DOWNLOAD
        }
        if (savedStep != null && savedStep != Step.DOWNLOAD && !signatureFile().isFile) {
            return Step.DOWNLOAD
        }
        if (savedStep != null && savedStep.ordinal > Step.VERIFY.ordinal && !verificationFile().isFile) {
            return Step.VERIFY
        }
        return savedStep ?: Step.DOWNLOAD
    }

    private fun saveCheckpoint(config: InstallConfig, step: Step) {
        checkpointFile().writeText("${config.downloadMode.name}\n${step.name}\n")
    }

    private var log: (String) -> Unit = {}

    private suspend fun download(config: InstallConfig, onProgress: (Progress) -> Unit) =
        withContext(Dispatchers.IO) {
            try {
                val mode = config.downloadMode
                val before = downloadSignature()
                val url = ChrootPaths.rootfsUrl(mode)
                val dest = tarballFile()
                if (dest.isFile && dest.length() > 0) {
                    RootfsSizeLimit.requireWithinLimit(dest.length())
                    onProgress(Progress(Step.DOWNLOAD, url, dest.length(), dest.length()))
                } else {
                    val partial = partialFile()
                    val initialBytes = if (partial.isFile) partial.length() else 0L
                    DownloadRetry().run(
                        initialBytes = initialBytes,
                        currentBytes = { if (partial.isFile) partial.length() else 0L },
                    ) { already ->
                        downloadAttempt(mode, url, partial, already, onProgress)
                    }.getOrThrow()
                }
                val after = downloadSignature()
                signatureFile().writeBytes(RootfsSecurity.requireStableSignature(before, after))
                verificationFile().delete()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: RootfsVerificationException) {
                discardUntrustedDownload()
                throw InstallException(Step.VERIFY, failure.message.orEmpty(), failure)
            } catch (failure: RootfsTooLargeException) {
                discardUntrustedDownload()
                throw InstallException(Step.DOWNLOAD, failure.message.orEmpty(), failure)
            }
        }

    private suspend fun downloadAttempt(
        mode: RootfsDownloadMode,
        url: String,
        partial: File,
        already: Long,
        onProgress: (Progress) -> Unit,
    ) {
        RootfsSizeLimit.requireWithinLimit(already)
        onProgress(Progress(Step.DOWNLOAD, url, already, -1L))

        val request = Request.Builder().url(url).get()
            .apply { if (already > 0) header("Range", "bytes=$already-") }
            .build()

        executeFollowingAllowedRedirects(mode, request, Step.DOWNLOAD).use { response ->
            if (DownloadResume.partialIsComplete(response.code, already)) {

                moveIntoPlace(partial)
                onProgress(Progress(Step.DOWNLOAD, url, already, already))
                return
            }
            if (!response.isSuccessful) {
                throw InstallException(Step.DOWNLOAD, "HTTP ${response.code} from $url")
            }
            val body = response.body
                ?: throw InstallException(Step.DOWNLOAD, "empty body from $url")

            val resumed = response.code == 206 && already > 0
            val base = if (resumed) already else 0L
            if (!resumed && already > 0) partial.delete()

            val total = RootfsSizeLimit.totalBytes(base, body.contentLength())
            var downloaded = base
            var lastReport = base

            body.byteStream().use { input ->
                FileOutputStream(partial, resumed).buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        RootfsSizeLimit.requireWithinLimit(downloaded + read)
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded - lastReport > 512 * 1024) {
                            lastReport = downloaded
                            onProgress(Progress(Step.DOWNLOAD, url, downloaded, total))
                        }
                    }
                }
            }
            moveIntoPlace(partial)
            onProgress(Progress(Step.DOWNLOAD, url, downloaded, downloaded))
        }
    }

    private fun moveIntoPlace(partial: File) {
        val dest = tarballFile()
        dest.delete()
        if (!partial.renameTo(dest)) {
            throw InstallException(Step.DOWNLOAD, "cannot move download into place")
        }
    }

    private fun downloadSignature(): ByteArray {
        val url = ChrootPaths.signatureUrl()
        val request = Request.Builder().url(url).get().build()
        return executeFollowingAllowedRedirects(
            RootfsDownloadMode.SECURE,
            request,
            Step.VERIFY,
        ).use { response ->
            if (!response.isSuccessful) {
                throw InstallException(Step.VERIFY, "HTTP ${response.code} from $url")
            }
            val body = response.body
                ?: throw InstallException(Step.VERIFY, "empty signature from $url")
            if (body.contentLength() > MAX_SIGNATURE_BYTES) {
                throw InstallException(Step.VERIFY, "rootfs signature is too large")
            }
            val bytes = body.byteStream().use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (output.size() + read > MAX_SIGNATURE_BYTES) {
                        throw InstallException(Step.VERIFY, "rootfs signature is too large")
                    }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
            if (bytes.isEmpty()) throw InstallException(Step.VERIFY, "empty signature from $url")
            bytes
        }
    }

    private fun executeFollowingAllowedRedirects(
        mode: RootfsDownloadMode,
        initialRequest: Request,
        step: Step,
    ): Response {
        var request = initialRequest
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val url = request.url
            if (!ChrootPaths.isAllowedEndpoint(mode, url.scheme, url.host)) {
                throw InstallException(step, "refused download endpoint: $url")
            }
            val response = client.newCall(request).execute()
            if (!response.isRedirect) return response
            val location = response.header("Location")
            val next = location?.let(url::resolve)
            response.close()
            if (next == null || redirectCount == MAX_REDIRECTS) {
                throw InstallException(step, "invalid download redirect from $url")
            }
            if (!ChrootPaths.isAllowedEndpoint(mode, next.scheme, next.host)) {
                throw InstallException(step, "refused download redirect: $next")
            }
            request = request.newBuilder().url(next).build()
        }
        throw InstallException(step, "too many download redirects")
    }

    private suspend fun verify(onProgress: (Progress) -> Unit) =
        withContext(Dispatchers.IO) {
            onProgress(Progress(Step.VERIFY))
            try {
                val verified = verifier().verify(tarballFile(), signatureFile().readBytes()) {
                    ensureActive()
                }
                verificationFile().writeText(RootfsSecurity.encode(verified))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                discardUntrustedDownload()
                throw InstallException(
                    Step.VERIFY,
                    failure.message ?: "rootfs signature verification failed",
                    failure,
                )
            }
        }

    private suspend fun extract(onProgress: (Progress) -> Unit) {
        verifyBeforeExtraction(onProgress)
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

    private suspend fun verifyBeforeExtraction(onProgress: (Progress) -> Unit) =
        withContext(Dispatchers.IO) {
            onProgress(Progress(Step.VERIFY, "final signature check"))
            try {
                val expected = RootfsSecurity.decode(verificationFile().readText())
                val actual = verifier().verify(tarballFile(), signatureFile().readBytes()) {
                    ensureActive()
                }
                RootfsSecurity.requireUnchanged(expected, actual)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                discardUntrustedDownload()
                throw InstallException(
                    Step.VERIFY,
                    failure.message ?: "final rootfs verification failed",
                    failure,
                )
            }
        }

    private fun verifier(): RootfsVerifier = RootfsVerifier(
        trustedKey = context.resources.openRawResource(R.raw.archlinuxarm_builder).use { it.readBytes() },
        expectedFingerprint = TRUSTED_ROOTFS_FINGERPRINT,
    )

    private fun discardUntrustedDownload() {
        tarballFile().delete()
        partialFile().delete()
        signatureFile().delete()
        verificationFile().delete()
    }

    private suspend fun baseConfig(config: InstallConfig, onProgress: (Progress) -> Unit) {
        onProgress(Progress(Step.BASE_CONFIG))
        val env = config.env
        val etc = EtcConfig.resolvConfCommands(
            ChrootPaths.CHROOT_DIR, env.dnsPrimary, env.dnsSecondary,
        ) + EtcConfig.hostnameCommands(ChrootPaths.CHROOT_DIR, env.hostname) +
            EtcConfig.mtabCommands(ChrootPaths.CHROOT_DIR)
        RootManager.exec(*etc.toTypedArray()).also {
            if (!it.isSuccess) throw InstallException(Step.BASE_CONFIG, it.err.joinToString())
        }

        onProgress(Progress(Step.BASE_CONFIG, "pacman.conf"))
        RootManager.exec(
            *PacmanConf.androidCompatCommands("${ChrootPaths.CHROOT_DIR}/etc/pacman.conf")
                .toTypedArray()
        ).also {
            if (!it.isSuccess) {
                throw InstallException(Step.BASE_CONFIG, "pacman.conf: ${it.err.joinToString()}")
            }
        }

        val mounted = MountManager.ensureMounted(MountConfig())
        if (!mounted.ok) {
            throw InstallException(Step.BASE_CONFIG, "mount failed: ${mounted.log.joinToString()}")
        }

        onProgress(Progress(Step.BASE_CONFIG, "pacman-key --init"))
        chrootBash(Step.BASE_CONFIG, "pacman-key --init && pacman-key --populate archlinuxarm")
        onProgress(Progress(Step.BASE_CONFIG, "pacman -Syu"))
        chrootBash(Step.BASE_CONFIG, "pacman -Syu --noconfirm")

        RootManager.execStreaming(DefaultAccounts.lockScript(ChrootPaths.CHROOT_DIR), log)
    }

    private suspend fun createUser(config: InstallConfig, onProgress: (Progress) -> Unit) {
        val user = config.username.trim()
        if (user.isEmpty()) return
        onProgress(Progress(Step.USER, user))
        chrootBash(Step.USER, "pacman -S --needed --noconfirm sudo")
        chrootBash(Step.USER, UserSetup.ensureUserCommand(user), "ensure user $user exists")
        val password = RootManager.execStreaming(
            UserSetup.setPasswordScript(ChrootPaths.CHROOT_DIR, user, config.password),
            log,
        )
        if (!password.isSuccess) {
            throw InstallException(Step.USER, commandFailure("set password for $user", password.err))
        }
        Sudoers.grantWheelCommands().forEach { chrootBash(Step.USER, it) }

        chrootBash(Step.USER, "id ${quote(user)} >/dev/null")
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
        signatureFile().delete()
        verificationFile().delete()
    }

    private suspend fun chrootBash(step: Step, command: String, failureLabel: String = command) {
        val result = RootManager.execStreaming(
            "chroot ${ChrootPaths.CHROOT_DIR} /bin/bash -lc ${quote(command)}",
            log,
        )
        if (!result.isSuccess) {
            throw InstallException(step, commandFailure(failureLabel, result.err))
        }
    }

    companion object {
        private const val MAX_SIGNATURE_BYTES = 64 * 1024
        private const val MAX_REDIRECTS = 5
        private const val TRUSTED_ROOTFS_FINGERPRINT =
            "68B3537F39A313B3E574D06777193F152BDBE6A6"

        fun commandFailure(label: String, stderr: List<String>): String =
            "$label -> ${stderr.joinToString()}"
    }
}
