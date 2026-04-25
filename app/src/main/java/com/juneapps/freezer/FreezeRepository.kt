package com.juneapps.freezer

import android.os.RemoteException
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

class FreezeRepository {

    sealed class FreezeResult {
        data object Success : FreezeResult()
        data object PermissionDenied : FreezeResult()
        data object ShizukuNotRunning : FreezeResult()
        data class Error(val message: String) : FreezeResult()
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

    fun freezeApp(packageName: String): FreezeResult {
        return setEnabledState(packageName, enabled = false)
    }

    fun unfreezeApp(packageName: String): FreezeResult {
        return setEnabledState(packageName, enabled = true)
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