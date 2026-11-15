package com.example.sleeptile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.text.format.DateFormat
import java.util.Date

class SleepTileService : TileService() {

    override fun onStartListening() {
        update()
    }

    override fun onClick() {
        if (SleepTimer.isRunning(this)) {
            SleepTimer.cancel(this)
        } else {
            SleepTimer.start(this, SleepTimer.defaultMinutes(this))
        }
        update()
    }

    private fun update() {
        val tile = qsTile ?: return
        val end = SleepTimer.endAt(this)
        if (end != 0L) {
            tile.state = Tile.STATE_ACTIVE
            tile.subtitle = "до " + DateFormat.getTimeFormat(this).format(Date(end))
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.subtitle = "${SleepTimer.defaultMinutes(this)} мин"
        }
        tile.updateTile()
    }
}
