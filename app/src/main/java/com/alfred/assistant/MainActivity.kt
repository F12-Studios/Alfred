package com.alfred.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.alfred.assistant.service.AlfredService
import com.alfred.assistant.ui.AlfredScreen
import com.alfred.assistant.ui.theme.AlfredTheme

class MainActivity : ComponentActivity() {

    companion object {
        private val REQUIRED_PERMISSIONS = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.READ_CONTACTS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AlfredTheme {
                var allPermissionsGranted by remember {
                    mutableStateOf(hasAllPermissions())
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val allGranted = permissions.values.all { it }
                    allPermissionsGranted = allGranted
                    if (allGranted) {
                        AlfredService.start(this@MainActivity)
                    } else {
                        val denied = permissions.filter { !it.value }.keys
                        Toast.makeText(
                            this@MainActivity,
                            "Alfred needs these permissions to function: ${denied.joinToString(", ") { it.substringAfterLast('.') }}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                LaunchedEffect(Unit) {
                    if (!allPermissionsGranted) {
                        permissionLauncher.launch(REQUIRED_PERMISSIONS)
                    } else {
                        AlfredService.start(this@MainActivity)
                    }
                }

                AlfredScreen(
                    permissionsGranted = allPermissionsGranted,
                    onRequestPermissions = {
                        permissionLauncher.launch(REQUIRED_PERMISSIONS)
                    }
                )
            }
        }
    }

    private fun hasAllPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
}
