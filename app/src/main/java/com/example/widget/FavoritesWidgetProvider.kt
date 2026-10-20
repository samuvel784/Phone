/*
 * Copyright (C) 2026 Nova Dialer contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.WidgetCallActivity
import org.json.JSONArray

/**
 * Home-screen widget that mirrors the user's favourite contacts.
 *
 * The app writes a small JSON snapshot to SharedPreferences whenever favourites
 * change (see DialerViewModel.updateWidgetFavorites); this provider only renders
 * that snapshot so it never touches the database from the launcher process.
 *
 * Tapping a row deep-links into the non-exported WidgetCallActivity, which places
 * the call when CALL_PHONE is granted, otherwise prefills the dialpad.
 */
class FavoritesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) renderWidget(context, appWidgetManager, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            refreshAll(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.aistudio.novadial.widget.REFRESH"
        const val ACTION_WIDGET_CALL = "com.aistudio.novadial.widget.CALL"
        const val EXTRA_WIDGET_CALL_NUMBER = "widget_call_number"
        const val EXTRA_WIDGET_CALL_NAME = "widget_call_name"

        const val PREFS = "dialer_prefs"
        const val KEY_SNAPSHOT = "widget_favorites_snapshot"

        private const val MAX_ROWS = 6

        /** Re-renders every placed widget from the stored snapshot. */
        fun refreshAll(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, FavoritesWidgetProvider::class.java)
                )
                for (id in ids) renderWidget(context, manager, id)
            } catch (_: Exception) {
                // Widget manager may be unavailable while the device is booting
            }
        }

        private fun renderWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_favorites)
            val rows = readSnapshot(context)
            val listId = R.id.widget_favorites_list
            views.removeAllViews(listId)

            val count = minOf(rows.size, MAX_ROWS)
            for (i in 0 until count) {
                val (name, number) = rows[i]
                val row = RemoteViews(context.packageName, R.layout.widget_favorites_row)
                row.setTextViewText(R.id.widget_row_initials, initials(name))
                row.setTextViewText(R.id.widget_row_name, name.ifBlank { number })
                row.setTextViewText(R.id.widget_row_number, number)
                row.setOnClickPendingIntent(
                    R.id.widget_row_root,
                    callPending(context, widgetId, i, number, name)
                )
                views.addView(listId, row)
            }

            views.setViewVisibility(
                R.id.widget_favorites_empty,
                if (count == 0) View.VISIBLE else View.GONE
            )
            views.setOnClickPendingIntent(R.id.widget_favorites_header, openAppPending(context))
            views.setOnClickPendingIntent(R.id.widget_favorites_refresh, refreshPending(context))

            manager.updateAppWidget(widgetId, views)
        }

        private fun readSnapshot(context: Context): List<Pair<String, String>> {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_SNAPSHOT, "[]") ?: "[]"
            return try {
                val arr = JSONArray(raw)
                (0 until arr.length()).mapNotNull { i ->
                    val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                    val number = obj.optString("v", "")
                    if (number.isBlank()) null else (obj.optString("n", "") to number)
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

        private fun initials(name: String): String {
            val parts = name.trim().split(" ").filter { it.isNotBlank() }
            if (parts.isEmpty()) return "#"
            val first = parts[0].first().uppercaseChar()
            val second = if (parts.size > 1) parts[1].first().uppercaseChar() else null
            return if (second != null) "$first$second" else first.toString()
        }

        private fun callPending(
            context: Context,
            widgetId: Int,
            index: Int,
            number: String,
            name: String
        ): PendingIntent {
            val intent = Intent(context, WidgetCallActivity::class.java).apply {
                action = ACTION_WIDGET_CALL
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_WIDGET_CALL_NUMBER, number)
                putExtra(EXTRA_WIDGET_CALL_NAME, name)
            }
            return PendingIntent.getActivity(context, widgetId * 100 + index, intent, pendingFlags())
        }

        private fun openAppPending(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            return PendingIntent.getActivity(context, 100000, intent, pendingFlags())
        }

        private fun refreshPending(context: Context): PendingIntent {
            val intent = Intent(context, FavoritesWidgetProvider::class.java)
                .setAction(ACTION_REFRESH)
            return PendingIntent.getBroadcast(context, 100001, intent, pendingFlags())
        }

        private fun pendingFlags(): Int =
            // minSdk 24 > M (23); FLAG_IMMUTABLE is always available.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}
