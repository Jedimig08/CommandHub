package com.commandhub.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.commandhub.service.CommandHubService
import com.commandhub.ui.theme.CommandHubTheme
import com.commandhub.viewmodel.DashboardViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()
    private var webViewInstance: WebView? = null
    private val prefs by lazy { getSharedPreferences("dashboard_prefs", Context.MODE_PRIVATE) }

    companion object {
        private const val SERVICE_ENABLED_KEY = "background_service_enabled"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val serviceEnabled = prefs.getBoolean(SERVICE_ENABLED_KEY, true)
        if (serviceEnabled) {
            startServer()
        }

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webViewInstance?.canGoBack() == true) {
                    webViewInstance?.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        setContent {
            CommandHubTheme {
                var serviceEnabled by remember { mutableStateOf(prefs.getBoolean(SERVICE_ENABLED_KEY, true)) }

                val cameraLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { _ -> }

                val notificationLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { _ -> }

                val bluetoothLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { _ -> }

                LaunchedEffect(Unit) {
                    viewModel.cameraManager.setLifecycleOwner(this@MainActivity)
                    val cameraStatus = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA)
                    if (cameraStatus != PackageManager.PERMISSION_GRANTED) {
                        cameraLauncher.launch(Manifest.permission.CAMERA)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val notificationStatus = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS,
                        )
                        if (notificationStatus != PackageManager.PERMISSION_GRANTED) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val btConnectStatus = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_CONNECT,
                        )
                        val btScanStatus = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_SCAN,
                        )
                        if (btConnectStatus != PackageManager.PERMISSION_GRANTED || btScanStatus != PackageManager.PERMISSION_GRANTED) {
                            bluetoothLauncher.launch(
                                arrayOf(
                                    Manifest.permission.BLUETOOTH_CONNECT,
                                    Manifest.permission.BLUETOOTH_SCAN,
                                )
                            )
                        }
                    }
                }

                LaunchedEffect(viewModel.keepScreenOn) {
                    if (viewModel.keepScreenOn) {
                        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                Box(Modifier.fillMaxSize()) {
                    if (serviceEnabled) {
                        AndroidView(
                            factory = { webViewContext ->
                                WebView(webViewContext).apply {
                                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                                    webViewClient = object : WebViewClient() {
                                        override fun onReceivedError(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                            error: WebResourceError?,
                                        ) {
                                            if (request?.isForMainFrame == true) {
                                                view?.postDelayed({ view.reload() }, 500)
                                            }
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            if (url != null && !url.startsWith("http://127.0.0.1:8080") && !url.startsWith("http://localhost:8080")) {
                                                val jsInject = """
                                                    (function() {
                                                        if (!document.getElementById('cmdhub-home-btn')) {
                                                            const btn = document.createElement('div');
                                                            btn.id = 'cmdhub-home-btn';
                                                            btn.innerHTML = 'CommandHub';
                                                            btn.style.cssText = 'position:fixed; bottom:20px; left:20px; z-index:999999; background:rgba(15,23,42,0.9); color:#38bdf8; padding:10px 16px; border-radius:24px; font-family:-apple-system,sans-serif; font-size:14px; font-weight:bold; border:1px solid #38bdf8; cursor:pointer; box-shadow:0 6px 12px rgba(0,0,0,0.5); user-select:none;';
                                                            btn.onclick = function() { window.location.href = 'http://127.0.0.1:8080/'; };
                                                            document.body.appendChild(btn);
                                                        }
                                                    })();
                                                """.trimIndent()
                                                view?.evaluateJavascript(jsInject, null)
                                            }
                                        }
                                    }
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        allowFileAccess = true
                                        loadsImagesAutomatically = true
                                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    }
                                    loadUrl("http://127.0.0.1:8080/")
                                    webViewInstance = this
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            onRelease = { view ->
                                view.destroy()
                                webViewInstance = null
                            },
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Background server is stopped.",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        serviceEnabled = true
                                        prefs.edit().putBoolean(SERVICE_ENABLED_KEY, true).apply()
                                        startServer()
                                    },
                                ) {
                                    Text("Start server")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startServer() {
        startForegroundService(Intent(this, CommandHubService::class.java))
    }
}
