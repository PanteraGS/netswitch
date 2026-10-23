package com.netswitch.widget

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Плитка в шторке: нажатие переключает 3G ⇄ 4G. */
class NetTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        show(NetController.cachedMode(this))
    }

    override fun onClick() {
        val target = if (NetController.cachedMode(this) == NetMode.G3) NetMode.G4 else NetMode.G3
        qsTile?.apply {
            state = Tile.STATE_UNAVAILABLE
            subtitle = getString(R.string.status_switching)
            updateTile()
        }
        scope.launch {
            try {
                show(NetController.switchTo(this@NetTileService, target))
            } catch (e: Throwable) {
                Toast.makeText(this@NetTileService, e.message ?: e.toString(), Toast.LENGTH_LONG).show()
                show(NetController.cachedMode(this@NetTileService))
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun show(mode: NetMode?) {
        val tile = qsTile ?: return
        tile.label = getString(R.string.tile_name)
        tile.subtitle = mode?.label ?: "—"
        tile.state = if (mode == NetMode.G3) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.updateTile()
    }

    companion object {
        fun requestUpdate(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, NetTileService::class.java)) }
        }
    }
}
