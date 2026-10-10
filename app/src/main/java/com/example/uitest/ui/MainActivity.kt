package com.example.uitest.ui

import android.Manifest
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.uitest.viewmodel.DashboardViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        setContent {

            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { _ -> }

            LaunchedEffect(Unit) {
                viewModel.cameraManager.setLifecycleOwner(this@MainActivity)
                val status = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA)
                if (status != PackageManager.PERMISSION_GRANTED) {
                    launcher.launch(Manifest.permission.CAMERA)
                }
                viewModel.loadLayout()
            }

            DashboardPager(viewModel = viewModel)
        }
    }
}
