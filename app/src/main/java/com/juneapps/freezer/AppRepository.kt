package com.juneapps.freezer

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

class AppRepository(private val context: Context) {

    private val neverFreezeList = setOf(
        // Google Play core — freezing breaks most apps
        "com.google.android.gms",
        "com.android.vending",
        "com.google.android.gsf",
        "com.google.android.package installer",

        // System UI and core device functions
        "com.android.system ui",
        "com.android.settings",
        "com.android.phone",
        "com.android.dialer",
        "com.android.contacts",
        "com.android.providers.contacts",
        "com.android.providers.telephony",

        // Keyboards — freezing these means you can't type
        "com.google.android.inputmethod.latin",
        "com.android.inputmethod.latin",

        // WebView — freezing breaks any app that displays web content
        "com.google.android.webview",
        "com.android.webview",

        // Chrome — also serves as system WebView provider on some devices
        "com.android.chrome"
    )

    fun getInstalledApps(): List<InstalledApp> {
        val packageManager = context.packageManager

        val allPackages = packageManager.getInstalledApplications(
            PackageManager.GET_META_DATA
        )

        return allPackages
            .filter { isUserApp(it) }
            .filter { it.packageName != context.packageName }
            .filter { it.packageName !in neverFreezeList }
            .filter { !isLauncher(it) }
            .map { appInfo ->
                InstalledApp(
                    packageName = appInfo.packageName,
                    appName = packageManager.getApplicationLabel(appInfo).toString(),
                    icon = packageManager.getApplicationIcon(appInfo),
                    isSystemApp = false
                )
            }
            .sortedBy { it.appName.lowercase() }
    }

    private fun isUserApp(appInfo: ApplicationInfo): Boolean {
        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        return !isSystem || isUpdatedSystem
    }

    private fun isLauncher(appInfo: ApplicationInfo): Boolean {
        val packageName = appInfo.packageName
        return packageName.contains("launcher", ignoreCase = true) ||
                packageName.contains("home", ignoreCase = true)
    }
}