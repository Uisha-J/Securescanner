package com.example.securescanner

import android.media.projection.MediaProjection

object MediaProjectionHolder {
    var mediaProjection: MediaProjection? = null
        private set

    @Volatile
    private var isReady = false

    fun setProjection(projection: MediaProjection?) {
        mediaProjection = projection
        isReady = projection != null
    }

    fun clear() {
        mediaProjection?.stop()
        mediaProjection = null
        isReady = false
    }

    /**
     * Clear the MediaProjection reference without stopping it
     * Used when MediaProjection has already been stopped by the system
     */
    fun clearWithoutStopping() {
        mediaProjection = null
        isReady = false
    }

    fun isReady(): Boolean = isReady && mediaProjection != null
}
