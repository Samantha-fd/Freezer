package com.juneapps.freezer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FreezerViewModel(application: Application) : AndroidViewModel(application) {

    private val appRepository = AppRepository(application)
    private val freezeRepository = FreezeRepository(application)

    private val frozenAppsStore = FrozenAppsStore(application)
    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _selectedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val selectedApps: StateFlow<List<InstalledApp>> = _selectedApps.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _shizukuStatus = MutableStateFlow(ShizukuStatus.UNKNOWN)
    val shizukuStatus: StateFlow<ShizukuStatus> = _shizukuStatus.asStateFlow()

    private val _lastResult = MutableStateFlow<UiMessage?>(null)
    val lastResult: StateFlow<UiMessage?> = _lastResult.asStateFlow()

    private val _showOnboarding = MutableStateFlow(false)
    val showOnboarding: StateFlow<Boolean> = _showOnboarding.asStateFlow()

    enum class ShizukuStatus {
        UNKNOWN, NOT_RUNNING, NEEDS_PERMISSION, READY
    }

    sealed class UiMessage {
        data class Info(val text: String) : UiMessage()
        data class Error(val text: String) : UiMessage()
    }

    private val restoreJob = viewModelScope.launch { restoreFrozenApps() }

    init {
        _showOnboarding.value = !frozenAppsStore.isOnboardingComplete()
    }

    private suspend fun restoreFrozenApps() {
        kotlinx.coroutines.coroutineScope {
            val records = withContext(Dispatchers.IO) {
                frozenAppsStore.loadFrozenApps()
            }
            val packageManager = getApplication<Application>().packageManager
            val restored = records.mapNotNull { record ->
                try {
                    val info = packageManager.getApplicationInfo(
                        record.packageName, android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS
                    )
                    InstalledApp(
                        packageName = record.packageName,
                        appName = record.appName,
                        icon = packageManager.getApplicationIcon(info),
                        isSystemApp = false
                    )
                } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                    null
                }
            }
            _selectedApps.value = restored

        }
    }

    fun checkShizukuStatus() {
        viewModelScope.launch {
            val ready = withContext(Dispatchers.IO) { freezeRepository.isShizukuReady() }
            if (!ready) {
                _shizukuStatus.value = ShizukuStatus.NOT_RUNNING
                return@launch
            }
            val permission = withContext(Dispatchers.IO) { freezeRepository.hasPermission() }
            _shizukuStatus.value = if (permission) ShizukuStatus.READY else ShizukuStatus.NEEDS_PERMISSION
            if (permission) {
                restoreJob.join()
                reconcileFrozenApps()
            }
        }
    }

    private suspend fun reconcileFrozenApps() {
        val records = frozenAppsStore.loadFrozenApps()
        val pm = getApplication<Application>().packageManager
        for (record in records) {
            val frozen = withContext(Dispatchers.IO) {
                try {
                    if (record.method == "suspended") freezeRepository.isSuspended(record.packageName)
                    else pm.getApplicationEnabledSetting(record.packageName) == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                } catch (_: Exception) { null }
            }
            // A query error is not evidence that an app was unfrozen.
            if (frozen == false && frozenAppsStore.loadFrozenApps().contains(record)) {
                frozenAppsStore.removeFrozenApp(record.packageName)
                _selectedApps.value = _selectedApps.value.filterNot { it.packageName == record.packageName }
            }
        }
        ShortcutTarget.refresh(getApplication())
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            _isLoading.value = true
            val apps = withContext(Dispatchers.IO) {
                appRepository.getInstalledApps()
            }
            _installedApps.value = apps
            _isLoading.value = false
        }
    }

    private val _fallbackApp = MutableStateFlow<InstalledApp?>(null)
    val fallbackApp: StateFlow<InstalledApp?> = _fallbackApp.asStateFlow()
    private val _failedAppIds = MutableStateFlow<Set<String>>(emptySet())
    val failedAppIds: StateFlow<Set<String>> = _failedAppIds.asStateFlow()
    private val _isFreezing = MutableStateFlow(false)
    val isFreezing: StateFlow<Boolean> = _isFreezing.asStateFlow()
    private val fallbackQueue = ArrayDeque<InstalledApp>()

    fun selectApps(apps: List<InstalledApp>) {
        if (_isFreezing.value || _fallbackApp.value != null) return
        viewModelScope.launch {
            _isFreezing.value = true
            _failedAppIds.value = emptySet()
            var successCount = 0
            val errors = mutableListOf<String>()
            for (app in apps) {
                if (_selectedApps.value.any { it.packageName == app.packageName }) continue
                if (frozenAppsStore.loadFrozenApps().size >= 16) {
                    _failedAppIds.value += app.packageName
                    continue
                }
                when (val result = withContext(Dispatchers.IO) { freezeRepository.freezeApp(app.packageName) }) {
                    is FreezeRepository.FreezeResult.Success -> {
                        recordFrozen(app, "suspended")
                        successCount++
                    }
                    is FreezeRepository.FreezeResult.Error -> {
                        _failedAppIds.value += app.packageName
                        fallbackQueue.addLast(app)
                    }
                    else -> {
                        _failedAppIds.value += app.packageName
                        val reason = when (result) {
                            is FreezeRepository.FreezeResult.Indeterminate -> result.message
                            is FreezeRepository.FreezeResult.PermissionDenied -> "Shizuku permission required"
                            is FreezeRepository.FreezeResult.ShizukuNotRunning -> "Shizuku is not running"
                            else -> "Unable to freeze"
                        }
                        errors += "${app.appName}: $reason"
                    }
                }
            }
            _isFreezing.value = false
            _lastResult.value = if (errors.isNotEmpty()) UiMessage.Error("$successCount frozen. ${errors.joinToString("; ")}")
                else if (successCount > 0) UiMessage.Info("$successCount app(s) frozen") else null
            showNextFallback()
        }
    }

    private fun showNextFallback() {
        _fallbackApp.value = if (fallbackQueue.isEmpty()) null else fallbackQueue.removeFirst()
    }

    fun resolveFallback(disable: Boolean) {
        val app = _fallbackApp.value ?: return
        _fallbackApp.value = null
        viewModelScope.launch {
            if (disable && frozenAppsStore.loadFrozenApps().size < 16) {
                val result = withContext(Dispatchers.IO) { freezeRepository.disableApp(app.packageName) }
                if (result is FreezeRepository.FreezeResult.Success) {
                    recordFrozen(app, "disabled")
                    _failedAppIds.value -= app.packageName
                    _lastResult.value = UiMessage.Info("${app.appName} frozen")
                } else handleFreezeResult(result, app, true)
            }
            showNextFallback()
        }
    }

    private fun recordFrozen(app: InstalledApp, method: String) {
        frozenAppsStore.addFrozenApp(FrozenAppRecord(app.packageName, app.appName, System.currentTimeMillis(), method))
        _selectedApps.value = _selectedApps.value + app
        ShortcutTarget.refresh(getApplication())
    }

    private val _shatteringAppIds = MutableStateFlow<Set<String>>(emptySet())
    val shatteringAppIds: StateFlow<Set<String>> = _shatteringAppIds.asStateFlow()

    fun removeApp(app: InstalledApp) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val method = frozenAppsStore.loadFrozenApps().firstOrNull { it.packageName == app.packageName }?.method ?: "disabled"
                freezeRepository.unfreezeApp(app.packageName, method)
            }
            if (result is FreezeRepository.FreezeResult.Success) {
                _shatteringAppIds.value = _shatteringAppIds.value + app.packageName
                frozenAppsStore.removeFrozenApp(app.packageName)
                ShortcutTarget.refresh(getApplication())
                _lastResult.value = UiMessage.Info("${app.appName} unfrozen")
            } else {
                handleFreezeResult(result, app, isFreeze = false)
            }
        }
    }

    fun unfreezeShortcut(packageName: String) {
        viewModelScope.launch {
            restoreJob.join()
            repeat(10) {
                if (!freezeRepository.isShizukuReady()) kotlinx.coroutines.delay(200)
            }
            val record = frozenAppsStore.loadFrozenApps().firstOrNull { it.packageName == packageName }
            if (record == null) {
                _lastResult.value = UiMessage.Error("That app is no longer frozen.")
                ShortcutTarget.refresh(getApplication())
                return@launch
            }
            val result = withContext(Dispatchers.IO) { freezeRepository.unfreezeApp(record.packageName, record.method) }
            if (result is FreezeRepository.FreezeResult.Success) {
                frozenAppsStore.removeFrozenApp(record.packageName)
                if (_selectedApps.value.any { it.packageName == record.packageName }) {
                    _shatteringAppIds.value = _shatteringAppIds.value + record.packageName
                } else {
                    _selectedApps.value = _selectedApps.value.filterNot { it.packageName == record.packageName }
                }
                _lastResult.value = UiMessage.Info("${record.appName} unfrozen")
                ShortcutTarget.refresh(getApplication())
            } else {
                _lastResult.value = UiMessage.Error(when (result) {
                    is FreezeRepository.FreezeResult.PermissionDenied -> "Shizuku permission required."
                    is FreezeRepository.FreezeResult.ShizukuNotRunning -> "Start Shizuku first."
                    is FreezeRepository.FreezeResult.Error -> result.message
                    is FreezeRepository.FreezeResult.Indeterminate -> result.message
                    else -> "Could not unfreeze app."
                })
            }
        }
    }

    fun completeShatter(packageName: String) {
        _selectedApps.value = _selectedApps.value.filter { it.packageName != packageName }
        _shatteringAppIds.value = _shatteringAppIds.value - packageName
    }


    private fun handleFreezeResult(
        result: FreezeRepository.FreezeResult,
        app: InstalledApp,
        isFreeze: Boolean
    ) {
        when (result) {
            is FreezeRepository.FreezeResult.Success -> {
                if (isFreeze) {
                    _selectedApps.value = _selectedApps.value + app
                    viewModelScope.launch(Dispatchers.IO) {
                        frozenAppsStore.addFrozenApp(
                            FrozenAppRecord(
                                packageName = app.packageName,
                                appName = app.appName,
                                frozenAt = System.currentTimeMillis()
                            )
                        )
                    }
                    _lastResult.value = UiMessage.Info("${app.appName} frozen")
                } else {
                    _selectedApps.value = _selectedApps.value.filter {
                        it.packageName != app.packageName
                    }
                    viewModelScope.launch(Dispatchers.IO) {
                        frozenAppsStore.removeFrozenApp(app.packageName)
                    }
                    _lastResult.value = UiMessage.Info("${app.appName} unfrozen")
                }
            }
            is FreezeRepository.FreezeResult.ShizukuNotRunning -> {
                _shizukuStatus.value = ShizukuStatus.NOT_RUNNING
                _lastResult.value = UiMessage.Error("Shizuku is not running. Please start it first.")
            }
            is FreezeRepository.FreezeResult.PermissionDenied -> {
                _shizukuStatus.value = ShizukuStatus.NEEDS_PERMISSION
                _lastResult.value = UiMessage.Error("Shizuku permission required.")
            }
            is FreezeRepository.FreezeResult.Error -> {
                _lastResult.value = UiMessage.Error(result.message)
            }
            is FreezeRepository.FreezeResult.Indeterminate -> {
                _lastResult.value = UiMessage.Error(result.message)
            }
        }
    }

    fun clearLastResult() {
        _lastResult.value = null
    }

    fun dismissOnboarding() {
        viewModelScope.launch(Dispatchers.IO) {
            frozenAppsStore.markOnboardingComplete()
        }
        _showOnboarding.value = false
    }
}