package de.mp3player.playback

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.MediaStore
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommands
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import de.mp3player.MainActivity

/**
 * Hintergrund-Wiedergabe: läuft weiter, wenn die App geschlossen ist,
 * und zeigt Steuerung in Benachrichtigung, Sperrbildschirm und auf Kopfhörern/Bluetooth.
 */
class PlaybackService : MediaSessionService() {

    companion object {
        /** Wählbare Abspielgeschwindigkeiten. */
        val SPEEDS = listOf(0.25f, 0.5f, 1f)
        private const val KEY_SPEED = "speed"

        /** Systemkomponenten, die Sperrbildschirm, Schnelleinstellungen und Bluetooth steuern. */
        private val SYSTEM_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.bluetooth",
            "com.google.android.bluetooth",
            MediaSession.ControllerInfo.LEGACY_CONTROLLER_PACKAGE_NAME,
        )

        fun audioCollection(): Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
    }

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // Pause beim Abziehen der Kopfhörer
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        // Zuletzt gewählte Abspielgeschwindigkeit wiederherstellen und Änderungen merken
        val prefs = getSharedPreferences("player", MODE_PRIVATE)
        val savedSpeed = prefs.getFloat(KEY_SPEED, 1f)
        if (savedSpeed in SPEEDS) player.setPlaybackSpeed(savedSpeed)
        player.addListener(object : Player.Listener {
            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                prefs.edit().putFloat(KEY_SPEED, playbackParameters.speed).apply()
            }
        })

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(SecureSessionCallback())
            .build()
    }

    /**
     * Sicherheit: Jede App auf dem Gerät kann sich mit einem Media-Dienst verbinden.
     * Volle Kontrolle (Warteschlange lesen/ändern, Titel-Infos) bekommen deshalb nur
     * vertrauenswürdige Steuerungen: diese App selbst, das System (Benachrichtigung,
     * Sperrbildschirm, Bluetooth) sowie Android Auto. Alle anderen Apps dürfen nur
     * Play/Pause/Weiter/Zurück – sie sehen nicht, was läuft, und können nichts einspielen.
     */
    private inner class SecureSessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            if (isTrustedController(session, controller)) {
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
            }
            val transportOnly = Player.Commands.Builder()
                .addAll(
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_STOP,
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                )
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(transportOnly)
                .setAvailableSessionCommands(SessionCommands.EMPTY)
                .build()
        }

        /**
         * Titel werden nie über eine mitgelieferte Datei-Adresse geladen, sondern nur über
         * ihre ID aus der Android-Musikbibliothek. So kann niemand dem Player fremde Dateien
         * oder Internet-Adressen unterschieben.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.mapNotNull { item ->
                val id = item.mediaId.toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                item.buildUpon()
                    .setUri(ContentUris.withAppendedId(audioCollection(), id))
                    .build()
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }
    }

    private fun isTrustedController(session: MediaSession, controller: MediaSession.ControllerInfo): Boolean {
        val pkg = controller.packageName
        return pkg == packageName ||
            pkg in SYSTEM_PACKAGES ||
            controller.uid == Process.SYSTEM_UID ||
            controller.uid == Process.myUid() ||
            session.isMediaNotificationController(controller) ||
            session.isAutoCompanionController(controller) ||
            session.isAutomotiveController(controller) ||
            controller.isTrusted
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
