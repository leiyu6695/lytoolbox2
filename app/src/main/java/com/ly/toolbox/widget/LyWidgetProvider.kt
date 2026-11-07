package com.ly.toolbox.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.ly.toolbox.R
import com.ly.toolbox.data.Trigger
import com.ly.toolbox.service.ActionRunner

/**
 * 1x1 桌面小组件，点一下执行"桌面小组件"这条绑定。
 */
class LyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_ly)
            views.setOnClickPendingIntent(R.id.widgetRoot, clickIntent(context))
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_CLICK) {
            ActionRunner.run(context, Trigger.WIDGET)
        }
    }

    private fun clickIntent(context: Context): PendingIntent {
        val intent = Intent(context, LyWidgetProvider::class.java).setAction(ACTION_CLICK)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_CLICK = "com.ly.toolbox.WIDGET_CLICK"
    }
}
