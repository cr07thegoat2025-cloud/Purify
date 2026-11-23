package com.purify.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

data class Reciter(val id: String, val name: String, val short: String)

/** Full-surah recitations served by the free Islamic Network audio CDN. */
val Reciters = listOf(
    Reciter("ar.alafasy", "Mishary Rashid Alafasy", "Alafasy"),
    Reciter("ar.abdulbasit", "Abdul Basit Abdus Samad", "Abdul Basit"),
    Reciter("ar.sudais", "Abdurrahman As-Sudais", "As-Sudais"),
    Reciter("ar.husary", "Mahmoud Khalil Al-Husary", "Al-Husary"),
    Reciter("ar.minshawi", "Mohamed Siddiq Al-Minshawi", "Al-Minshawi"),
    Reciter("ar.shuraim", "Saud Ash-Shuraim", "Ash-Shuraim"),
)

object AudioSource {
    const val HOST = "cdn.islamic.network"

    // Not every reciter is hosted at every bitrate, so we probe in this order once and remember it.
    private val bitrates = listOf(128, 64, 192)

    fun url(edition: String, bitrate: Int, surah: Int) =
        "https://$HOST/quran/audio-surah/$bitrate/$edition/$surah.mp3"

    fun localFile(ctx: Context, edition: String, surah: Int): File =
        File(File(ctx.filesDir, "audio/$edition"), "$surah.mp3")

    /** Blocking. Call from a background thread. */
    fun resolveBitrate(ctx: Context, edition: String, surah: Int): Int {
        val prefs = ctx.getSharedPreferences("audio", Context.MODE_PRIVATE)
        val cached = prefs.getInt("bitrate_$edition", 0)
        if (cached != 0) return cached
        for (b in bitrates) {
            if (exists(url(edition, b, surah))) {
                prefs.edit().putInt("bitrate_$edition", b).apply()
                return b
            }
        }
        return 128
    }

    private fun exists(url: String): Boolean = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "HEAD"
        c.connectTimeout = 5000
        c.readTimeout = 5000
        c.instanceFollowRedirects = true
        val code = c.responseCode
        c.disconnect()
        code in 200..299 || code == 405
    } catch (e: Exception) {
        false
    }

    /** Downloads one surah to app storage. Returns true on success. */
    suspend fun download(
        ctx: Context,
        edition: String,
        surah: Int,
        onProgress: (Float) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        val dest = localFile(ctx, edition, surah)
        dest.parentFile?.mkdirs()
        val part = File(dest.parentFile, "$surah.part")
        try {
            val bitrate = resolveBitrate(ctx, edition, surah)
            val conn = URL(url(edition, bitrate, surah)).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.instanceFollowRedirects = true
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return@withContext false
            }
            val total = conn.contentLengthLong
            var read = 0L
            var lastPct = -1
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) {
                            val pct = (read * 100 / total).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(pct / 100f)
                            }
                        }
                    }
                }
            }
            conn.disconnect()
            if (total > 0 && read != total) {
                part.delete()
                false
            } else {
                part.renameTo(dest)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            part.delete()
            throw e
        } catch (e: Exception) {
            part.delete()
            false
        }
    }
}

enum class DownloadEvent { CHANGED, FAILED }

/** Downloads live at app level so they keep going when the screen is closed. */
object Downloads {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()

    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<DownloadEvent> = _events.asSharedFlow()

    fun key(edition: String, surah: Int) = "$edition:$surah"

    fun start(ctx: Context, edition: String, surah: Int) {
        val app = ctx.applicationContext
        val key = key(edition, surah)
        if (jobs.containsKey(key) || AudioSource.localFile(app, edition, surah).exists()) return
        _progress.update { it + (key to 0f) }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            var ok = false
            try {
                ok = AudioSource.download(app, edition, surah) { p ->
                    _progress.update { m -> m + (key to p) }
                }
            } finally {
                jobs.remove(key)
                _progress.update { it - key }
            }
            _events.tryEmit(if (ok) DownloadEvent.CHANGED else DownloadEvent.FAILED)
        }
        jobs[key] = job
        job.start()
    }

    fun cancel(edition: String, surah: Int) {
        jobs[key(edition, surah)]?.cancel()
    }
}
