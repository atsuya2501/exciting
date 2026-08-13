package com.atsuya.dailydashboard

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class QuickStartTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        when (TimerScheduler.status(this)) {
            "running" -> Unit
            "paused" -> TimerScheduler.resume(this)
            else -> TimerScheduler.schedulePreset(this, "work")
        }
        refreshTile()
    }

    private fun refreshTile() {
        val status = TimerScheduler.status(this)
        val focus = TimerScheduler.preferredFocus(this)
        qsTile?.apply {
            label = "Pomodoro"
            subtitle = if (status == "paused") "$focus・再開" else focus
            state = if (status == "running") Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
