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
    private val freezeRepository = FreezeRepository()

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

    init {
        _showOnboarding.value = !frozenAppsStore.isOnboardingComplete()
        restoreFrozenApps()
    }

    private fun restoreFrozenApps() {
        viewModelScope.launch {
            val records = withContext(Dispatchers.IO) {
                frozenAppsStore.loadFrozenApps()
            }
            val packageManager = getApplication<Application>().packageManager
            val restored = records.mapNotNull { record ->
                try {
                    val info = packageManager.getApplicationInfo(record.packageName, 0)
                    val isActuallyFrozen = withContext(Dispatchers.IO) {
                        freezeRepository.isAppFrozen(record.packageName)
                    }
                    if (!isActuallyFrozen) return@mapNotNull null
                    InstalledApp(
                        packageName = record.packageName,
                        appName = record.appName,
                        icon = packageManager.getApplicationIcon(info),
                        isSystemApp = false
                    )
                } catch (_: Exception) {
                    null
                }
            }
            _selectedApps.value = restored
            val survivingPackages = restored.map { it.packageName }.toSet()
            withContext(Dispatchers.IO) {
                val updatedRecords = records.filter { it.packageName in survivingPackages }
                frozenAppsStore.saveFrozenApps(updatedRecords)
            }
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
        }
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

    fun selectApp(app: InstalledApp) {
        val current = _selectedApps.value
        if (current.any { it.packageName == app.packageName }) return
        if (current.size >= 16) return

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                freezeRepository.freezeApp(app.packageName)
            }
            handleFreezeResult(result, app, isFreeze = true)
        }
    }

    private val _shatteringAppIds = MutableStateFlow<Set<String>>(emptySet())
    val shatteringAppIds: StateFlow<Set<String>> = _shatteringAppIds.asStateFlow()

    fun removeApp(app: InstalledApp) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                freezeRepository.unfreezeApp(app.packageName)
            }
            if (result is FreezeRepository.FreezeResult.Success) {
                _shatteringAppIds.value = _shatteringAppIds.value + app.packageName
                viewModelScope.launch(Dispatchers.IO) {
                    frozenAppsStore.removeFrozenApp(app.packageName)
                }
                _lastResult.value = UiMessage.Info("${app.appName} unfrozen")
            } else {
                handleFreezeResult(result, app, isFreeze = false)
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