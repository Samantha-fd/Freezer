package com.juneapps.freezer

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.hardware.biometrics.BiometricPrompt
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.juneapps.freezer.ui.theme.FreezerTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private val viewModel: FreezerViewModel by viewModels()
    private val prefs by lazy { getSharedPreferences("freezer_security", Context.MODE_PRIVATE) }
    private var pendingShortcut by mutableStateOf<String?>(null)
    private var locked by mutableStateOf(false)
    private var lockEnabled by mutableStateOf(false)
    private var authInProgress = false
    private var authAction: (() -> Unit)? = null
    private var authMessage by mutableStateOf<String?>(null)
    private val credentials = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        authInProgress = false
        if (result.resultCode == RESULT_OK) authAction?.invoke() else authMessage = "Authentication cancelled."
        authAction = null
    }
    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, _ -> viewModel.checkShizukuStatus() }
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { viewModel.checkShizukuStatus() }
    private val binderDeadListener = Shizuku.OnBinderDeadListener { viewModel.checkShizukuStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lockEnabled = prefs.getBoolean("enabled", false)
        locked = lockEnabled
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        setContent {
            FreezerTheme {
                LaunchedEffect(locked, pendingShortcut) {
                    if (!locked) pendingShortcut?.let {
                        pendingShortcut = null
                        viewModel.unfreezeShortcut(it)
                    }
                }
                if (locked) {
                    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                        Text("Freezer is locked", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(12.dp))
                        authMessage?.let { Text(it) }
                        Button(onClick = { authenticate { locked = false } }) { Text("Unlock Freezer") }
                    }
                } else {
                    GridScreen(viewModel = viewModel, lockEnabled = lockEnabled, onToggleLock = {
                        authenticate {
                            lockEnabled = !lockEnabled
                            prefs.edit().putBoolean("enabled", lockEnabled).apply()
                            locked = false
                        }
                    })
                }
            }
        }
    }

    private fun authenticate(onSuccess: () -> Unit) {
        if (authInProgress) return
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isDeviceSecure) {
            authMessage = "Set a device screen lock in Android settings first."
            android.widget.Toast.makeText(this, authMessage, android.widget.Toast.LENGTH_LONG).show()
            return
        }
        authMessage = null
        authInProgress = true
        authAction = onSuccess
        if (Build.VERSION.SDK_INT >= 30) {
            val prompt = BiometricPrompt.Builder(this)
                .setTitle("Unlock Freezer")
                .setAllowedAuthenticators(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG or android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()
            prompt.authenticate(CancellationSignal(), mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authInProgress = false
                    authAction?.invoke()
                    authAction = null
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    authInProgress = false
                    authAction = null
                    authMessage = errString.toString()
                }
            })
        } else {
            @Suppress("DEPRECATION")
            val intent = keyguard.createConfirmDeviceCredentialIntent("Unlock Freezer", "Confirm your screen lock")
            if (intent == null) {
                authInProgress = false
                authAction = null
                authMessage = "Device authentication is unavailable."
            } else credentials.launch(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        ShortcutTarget.pending?.let { pendingShortcut = it; ShortcutTarget.pending = null }
        viewModel.checkShizukuStatus()
    }
    override fun onStop() { super.onStop(); if (lockEnabled) locked = true }
    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
    }
}
