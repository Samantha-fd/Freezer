package com.juneapps.freezer

import android.os.RemoteException
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

class FreezeRepository(private val context: android.content.Context) {

    sealed class FreezeResult {
        data object Success : FreezeResult()
        data object PermissionDenied : FreezeResult()
        data object ShizukuNotRunning : FreezeResult()
        data class Error(val message: String) : FreezeResult()
        data class Indeterminate(val message: String) : FreezeResult()
    }

    fun isShizukuReady(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Exception) {
            false
        }
    }

    fun hasPermission(): Boolean {
        if (!isShizukuReady()) return false
        return try {
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    fun freezeApp(packageName: String): FreezeResult = suspendApp(packageName, true)

    fun disableApp(packageName: String): FreezeResult = setEnabledState(packageName, false)

    fun unfreezeApp(packageName: String, method: String = "disabled"): FreezeResult =
        if (method == "suspended") suspendApp(packageName, false) else setEnabledState(packageName, true)

    fun isSuspended(packageName: String): Boolean {
        val info = context.packageManager.getApplicationInfo(packageName, android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS)
        return info.flags and android.content.pm.ApplicationInfo.FLAG_SUSPENDED != 0
    }

    private fun suspendApp(packageName: String, suspend: Boolean): FreezeResult {
        if (!isShizukuReady()) return FreezeResult.ShizukuNotRunning
        if (!hasPermission()) return FreezeResult.PermissionDenied
        if (!packageName.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))) {
            return FreezeResult.Error("Invalid package name")
        }
        var safeToOfferFallback = false
        return try {
            if (suspend && isSuspended(packageName)) {
                return FreezeResult.Indeterminate("This app is already suspended outside Freezer.")
            }
            safeToOfferFallback = suspend
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
            ).apply { isAccessible = true }
            val userId = android.os.Process.myUid() / 100000
            val command = arrayOf("pm", if (suspend) "suspend" else "unsuspend", "--user", userId.toString(), packageName)
            safeToOfferFallback = false
            val process = method.invoke(null, command, null, null) as Process
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val errors = process.errorStream.bufferedReader().use { it.readText() }
            val exit = process.waitFor()
            val actual = isSuspended(packageName)
            if (actual == suspend) FreezeResult.Success
            else FreezeResult.Error((errors.ifBlank { output }).ifBlank { "Could not change suspension state (exit $exit)" }.trim())
        } catch (e: Exception) {
            if (safeToOfferFallback) FreezeResult.Error("Suspension is unavailable: ${e.cause?.message ?: e.message}")
            else FreezeResult.Indeterminate("Could not verify suspension state: ${e.cause?.message ?: e.message}. Check the app before trying again.")
        }
    }

    private fun setEnabledState(packageName: String, enabled: Boolean): FreezeResult {
        if (!isShizukuReady()) return FreezeResult.ShizukuNotRunning
        if (!hasPermission()) return FreezeResult.PermissionDenied

        return try {
            val newState = if (enabled) {
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
            }

            val packageManagerClass = Class.forName("android.content.pm.IPackageManager")
            val stubClass = Class.forName("android.content.pm.IPackageManager\$Stub")

            val rawBinder = SystemServiceHelper.getSystemService("package")
                ?: return FreezeResult.Error("Could not access package service")

            val wrappedBinder = ShizukuBinderWrapper(rawBinder)

            val asInterfaceMethod = stubClass.getMethod("asInterface", android.os.IBinder::class.java)
            val packageManagerProxy = asInterfaceMethod.invoke(null, wrappedBinder)

            val setEnabledMethod = packageManagerClass.getMethod(
                "setApplicationEnabledSetting",
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java
            )

            setEnabledMethod.invoke(
                packageManagerProxy,
                packageName,
                newState,
                0,
                0,
                "com.android.shell"
            )

            FreezeResult.Success
        } catch (e: RemoteException) {
            FreezeResult.Error("Shizuku call failed: ${e.message}")
        } catch (e: SecurityException) {
            FreezeResult.PermissionDenied
        } catch (e: Exception) {
            FreezeResult.Error("Unexpected error: ${e.message}")
        }
    }

    fun isAppFrozen(packageName: String): Boolean {
        if (!isShizukuReady() || !hasPermission()) return false
        return try {
            val packageManagerClass = Class.forName("android.content.pm.IPackageManager")
            val stubClass = Class.forName("android.content.pm.IPackageManager\$Stub")

            val rawBinder = SystemServiceHelper.getSystemService("package") ?: return false
            val wrappedBinder = ShizukuBinderWrapper(rawBinder)

            val asInterfaceMethod = stubClass.getMethod("asInterface", android.os.IBinder::class.java)
            val packageManagerProxy = asInterfaceMethod.invoke(null, wrappedBinder)

            val getEnabledMethod = packageManagerClass.getMethod(
                "getApplicationEnabledSetting",
                String::class.java,
                Int::class.javaPrimitiveType
            )

            val state = getEnabledMethod.invoke(packageManagerProxy, packageName, 0) as Int
            state == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
        } catch (_: Exception) {
            false
        }
    }
}