package com.filemanagerplus.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

class MediaPlaybackController(context: Context) {
    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()

    fun play(uri: Uri) {
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.play()
    }

    fun release() = player.release()
}
