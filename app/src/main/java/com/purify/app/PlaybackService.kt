package com.purify.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Owns the player so recitation keeps playing with the screen off and shows
 * lock-screen / notification controls.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val dataSourceFactory = ResolvingDataSource.Factory(
            DefaultDataSource.Factory(this),
            SurahResolver(applicationContext),
        )
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player).setSessionActivity(openApp).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}

/**
 * Every queue item points at the CDN. At load time we swap in the downloaded
 * copy when there is one (so it plays offline), otherwise a bitrate that exists.
 */
@OptIn(UnstableApi::class)
class SurahResolver(private val ctx: Context) : ResolvingDataSource.Resolver {
    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (uri.host != AudioSource.HOST) return dataSpec
        val segments = uri.pathSegments
        if (segments.size < 2) return dataSpec
        val surah = segments.last().removeSuffix(".mp3").toIntOrNull() ?: return dataSpec
        val edition = segments[segments.size - 2]

        val local = AudioSource.localFile(ctx, edition, surah)
        if (local.exists()) return dataSpec.withUri(Uri.fromFile(local))

        val bitrate = AudioSource.resolveBitrate(ctx, edition, surah)
        return dataSpec.withUri(Uri.parse(AudioSource.url(edition, bitrate, surah)))
    }
}
