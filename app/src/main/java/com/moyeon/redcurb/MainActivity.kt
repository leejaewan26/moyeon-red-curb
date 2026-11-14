package com.moyeon.redcurb

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.moyeon.redcurb.ui.RedCurbApp

class MainActivity : ComponentActivity() {
    private val vm: RedCurbViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val missing = RUNTIME_PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            try { requestPermissions(missing.toTypedArray(), 1) } catch (_: Exception) {}
        }
        setContent { RedCurbApp(vm) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        vm.restartLocation()
    }

    companion object {
        val RUNTIME_PERMISSIONS = listOf(
            "pleos.car.permission.FUSED_LOCATION",
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }
}
