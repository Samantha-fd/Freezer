package com.juneapps.freezer

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@Serializable
data class FrozenAppRecord(
    val packageName: String,
    val appName: String,
    val frozenAt: Long
)

class FrozenAppsStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun loadFrozenApps(): List<FrozenAppRecord> {
        val raw = prefs.getString(KEY_FROZEN_APPS, null) ?: return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveFrozenApps(apps: List<FrozenAppRecord>) {
        val raw = json.encodeToString(apps)
        prefs.edit().putString(KEY_FROZEN_APPS, raw).apply()
    }

    fun addFrozenApp(record: FrozenAppRecord) {
        val current = loadFrozenApps().toMutableList()
        current.removeAll { it.packageName == record.packageName }
        current.add(record)
        saveFrozenApps(current)
    }

    fun removeFrozenApp(packageName: String) {
        val current = loadFrozenApps().filter { it.packageName != packageName }
        saveFrozenApps(current)
    }

    fun isOnboardingComplete(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_DONE, false)
    }

    fun markOnboardingComplete() {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
    }
    companion object {
        private const val PREFS_NAME = "freezer_prefs"
        private const val KEY_FROZEN_APPS = "frozen_apps_json"
        private const val KEY_ONBOARDING_DONE = "onboarding_complete"
    }
}