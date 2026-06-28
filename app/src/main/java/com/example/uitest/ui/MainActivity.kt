package com.example.uitest.ui

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import com.example.uitest.viewmodel.DashboardViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {

            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    viewModel.cameraManager.startSupportedCameras(
                        this@MainActivity
                    )
                }
            }

            LaunchedEffect(Unit) {
                val status = ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.CAMERA)
                if (status == PackageManager.PERMISSION_GRANTED) {
                    viewModel.cameraManager.startSupportedCameras(
                        this@MainActivity
                    )
                } else {
                    launcher.launch(android.Manifest.permission.CAMERA)
                }
                viewModel.loadLayout()
            }

            DashboardPager(presets = viewModel.statePresets)
        }
    }
}