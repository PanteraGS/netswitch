package com.netswitch.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch

class NetWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, NetController.cachedMode(context), null)
        if (NetController.shizukuState(context) == ShizukuState.READY) {
            val pending = goAsync()
            scope.launch {
                try {
                    NetController.refresh(context)
                } finally {
                    pending.finish()
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SWITCH) {
            super.onReceive(context, intent)
            return
        }
        val target = NetMode.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_MODE) } ?: return
        val pending = goAsync()
        scope.launch {
            try {
                render(context, target, context.getString(R.string.status_switching), busy = true)
                val mode = NetController.switchTo(context, target)
                render(context, mode, null)
            } catch (e: Throwable) {
                val message = when (e) {
                    is NetException -> e.message
                    is TimeoutCancellationException -> context.getString(R.string.error_timeout)
                    else -> e.message ?: e.toString()
                }
                render(context, NetController.cachedMode(context), message, error = true)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_SWITCH = "com.netswitch.widget.SWITCH"
        private const val EXTRA_MODE = "mode"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        fun updateAll(context: Context) = render(context, NetController.cachedMode(context), null)

        private fun render(
            context: Context,
            mode: NetMode?,
            status: String?,
            busy: Boolean = false,
            error: Boolean = false,
        ) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NetWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val views = RemoteViews(context.packageName, R.layout.widget_net)
            views.setTextViewText(R.id.mode_big, mode?.label ?: "—")
            views.setTextViewText(
                R.id.status,
                status ?: when (mode) {
                    null -> context.getString(R.string.status_unknown)
                    NetMode.G5 -> context.getString(R.string.status_5g)
                    else -> context.getString(R.string.status_active)
                },
            )
            views.setTextColor(
                R.id.status,
                context.getColor(if (error) R.color.widget_error else R.color.widget_text_dim),
            )
            views.setInt(
                R.id.dot, "setBackgroundResource",
                when {
                    error -> R.drawable.dot_error
                    busy -> R.drawable.dot_busy
                    else -> R.drawable.dot_ok
                },
            )

            pill(context, views, R.id.btn_3g, NetMode.G3, mode == NetMode.G3)
            pill(context, views, R.id.btn_4g, NetMode.G4, mode == NetMode.G4)

            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.header, open)

            manager.updateAppWidget(ids, views)
        }

        private fun pill(context: Context, views: RemoteViews, id: Int, mode: NetMode, selected: Boolean) {
            views.setInt(id, "setBackgroundResource", if (selected) R.drawable.pill_on else R.drawable.pill_off)
            views.setTextColor(id, context.getColor(if (selected) R.color.pill_text_on else R.color.pill_text_off))
            val intent = Intent(context, NetWidgetProvider::class.java)
                .setAction(ACTION_SWITCH)
                .putExtra(EXTRA_MODE, mode.name)
            views.setOnClickPendingIntent(
                id,
                PendingIntent.getBroadcast(
                    context, mode.ordinal + 1, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        }
    }
}
