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

    private val repository = AppRepository(application)

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _selectedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val selectedApps: StateFlow<List<InstalledApp>> = _selectedApps.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadInstalledApps() {
        viewModelScope.launch {
            _isLoading.value = true
            val apps = withContext(Dispatchers.IO) {
                repository.getInstalledApps()
            }
            _installedApps.value = apps
            _isLoading.value = false
        }
    }

    fun selectApp(app: InstalledApp) {
        val current = _selectedApps.value
        if (current.any { it.packageName == app.packageName }) return
        if (current.size >= 16) return
        _selectedApps.value = current + app
    }

    fun removeApp(app: InstalledApp) {
        _selectedApps.value = _selectedApps.value.filter {
            it.packageName != app.packageName
        }
    }
}