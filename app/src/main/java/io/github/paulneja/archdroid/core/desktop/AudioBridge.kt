package io.github.paulneja.archdroid.core.desktop

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket

class AudioBridge(private val scope: CoroutineScope) {

    private var job: Job? = null
    private var activeSocket: Socket? = null

    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                runCatching { playOnce() }
                delay(RECONNECT_MILLIS)
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        runCatching { activeSocket?.close() }
        activeSocket = null
    }

    private suspend fun playOnce() {
        val context = currentCoroutineContext()
        Socket().use { socket ->
            synchronized(this) {
                context.ensureActive()
                activeSocket = socket
            }
            try {
                socket.connect(InetSocketAddress("127.0.0.1", PORT), 1_000)
                val minBuffer = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuffer * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                try {
                    context.ensureActive()
                    track.play()
                    val input = socket.getInputStream()
                    val buffer = ByteArray(minBuffer)
                    while (true) {
                        context.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        var offset = 0
                        while (offset < read) {
                            context.ensureActive()
                            val written = track.write(buffer, offset, read - offset, AudioTrack.WRITE_NON_BLOCKING)
                            check(written >= 0) { "Audio write failed: $written" }
                            if (written == 0) delay(WRITE_RETRY_MILLIS) else offset += written
                        }
                    }
                } finally {
                    track.release()
                }
            } finally {
                synchronized(this) {
                    if (activeSocket === socket) activeSocket = null
                }
            }
        }
    }

    companion object {
        const val PORT = 4713
        const val RATE = 48_000
        private const val RECONNECT_MILLIS = 2_000L
        private const val WRITE_RETRY_MILLIS = 10L

        /** Loaded by the session script once pipewire-pulse is up. */
        const val PULSE_MODULE_COMMAND =
            "audio_sink=\$(pactl get-default-sink) && [ -n \"\$audio_sink\" ] && " +
                "pactl load-module module-simple-protocol-tcp rate=$RATE format=s16le channels=2 " +
                "source=\"\$audio_sink.monitor\" record=true port=$PORT listen=127.0.0.1"
    }
}
