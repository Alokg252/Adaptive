package com.flarecon.adaptive

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.flarecon.adaptive.ui.theme.AdaptiveTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AdaptiveTheme {
                AdaptiveApp()
            }
        }
    }
}

// SharedPreferences helper
object UrlPreferences {
    private const val PREFS_NAME = "adaptive_prefs"
    private const val KEY_URL = "saved_url"

    fun saveUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_URL, url)
            .apply()
    }

    fun getUrl(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_URL, null)
    }

    fun clearUrl(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_URL)
            .apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveApp() {
    val context = LocalContext.current
    var savedUrl by remember { mutableStateOf(UrlPreferences.getUrl(context)) }
    var showSettings by remember { mutableStateOf(savedUrl == null) }
    var webView: WebView? by remember { mutableStateOf(null) }
    var isLoading by remember { mutableStateOf(false) }
    var loadingProgress by remember { mutableIntStateOf(0) }
    var currentUrl by remember { mutableStateOf(savedUrl ?: "") }

    // Handle back press for WebView navigation
    BackHandler(enabled = webView?.canGoBack() == true && !showSettings) {
        webView?.goBack()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (!showSettings && savedUrl != null) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Adaptive",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = currentUrl,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { webView?.reload() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (showSettings || savedUrl == null) {
                UrlSettingsScreen(
                    currentSavedUrl = savedUrl,
                    onUrlSaved = { url ->
                        val formattedUrl = formatUrl(url)
                        UrlPreferences.saveUrl(context, formattedUrl)
                        savedUrl = formattedUrl
                        currentUrl = formattedUrl
                        showSettings = false
                    },
                    onUrlCleared = {
                        UrlPreferences.clearUrl(context)
                        savedUrl = null
                        currentUrl = ""
                        showSettings = true
                    },
                    onCancel = if (savedUrl != null) {
                        { showSettings = false }
                    } else null
                )
            } else {
                WebViewScreen(
                    url = savedUrl!!,
                    onWebViewCreated = { webView = it },
                    onLoadingChanged = { isLoading = it },
                    onProgressChanged = { loadingProgress = it },
                    onUrlChanged = { currentUrl = it }
                )

                // Loading indicator
                AnimatedVisibility(
                    visible = isLoading,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    LinearProgressIndicator(
                        progress = { loadingProgress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun UrlSettingsScreen(
    currentSavedUrl: String?,
    onUrlSaved: (String) -> Unit,
    onUrlCleared: () -> Unit,
    onCancel: (() -> Unit)?
) {
    var urlInput by remember { mutableStateOf(currentSavedUrl ?: "") }
    var showError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (currentSavedUrl == null) "Welcome to Adaptive" else "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Enter a URL to load your web app",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = urlInput,
            onValueChange = {
                urlInput = it
                showError = false
            },
            label = { Text("Website URL") },
            placeholder = { Text("https://example.com") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go
            ),
            keyboardActions = KeyboardActions(
                onGo = {
                    if (urlInput.isNotBlank()) {
                        onUrlSaved(urlInput)
                    } else {
                        showError = true
                    }
                }
            ),
            isError = showError,
            supportingText = if (showError) {
                { Text("Please enter a valid URL") }
            } else null,
            trailingIcon = {
                if (urlInput.isNotEmpty()) {
                    IconButton(onClick = { urlInput = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (urlInput.isNotBlank()) {
                    onUrlSaved(urlInput)
                } else {
                    showError = true
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Load Website")
        }

        if (currentSavedUrl != null) {
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onUrlCleared,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Clear Saved URL")
            }

            if (onCancel != null) {
                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Perfect for web developers testing responsive designs!",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    url: String,
    onWebViewCreated: (WebView) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onUrlChanged: (String) -> Unit
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    setSupportZoom(true)
                    allowFileAccess = true
                    allowContentAccess = true
                    mediaPlaybackRequiresUserGesture = false
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        onLoadingChanged(true)
                        url?.let { onUrlChanged(it) }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onLoadingChanged(false)
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        return false // Allow all URLs to load in WebView
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        onProgressChanged(newProgress)
                    }
                }

                onWebViewCreated(this)
                loadUrl(url)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

// Helper function to format URL
fun formatUrl(url: String): String {
    val trimmedUrl = url.trim()
    return when {
        trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://") -> trimmedUrl
        trimmedUrl.startsWith("localhost") || trimmedUrl.startsWith("127.0.0.1") -> "http://$trimmedUrl"
        else -> "https://$trimmedUrl"
    }
}