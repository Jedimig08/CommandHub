package com.commandhub.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.commandhub.ui.theme.CommandHubTheme
import com.commandhub.viewmodel.DashboardViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()
    private var webViewInstance: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { _ -> }

                LaunchedEffect(Unit) {
                    viewModel.cameraManager.setLifecycleOwner(this@MainActivity)
                    val status = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA)
                    if (status != PackageManager.PERMISSION_GRANTED) {
                        launcher.launch(Manifest.permission.CAMERA)
                    }
                }

                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            setLayerType(View.LAYER_TYPE_HARDWARE, null)
                            webViewClient = object : WebViewClient() {
                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
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
                                                    btn.innerHTML = '🏠 CommandHub';
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
                )
            }
        }
    }
}
