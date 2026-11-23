package com.purify.app

import android.app.Application
import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class PlayerUi(
    val surah: Int? = null,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val repeat: Int = Player.REPEAT_MODE_OFF,
)

class PurifyViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context = app.applicationContext
    private val prefs = ctx.getSharedPreferences("purify", Context.MODE_PRIVATE)

    val surahs: List<Surah> = SurahRepo.load(ctx)

    private val _reciter = MutableStateFlow(
        Reciters.firstOrNull { it.id == prefs.getString("reciter", null) } ?: Reciters.first()
    )
    val reciter: StateFlow<Reciter> = _reciter.asStateFlow()

    private val _downloaded = MutableStateFlow<Set<Int>>(emptySet())
    val downloaded: StateFlow<Set<Int>> = _downloaded.asStateFlow()

    val progress: StateFlow<Map<String, Float>> = Downloads.progress

    private val _player = MutableStateFlow(PlayerUi())
    val player: StateFlow<PlayerUi> = _player.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncFrom(player)
        }

        override fun onPlayerError(error: PlaybackException) {
            _messages.tryEmit("Couldn't play this surah. Check your connection, or download it first.")
        }
    }

    init {
        refreshDownloaded()

        viewModelScope.launch {
            Downloads.events.collect { event ->
                when (event) {
                    DownloadEvent.CHANGED -> refreshDownloaded()
                    DownloadEvent.FAILED ->
                        _messages.tryEmit("Download failed. Check your connection and try again.")
                }
            }
        }

        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                controller = future.get()
                onConnected()
            } catch (e: Exception) {
                _messages.tryEmit("Couldn't start the audio player.")
            }
        }, MoreExecutors.directExecutor())
    }

    private fun onConnected() {
        val c = controller ?: return
        c.addListener(listener)
        // If a recitation is already playing from a previous launch, show that reciter.
        if (c.mediaItemCount > 0) {
            val edition = c.getMediaItemAt(0).mediaId.substringBefore(':')
            Reciters.firstOrNull { it.id == edition }?.let {
                if (it != _reciter.value) {
                    _reciter.value = it
                    refreshDownloaded()
                }
            }
        }
        syncFrom(c)
        viewModelScope.launch {
            while (true) {
                controller?.let { if (it.isPlaying) syncFrom(it) }
                delay(500)
            }
        }
    }

    private fun syncFrom(p: Player) {
        val surah = p.currentMediaItem?.mediaId?.substringAfter(':', "")?.toIntOrNull()
        _player.value = PlayerUi(
            surah = surah,
            isPlaying = p.isPlaying,
            buffering = p.playbackState == Player.STATE_BUFFERING && p.playWhenReady,
            positionMs = p.currentPosition.coerceAtLeast(0L),
            durationMs = if (p.duration == C.TIME_UNSET) 0L else p.duration.coerceAtLeast(0L),
            repeat = p.repeatMode,
        )
    }

    private fun buildItems(r: Reciter): List<MediaItem> = surahs.map { s ->
        MediaItem.Builder()
            .setMediaId("${r.id}:${s.number}")
            .setUri(AudioSource.url(r.id, 128, s.number))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Surah ${s.english}")
                    .setArtist(r.name)
                    .setAlbumTitle("Purify")
                    .build()
            )
            .build()
    }

    fun playSurah(n: Int) {
        val c = controller
        if (c == null) {
            _messages.tryEmit("The player is still starting. Try again in a moment.")
            return
        }
        val r = _reciter.value
        val sameQueue = c.mediaItemCount == surahs.size &&
            c.getMediaItemAt(0).mediaId.startsWith("${r.id}:")
        if (!sameQueue) {
            c.setMediaItems(buildItems(r), n - 1, 0L)
            c.prepare()
            c.play()
        } else if (c.currentMediaItemIndex == n - 1) {
            togglePlayPause()
        } else {
            c.seekTo(n - 1, 0L)
            c.prepare()
            c.play()
        }
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(c.currentMediaItemIndex, 0L)
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() {
        controller?.seekToNext()
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun selectReciter(r: Reciter) {
        if (r == _reciter.value) return
        _reciter.value = r
        prefs.edit().putString("reciter", r.id).apply()
        refreshDownloaded()
        val c = controller
        if (c != null && c.mediaItemCount > 0) {
            // Different reciters have different timings, so the same surah restarts from 0.
            val index = c.currentMediaItemIndex
            c.setMediaItems(buildItems(r), index, 0L)
            c.prepare()
        }
    }

    fun download(n: Int) {
        Downloads.start(ctx, _reciter.value.id, n)
    }

    fun cancelDownload(n: Int) {
        Downloads.cancel(_reciter.value.id, n)
    }

    fun deleteDownload(n: Int) {
        AudioSource.localFile(ctx, _reciter.value.id, n).delete()
        refreshDownloaded()
    }

    private fun refreshDownloaded() {
        val dir = File(ctx.filesDir, "audio/${_reciter.value.id}")
        _downloaded.value = dir.listFiles()
            ?.filter { it.extension == "mp3" }
            ?.mapNotNull { it.nameWithoutExtension.toIntOrNull() }
            ?.toSet()
            ?: emptySet()
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onCleared()
    }
}
