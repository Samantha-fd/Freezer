package com.juneapps.freezer

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

object ShortcutTarget {
    var pending: String? = null
    const val EXTRA_PACKAGE = "target_package"
    const val PREFS = "shortcut_targets"
    fun widgetKey(id: Int) = "widget_$id"
    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, FreezerWidget::class.java))
        FreezerWidget.update(context, manager, ids)
    }
}

class ShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShortcutTarget.pending = intent.getStringExtra(ShortcutTarget.EXTRA_PACKAGE)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }
}

class FreezerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = update(context, manager, ids)
    override fun onDeleted(context: Context, ids: IntArray) {
        val prefs = context.getSharedPreferences(ShortcutTarget.PREFS, Context.MODE_PRIVATE)
        ids.forEach { prefs.edit().remove(ShortcutTarget.widgetKey(it)).apply() }
    }
    companion object {
        fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val prefs = context.getSharedPreferences(ShortcutTarget.PREFS, Context.MODE_PRIVATE)
            val records = FrozenAppsStore(context).loadFrozenApps()
            ids.forEach { id ->
                val target = prefs.getString(ShortcutTarget.widgetKey(id), null)
                val record = records.firstOrNull { it.packageName == target }
                val views = RemoteViews(context.packageName, R.layout.freezer_widget)
                views.setTextViewText(R.id.widget_text, record?.let { "${it.appName}\nFrozen · tap to unfreeze" } ?: "Open Freezer")
                val icon = try {
                    record?.let { context.packageManager.getApplicationIcon(it.packageName) }
                } catch (_: android.content.pm.PackageManager.NameNotFoundException) { null }
                views.setImageViewBitmap(R.id.widget_icon, (icon ?: context.applicationInfo.loadIcon(context.packageManager)).toBitmap(96, 96))
                run {
                    val intent = Intent(context, ShortcutActivity::class.java).putExtra(ShortcutTarget.EXTRA_PACKAGE, record?.packageName)
                    val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    views.setOnClickPendingIntent(R.id.widget_root, pending)
                }
                manager.updateAppWidget(id, views)
            }
        }
    }
}

class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID || info?.provider != ComponentName(this, FreezerWidget::class.java)) { finish(); return }
        val records = FrozenAppsStore(this).loadFrozenApps()
        setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().padding(20.dp)) {
                    Text("Choose a frozen app", style = MaterialTheme.typography.titleLarge)
                    if (records.isEmpty()) Text("Freeze an app in Freezer first.")
                    LazyColumn { items(records) { record ->
                        Row(Modifier.fillMaxWidth().clickable {
                            getSharedPreferences(ShortcutTarget.PREFS, Context.MODE_PRIVATE).edit().putString(ShortcutTarget.widgetKey(id), record.packageName).apply()
                            FreezerWidget.update(this@WidgetConfigActivity, AppWidgetManager.getInstance(this@WidgetConfigActivity), intArrayOf(id))
                            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                            finish()
                        }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            val icon = remember(record.packageName) {
                                try { packageManager.getApplicationIcon(record.packageName).toBitmap(96, 96).asImageBitmap() }
                                catch (_: android.content.pm.PackageManager.NameNotFoundException) { null }
                            }
                            if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(40.dp).padding(end = 8.dp))
                            Text(record.appName)
                        }
                    } }
                }
            }
        }
    }
}
