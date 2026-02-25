package com.flarecon.adaptive

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.util.Size
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.flarecon.adaptive.ui.theme.AdaptiveTheme
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "Adaptive"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Handle deep link
        val importedSettings = handleDeepLink(intent)
        
        setContent {
            AdaptiveTheme {
                AdaptiveApp(importedSettings = importedSettings)
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle deep link when app is already running
        val importedSettings = handleDeepLink(intent)
        if (importedSettings != null) {
            setContent {
                AdaptiveTheme {
                    AdaptiveApp(importedSettings = importedSettings)
                }
            }
        }
    }
    
    private fun handleDeepLink(intent: Intent?): AppSettings? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        
        val uri = intent.data ?: return null
        Log.d(TAG, "Deep link received: $uri")
        
        if (uri.scheme != "adaptive" || uri.host != "import") {
            Log.w(TAG, "Invalid scheme or host: ${uri.scheme}://${uri.host}")
            return null
        }
        
        val encodedData = uri.getQueryParameter("data")
        if (encodedData.isNullOrEmpty()) {
            Log.w(TAG, "No data parameter in deep link")
            Toast.makeText(this, "Invalid QR code: no data", Toast.LENGTH_SHORT).show()
            return null
        }
        
        return try {
            val settings = DeepLinkHandler.decodeConfig(encodedData)
            if (settings != null) {
                Log.i(TAG, "Successfully imported config via QR")
                Toast.makeText(this, "Configuration imported!", Toast.LENGTH_SHORT).show()
                // Save the imported settings
                SettingsManager.saveSettings(this, settings)
            }
            settings
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode deep link data", e)
            Toast.makeText(this, "Invalid QR code: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }
}

// Deep Link Handler for QR-based config sharing
object DeepLinkHandler {
    private const val TAG = "DeepLinkHandler"
    
    /**
     * Encode settings to Base64 string for QR code
     * Supports optional gzip compression for smaller QR codes
     */
    fun encodeConfig(settings: AppSettings, compress: Boolean = true): String {
        val json = SettingsManager.exportToJson(settings)
        Log.d(TAG, "Encoding config, JSON length: ${json.length}")
        
        return if (compress) {
            // Gzip compress then Base64 encode
            val compressed = gzipCompress(json)
            Log.d(TAG, "Compressed size: ${compressed.size} bytes")
            "gz:" + Base64.encodeToString(compressed, Base64.URL_SAFE or Base64.NO_WRAP)
        } else {
            Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
        }
    }
    
    /**
     * Decode Base64 config from QR code
     * Supports both compressed (gz:) and uncompressed formats
     */
    fun decodeConfig(encoded: String): AppSettings? {
        Log.d(TAG, "Decoding config, length: ${encoded.length}")
        
        val json = try {
            if (encoded.startsWith("gz:")) {
                // Gzip compressed
                val compressed = Base64.decode(encoded.substring(3), Base64.URL_SAFE)
                gzipDecompress(compressed)
            } else {
                // Plain Base64
                String(Base64.decode(encoded, Base64.URL_SAFE), Charsets.UTF_8)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode Base64", e)
            throw IllegalArgumentException("Invalid Base64 encoding")
        }
        
        Log.d(TAG, "Decoded JSON length: ${json.length}")
        
        return try {
            val settings = SettingsManager.importFromJson(json)
            if (settings == null) {
                throw IllegalArgumentException("Invalid config format")
            }
            
            // Validate URL (allow http for localhost, require https otherwise)
            val url = settings.url
            if (url != null && !url.startsWith("https://") && 
                !url.startsWith("http://localhost") && 
                !url.startsWith("http://127.0.0.1") &&
                !url.startsWith("http://10.0.2.2")) {
                Log.w(TAG, "URL validation: $url")
                // Don't reject, just warn - user can decide
            }
            
            settings
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON config", e)
            throw IllegalArgumentException("Invalid JSON: ${e.message}")
        }
    }
    
    /**
     * Generate deep link URL for sharing
     */
    fun generateDeepLink(settings: AppSettings, compress: Boolean = true): String {
        val encoded = encodeConfig(settings, compress)
        return "adaptive://import?data=$encoded"
    }
    
    private fun gzipCompress(data: String): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { gzip ->
            gzip.write(data.toByteArray(Charsets.UTF_8))
        }
        return bos.toByteArray()
    }
    
    private fun gzipDecompress(compressed: ByteArray): String {
        ByteArrayInputStream(compressed).use { bis ->
            GZIPInputStream(bis).use { gzip ->
                return gzip.bufferedReader(Charsets.UTF_8).readText()
            }
        }
    }
}

// QR Code Generator Helper
object QrCodeGenerator {
    private const val TAG = "QrCodeGenerator"
    
    /**
     * Generate QR code bitmap from string content
     */
    fun generateQrBitmap(content: String, size: Int = 512): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            
            val bitMatrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                size,
                size,
                hints
            )
            
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(
                        x, y,
                        if (bitMatrix.get(x, y)) android.graphics.Color.BLACK
                        else android.graphics.Color.WHITE
                    )
                }
            }
            
            Log.d(TAG, "Generated QR code bitmap: ${width}x${height}")
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate QR code", e)
            null
        }
    }
}

// App Settings Data Class
data class AppSettings(
    val url: String? = null,
    // Header settings
    val showHeader: Boolean = false,
    val headerTitle: String = "",
    val showSubtitle: Boolean = false,
    val subtitleText: String = "",
    val headerColorType: String = "preset",
    val headerColorPreset: String = "default",
    val headerColorCustom: String = "#6200EE",
    val headerSize: String = "medium",
    val headerElevation: Boolean = true,
    val headerOverlay: Boolean = false, // true = website behind header, false = website below header
    val headerPaddingLeft: Int = 4,
    val headerPaddingRight: Int = 4,
    val headerPaddingTop: Int = 0,
    val headerPaddingBottom: Int = 0,
    // Control buttons
    val showRefreshButton: Boolean = true,
    val showHomeButton: Boolean = false,
    val showBackButton: Boolean = false,
    // Website layout
    val websiteMarginTop: Int = 0,
    val websiteMarginBottom: Int = 0,
    val websiteMarginLeft: Int = 0,
    val websiteMarginRight: Int = 0,
    val backgroundColor: String = "#000000",
    // Other settings
    val showProgressBar: Boolean = true,
    val progressColorCustom: String = "#6200EE",
    val immersiveMode: Boolean = true,
    val allowZoom: Boolean = true,
    val desktopMode: Boolean = false
)

// Persistent Storage Manager
object SettingsManager {
    private const val PREFS_NAME = "adaptive_settings"
    private const val FILE_NAME = "adaptive_config.json"
    
    fun saveSettings(context: Context, settings: AppSettings) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("url", settings.url)
            putBoolean("showHeader", settings.showHeader)
            putString("headerTitle", settings.headerTitle)
            putBoolean("showSubtitle", settings.showSubtitle)
            putString("subtitleText", settings.subtitleText)
            putString("headerColorType", settings.headerColorType)
            putString("headerColorPreset", settings.headerColorPreset)
            putString("headerColorCustom", settings.headerColorCustom)
            putString("headerSize", settings.headerSize)
            putBoolean("headerElevation", settings.headerElevation)
            putBoolean("headerOverlay", settings.headerOverlay)
            putInt("headerPaddingLeft", settings.headerPaddingLeft)
            putInt("headerPaddingRight", settings.headerPaddingRight)
            putInt("headerPaddingTop", settings.headerPaddingTop)
            putInt("headerPaddingBottom", settings.headerPaddingBottom)
            putBoolean("showRefreshButton", settings.showRefreshButton)
            putBoolean("showHomeButton", settings.showHomeButton)
            putBoolean("showBackButton", settings.showBackButton)
            putInt("websiteMarginTop", settings.websiteMarginTop)
            putInt("websiteMarginBottom", settings.websiteMarginBottom)
            putInt("websiteMarginLeft", settings.websiteMarginLeft)
            putInt("websiteMarginRight", settings.websiteMarginRight)
            putString("backgroundColor", settings.backgroundColor)
            putBoolean("showProgressBar", settings.showProgressBar)
            putString("progressColorCustom", settings.progressColorCustom)
            putBoolean("immersiveMode", settings.immersiveMode)
            putBoolean("allowZoom", settings.allowZoom)
            putBoolean("desktopMode", settings.desktopMode)
            commit()
        }
        saveToFile(context, settings)
    }
    
    private fun saveToFile(context: Context, settings: AppSettings) {
        try {
            val json = JSONObject().apply {
                put("url", settings.url ?: "")
                put("showHeader", settings.showHeader)
                put("headerTitle", settings.headerTitle)
                put("showSubtitle", settings.showSubtitle)
                put("subtitleText", settings.subtitleText)
                put("headerColorType", settings.headerColorType)
                put("headerColorPreset", settings.headerColorPreset)
                put("headerColorCustom", settings.headerColorCustom)
                put("headerSize", settings.headerSize)
                put("headerElevation", settings.headerElevation)
                put("headerOverlay", settings.headerOverlay)
                put("headerPaddingLeft", settings.headerPaddingLeft)
                put("headerPaddingRight", settings.headerPaddingRight)
                put("headerPaddingTop", settings.headerPaddingTop)
                put("headerPaddingBottom", settings.headerPaddingBottom)
                put("showRefreshButton", settings.showRefreshButton)
                put("showHomeButton", settings.showHomeButton)
                put("showBackButton", settings.showBackButton)
                put("websiteMarginTop", settings.websiteMarginTop)
                put("websiteMarginBottom", settings.websiteMarginBottom)
                put("websiteMarginLeft", settings.websiteMarginLeft)
                put("websiteMarginRight", settings.websiteMarginRight)
                put("backgroundColor", settings.backgroundColor)
                put("showProgressBar", settings.showProgressBar)
                put("progressColorCustom", settings.progressColorCustom)
                put("immersiveMode", settings.immersiveMode)
                put("allowZoom", settings.allowZoom)
                put("desktopMode", settings.desktopMode)
            }
            File(context.filesDir, FILE_NAME).writeText(json.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun loadSettings(context: Context): AppSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val urlFromPrefs = prefs.getString("url", null)
        
        if (urlFromPrefs != null) {
            return AppSettings(
                url = urlFromPrefs,
                showHeader = prefs.getBoolean("showHeader", false),
                headerTitle = prefs.getString("headerTitle", "") ?: "",
                showSubtitle = prefs.getBoolean("showSubtitle", false),
                subtitleText = prefs.getString("subtitleText", "") ?: "",
                headerColorType = prefs.getString("headerColorType", "preset") ?: "preset",
                headerColorPreset = prefs.getString("headerColorPreset", "default") ?: "default",
                headerColorCustom = prefs.getString("headerColorCustom", "#6200EE") ?: "#6200EE",
                headerSize = prefs.getString("headerSize", "medium") ?: "medium",
                headerElevation = prefs.getBoolean("headerElevation", true),
                headerOverlay = prefs.getBoolean("headerOverlay", false),
                headerPaddingLeft = prefs.getInt("headerPaddingLeft", 4),
                headerPaddingRight = prefs.getInt("headerPaddingRight", 4),
                headerPaddingTop = prefs.getInt("headerPaddingTop", 0),
                headerPaddingBottom = prefs.getInt("headerPaddingBottom", 0),
                showRefreshButton = prefs.getBoolean("showRefreshButton", true),
                showHomeButton = prefs.getBoolean("showHomeButton", false),
                showBackButton = prefs.getBoolean("showBackButton", false),
                websiteMarginTop = prefs.getInt("websiteMarginTop", 0),
                websiteMarginBottom = prefs.getInt("websiteMarginBottom", 0),
                websiteMarginLeft = prefs.getInt("websiteMarginLeft", 0),
                websiteMarginRight = prefs.getInt("websiteMarginRight", 0),
                backgroundColor = prefs.getString("backgroundColor", "#000000") ?: "#000000",
                showProgressBar = prefs.getBoolean("showProgressBar", true),
                progressColorCustom = prefs.getString("progressColorCustom", "#6200EE") ?: "#6200EE",
                immersiveMode = prefs.getBoolean("immersiveMode", true),
                allowZoom = prefs.getBoolean("allowZoom", true),
                desktopMode = prefs.getBoolean("desktopMode", false)
            )
        }
        return loadFromFile(context) ?: AppSettings()
    }
    
    private fun loadFromFile(context: Context): AppSettings? {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return null
            
            val json = JSONObject(file.readText())
            val url = json.optString("url", "").takeIf { it.isNotEmpty() } ?: return null
            
            AppSettings(
                url = url,
                showHeader = json.optBoolean("showHeader", false),
                headerTitle = json.optString("headerTitle", ""),
                showSubtitle = json.optBoolean("showSubtitle", false),
                subtitleText = json.optString("subtitleText", ""),
                headerColorType = json.optString("headerColorType", "preset"),
                headerColorPreset = json.optString("headerColorPreset", "default"),
                headerColorCustom = json.optString("headerColorCustom", "#6200EE"),
                headerSize = json.optString("headerSize", "medium"),
                headerElevation = json.optBoolean("headerElevation", true),
                headerOverlay = json.optBoolean("headerOverlay", false),
                headerPaddingLeft = json.optInt("headerPaddingLeft", 4),
                headerPaddingRight = json.optInt("headerPaddingRight", 4),
                headerPaddingTop = json.optInt("headerPaddingTop", 0),
                headerPaddingBottom = json.optInt("headerPaddingBottom", 0),
                showRefreshButton = json.optBoolean("showRefreshButton", true),
                showHomeButton = json.optBoolean("showHomeButton", false),
                showBackButton = json.optBoolean("showBackButton", false),
                websiteMarginTop = json.optInt("websiteMarginTop", 0),
                websiteMarginBottom = json.optInt("websiteMarginBottom", 0),
                websiteMarginLeft = json.optInt("websiteMarginLeft", 0),
                websiteMarginRight = json.optInt("websiteMarginRight", 0),
                backgroundColor = json.optString("backgroundColor", "#000000"),
                showProgressBar = json.optBoolean("showProgressBar", true),
                progressColorCustom = json.optString("progressColorCustom", "#6200EE"),
                immersiveMode = json.optBoolean("immersiveMode", true),
                allowZoom = json.optBoolean("allowZoom", true),
                desktopMode = json.optBoolean("desktopMode", false)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    // Export settings to pretty-printed JSON string
    fun exportToJson(settings: AppSettings): String {
        return JSONObject().apply {
            put("url", settings.url ?: "")
            put("showHeader", settings.showHeader)
            put("headerTitle", settings.headerTitle)
            put("showSubtitle", settings.showSubtitle)
            put("subtitleText", settings.subtitleText)
            put("headerColorType", settings.headerColorType)
            put("headerColorPreset", settings.headerColorPreset)
            put("headerColorCustom", settings.headerColorCustom)
            put("headerSize", settings.headerSize)
            put("headerElevation", settings.headerElevation)
            put("headerOverlay", settings.headerOverlay)
            put("headerPaddingLeft", settings.headerPaddingLeft)
            put("headerPaddingRight", settings.headerPaddingRight)
            put("headerPaddingTop", settings.headerPaddingTop)
            put("headerPaddingBottom", settings.headerPaddingBottom)
            put("showRefreshButton", settings.showRefreshButton)
            put("showHomeButton", settings.showHomeButton)
            put("showBackButton", settings.showBackButton)
            put("websiteMarginTop", settings.websiteMarginTop)
            put("websiteMarginBottom", settings.websiteMarginBottom)
            put("websiteMarginLeft", settings.websiteMarginLeft)
            put("websiteMarginRight", settings.websiteMarginRight)
            put("backgroundColor", settings.backgroundColor)
            put("showProgressBar", settings.showProgressBar)
            put("progressColorCustom", settings.progressColorCustom)
            put("immersiveMode", settings.immersiveMode)
            put("allowZoom", settings.allowZoom)
            put("desktopMode", settings.desktopMode)
        }.toString(2) // Pretty print with 2-space indent
    }
    
    // Import settings from JSON string
    fun importFromJson(jsonString: String): AppSettings? {
        return try {
            val json = JSONObject(jsonString)
            AppSettings(
                url = json.optString("url", "").takeIf { it.isNotEmpty() },
                showHeader = json.optBoolean("showHeader", false),
                headerTitle = json.optString("headerTitle", ""),
                showSubtitle = json.optBoolean("showSubtitle", false),
                subtitleText = json.optString("subtitleText", ""),
                headerColorType = json.optString("headerColorType", "preset"),
                headerColorPreset = json.optString("headerColorPreset", "default"),
                headerColorCustom = json.optString("headerColorCustom", "#6200EE"),
                headerSize = json.optString("headerSize", "medium"),
                headerElevation = json.optBoolean("headerElevation", true),
                headerOverlay = json.optBoolean("headerOverlay", false),
                headerPaddingLeft = json.optInt("headerPaddingLeft", 4),
                headerPaddingRight = json.optInt("headerPaddingRight", 4),
                headerPaddingTop = json.optInt("headerPaddingTop", 0),
                headerPaddingBottom = json.optInt("headerPaddingBottom", 0),
                showRefreshButton = json.optBoolean("showRefreshButton", true),
                showHomeButton = json.optBoolean("showHomeButton", false),
                showBackButton = json.optBoolean("showBackButton", false),
                websiteMarginTop = json.optInt("websiteMarginTop", 0),
                websiteMarginBottom = json.optInt("websiteMarginBottom", 0),
                websiteMarginLeft = json.optInt("websiteMarginLeft", 0),
                websiteMarginRight = json.optInt("websiteMarginRight", 0),
                backgroundColor = json.optString("backgroundColor", "#000000"),
                showProgressBar = json.optBoolean("showProgressBar", true),
                progressColorCustom = json.optString("progressColorCustom", "#6200EE"),
                immersiveMode = json.optBoolean("immersiveMode", true),
                allowZoom = json.optBoolean("allowZoom", true),
                desktopMode = json.optBoolean("desktopMode", false)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        try { File(context.filesDir, FILE_NAME).delete() } catch (_: Exception) {}
    }
}

// Color presets
data class ColorPreset(val id: String, val name: String, val containerColor: Color, val contentColor: Color)

val colorPresets = listOf(
    ColorPreset("default", "Default", Color(0xFFFFFBFE), Color(0xFF1C1B1F)),
    ColorPreset("primary", "Purple", Color(0xFF6200EE), Color.White),
    ColorPreset("dark", "Dark", Color(0xFF1C1C1C), Color.White),
    ColorPreset("light", "Light", Color(0xFFF5F5F5), Color(0xFF212121)),
    ColorPreset("blue", "Blue", Color(0xFF1976D2), Color.White),
    ColorPreset("green", "Green", Color(0xFF388E3C), Color.White),
    ColorPreset("red", "Red", Color(0xFFD32F2F), Color.White),
    ColorPreset("orange", "Orange", Color(0xFFF57C00), Color.White),
    ColorPreset("teal", "Teal", Color(0xFF00897B), Color.White),
    ColorPreset("pink", "Pink", Color(0xFFC2185B), Color.White),
    ColorPreset("indigo", "Indigo", Color(0xFF303F9F), Color.White),
    ColorPreset("black", "Black", Color(0xFF000000), Color.White)
)

fun getHeaderColors(settings: AppSettings): Pair<Color, Color> {
    return if (settings.headerColorType == "custom") {
        val customColor = try { Color(settings.headerColorCustom.toColorInt()) } catch (e: Exception) { Color(0xFF6200EE) }
        val luminance = (0.299 * ((customColor.toArgb() shr 16) and 0xFF) +
                0.587 * ((customColor.toArgb() shr 8) and 0xFF) +
                0.114 * (customColor.toArgb() and 0xFF)) / 255
        Pair(customColor, if (luminance > 0.5) Color.Black else Color.White)
    } else {
        val preset = colorPresets.find { it.id == settings.headerColorPreset } ?: colorPresets[0]
        Pair(preset.containerColor, preset.contentColor)
    }
}

fun getHeaderHeight(size: String): Dp = when (size) { "small" -> 48.dp; "large" -> 72.dp; else -> 64.dp }
fun getHeaderTitleSize(size: String): Int = when (size) { "small" -> 16; "large" -> 22; else -> 18 }

fun parseColor(hex: String, default: Color = Color.Black): Color {
    return try { Color(hex.toColorInt()) } catch (e: Exception) { default }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveApp(importedSettings: AppSettings? = null) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(importedSettings ?: SettingsManager.loadSettings(context)) }
    var showSettings by remember { mutableStateOf(settings.url == null) }
    var webView: WebView? by remember { mutableStateOf(null) }
    var isLoading by remember { mutableStateOf(false) }
    var loadingProgress by remember { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    
    // Show toast if config was imported via QR
    LaunchedEffect(importedSettings) {
        if (importedSettings != null) {
            showSettings = false // Go directly to WebView if config was imported
        }
    }

    BackHandler(enabled = webView?.canGoBack() == true && !showSettings) {
        webView?.goBack()
    }

    val (headerContainerColor, headerContentColor) = remember(settings) { getHeaderColors(settings) }
    val bgColor = remember(settings.backgroundColor) { parseColor(settings.backgroundColor) }

    if (showSettings || settings.url == null) {
        SettingsScreen(
            currentSettings = settings,
            onSettingsSaved = { newSettings ->
                val formattedSettings = newSettings.copy(url = newSettings.url?.let { formatUrl(it) })
                SettingsManager.saveSettings(context, formattedSettings)
                settings = formattedSettings
                showSettings = false
            },
            onSettingsCleared = {
                SettingsManager.clearAll(context)
                settings = AppSettings()
                showSettings = true
            },
            onCancel = if (settings.url != null) { { showSettings = false } } else null
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
        ) {
            // WebView with margins
            val webViewTopMargin = settings.websiteMarginTop.dp + 
                if (settings.showHeader && !settings.headerOverlay) {
                    getHeaderHeight(settings.headerSize) + settings.headerPaddingTop.dp + settings.headerPaddingBottom.dp
                } else { 0.dp }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(
                        top = webViewTopMargin,
                        bottom = settings.websiteMarginBottom.dp,
                        start = settings.websiteMarginLeft.dp,
                        end = settings.websiteMarginRight.dp
                    )
            ) {
                WebViewScreen(
                    url = settings.url!!,
                    settings = settings,
                    onWebViewCreated = { webView = it },
                    onLoadingChanged = { isLoading = it },
                    onProgressChanged = { loadingProgress = it }
                )
            }

            // Header (overlay or above)
            if (settings.showHeader) {
                Surface(
                    color = headerContainerColor,
                    shadowElevation = if (settings.headerElevation) 4.dp else 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .zIndex(10f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(getHeaderHeight(settings.headerSize))
                            .padding(
                                start = settings.headerPaddingLeft.dp,
                                end = settings.headerPaddingRight.dp,
                                top = settings.headerPaddingTop.dp,
                                bottom = settings.headerPaddingBottom.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (settings.showBackButton) {
                            IconButton(onClick = { webView?.goBack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = headerContentColor)
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = if (settings.showBackButton) 0.dp else 8.dp)
                        ) {
                            if (settings.headerTitle.isNotEmpty()) {
                                Text(
                                    text = settings.headerTitle,
                                    color = headerContentColor,
                                    fontSize = getHeaderTitleSize(settings.headerSize).sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                            if (settings.showSubtitle && settings.subtitleText.isNotEmpty()) {
                                Text(
                                    text = settings.subtitleText,
                                    color = headerContentColor.copy(alpha = 0.7f),
                                    fontSize = (getHeaderTitleSize(settings.headerSize) - 4).sp,
                                    maxLines = 1
                                )
                            }
                        }

                        if (settings.showHomeButton) {
                            IconButton(onClick = { webView?.loadUrl(settings.url!!) }) {
                                Icon(Icons.Default.Home, "Home", tint = headerContentColor)
                            }
                        }
                        if (settings.showRefreshButton) {
                            IconButton(onClick = { webView?.reload() }) {
                                Icon(Icons.Default.Refresh, "Refresh", tint = headerContentColor)
                            }
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, "Menu", tint = headerContentColor)
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    onClick = { showMenu = false; showSettings = true },
                                    leadingIcon = { Icon(Icons.Default.Settings, null) }
                                )
                            }
                        }
                    }
                }
            }

            // Loading indicator
            if (settings.showProgressBar && isLoading) {
                LinearProgressIndicator(
                    progress = { loadingProgress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .then(
                            if (settings.showHeader) Modifier.padding(top = getHeaderHeight(settings.headerSize))
                            else Modifier
                        )
                        .zIndex(11f),
                    color = parseColor(settings.progressColorCustom, MaterialTheme.colorScheme.primary)
                )
            }

            // Floating controls when no header
            if (!settings.showHeader) {
                FloatingControls(
                    showRefresh = settings.showRefreshButton,
                    onRefresh = { webView?.reload() },
                    onSettings = { showSettings = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(8.dp)
                        .zIndex(12f)
                )
            }
        }
    }
}

@Composable
fun FloatingControls(
    showRefresh: Boolean,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        FilledTonalIconButton(
            onClick = { expanded = !expanded },
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            )
        ) {
            Icon(Icons.Default.MoreVert, "Menu", tint = MaterialTheme.colorScheme.onSurface)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (showRefresh) {
                DropdownMenuItem(
                    text = { Text("Refresh") },
                    onClick = { expanded = false; onRefresh() },
                    leadingIcon = { Icon(Icons.Default.Refresh, null) }
                )
            }
            DropdownMenuItem(
                text = { Text("Settings") },
                onClick = { expanded = false; onSettings() },
                leadingIcon = { Icon(Icons.Default.Settings, null) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentSettings: AppSettings,
    onSettingsSaved: (AppSettings) -> Unit,
    onSettingsCleared: () -> Unit,
    onCancel: (() -> Unit)?
) {
    var urlInput by remember { mutableStateOf(currentSettings.url ?: "") }
    var showHeader by remember { mutableStateOf(currentSettings.showHeader) }
    var headerTitle by remember { mutableStateOf(currentSettings.headerTitle) }
    var showSubtitle by remember { mutableStateOf(currentSettings.showSubtitle) }
    var subtitleText by remember { mutableStateOf(currentSettings.subtitleText) }
    var headerColorType by remember { mutableStateOf(currentSettings.headerColorType) }
    var headerColorPreset by remember { mutableStateOf(currentSettings.headerColorPreset) }
    var headerColorCustom by remember { mutableStateOf(currentSettings.headerColorCustom) }
    var headerSize by remember { mutableStateOf(currentSettings.headerSize) }
    var headerElevation by remember { mutableStateOf(currentSettings.headerElevation) }
    var headerOverlay by remember { mutableStateOf(currentSettings.headerOverlay) }
    var headerPaddingLeft by remember { mutableStateOf(currentSettings.headerPaddingLeft.toString()) }
    var headerPaddingRight by remember { mutableStateOf(currentSettings.headerPaddingRight.toString()) }
    var headerPaddingTop by remember { mutableStateOf(currentSettings.headerPaddingTop.toString()) }
    var headerPaddingBottom by remember { mutableStateOf(currentSettings.headerPaddingBottom.toString()) }
    var showRefresh by remember { mutableStateOf(currentSettings.showRefreshButton) }
    var showHome by remember { mutableStateOf(currentSettings.showHomeButton) }
    var showBack by remember { mutableStateOf(currentSettings.showBackButton) }
    var websiteMarginTop by remember { mutableStateOf(currentSettings.websiteMarginTop.toString()) }
    var websiteMarginBottom by remember { mutableStateOf(currentSettings.websiteMarginBottom.toString()) }
    var websiteMarginLeft by remember { mutableStateOf(currentSettings.websiteMarginLeft.toString()) }
    var websiteMarginRight by remember { mutableStateOf(currentSettings.websiteMarginRight.toString()) }
    var backgroundColor by remember { mutableStateOf(currentSettings.backgroundColor) }
    var showProgress by remember { mutableStateOf(currentSettings.showProgressBar) }
    var progressColorCustom by remember { mutableStateOf(currentSettings.progressColorCustom) }
    var immersiveMode by remember { mutableStateOf(currentSettings.immersiveMode) }
    var allowZoom by remember { mutableStateOf(currentSettings.allowZoom) }
    var desktopMode by remember { mutableStateOf(currentSettings.desktopMode) }
    var showError by remember { mutableStateOf(false) }

    val isFirstSetup = currentSettings.url == null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .statusBarsPadding()
    ) {
        // Header
        if (isFirstSetup) {
            Text("Setup", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text("Configure your app", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 24.dp))
        } else {
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Settings", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                if (onCancel != null) TextButton(onClick = onCancel) { Text("Done") }
            }
        }

        // URL Section
        SettingsCard("Source URL") {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it; showError = false },
                label = { Text("URL") },
                placeholder = { Text("https://yourwebsite.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                isError = showError,
                supportingText = if (showError) { { Text("Please enter a valid URL") } } else null,
                trailingIcon = { if (urlInput.isNotEmpty()) IconButton(onClick = { urlInput = "" }) { Icon(Icons.Default.Clear, "Clear") } }
            )
        }

        // Header Section
        SettingsCard("Header Bar") {
            SettingsSwitch("Show Header Bar", "Display a toolbar at the top", showHeader) { showHeader = it }

            AnimatedVisibility(visible = showHeader) {
                Column {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    OutlinedTextField(headerTitle, { headerTitle = it }, label = { Text("App Name") }, placeholder = { Text("My App") }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), singleLine = true)

                    SettingsSwitch("Show Subtitle", "Display text below app name", showSubtitle) { showSubtitle = it }
                    AnimatedVisibility(visible = showSubtitle) {
                        OutlinedTextField(subtitleText, { subtitleText = it }, label = { Text("Subtitle Text") }, placeholder = { Text("Welcome back!") }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), singleLine = true)
                    }

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    // Header Size
                    Text("Header Size", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("small" to "Small", "medium" to "Medium", "large" to "Large").forEach { (value, label) ->
                            FilterChip(selected = headerSize == value, onClick = { headerSize = value }, label = { Text(label) },
                                leadingIcon = if (headerSize == value) { { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) } } else null)
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 12.dp))

                    // Header Color
                    Text("Header Color", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = headerColorType == "preset", onClick = { headerColorType = "preset" }, label = { Text("Preset") })
                        FilterChip(selected = headerColorType == "custom", onClick = { headerColorType = "custom" }, label = { Text("Custom") })
                    }
                    Spacer(Modifier.height(12.dp))

                    if (headerColorType == "preset") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            colorPresets.chunked(4).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { preset -> ColorPresetItem(preset, headerColorPreset == preset.id, { headerColorPreset = preset.id }, Modifier.weight(1f)) }
                                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    } else {
                        ColorInputField("Hex Color", headerColorCustom) { headerColorCustom = it }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 12.dp))

                    // Header Position
                    SettingsSwitch("Overlay Mode", "Website shows behind header (transparent header recommended)", headerOverlay) { headerOverlay = it }
                    SettingsSwitch("Header Shadow", "Show elevation shadow", headerElevation) { headerElevation = it }

                    HorizontalDivider(Modifier.padding(vertical = 12.dp))

                    // Header Padding
                    Text("Header Padding (dp)", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberInputField("Left", headerPaddingLeft, { headerPaddingLeft = it }, Modifier.weight(1f))
                        NumberInputField("Top", headerPaddingTop, { headerPaddingTop = it }, Modifier.weight(1f))
                        NumberInputField("Right", headerPaddingRight, { headerPaddingRight = it }, Modifier.weight(1f))
                        NumberInputField("Bottom", headerPaddingBottom, { headerPaddingBottom = it }, Modifier.weight(1f))
                    }

                    HorizontalDivider(Modifier.padding(vertical = 12.dp))

                    SettingsSwitch("Back Button", "Show navigation back button", showBack) { showBack = it }
                    SettingsSwitch("Home Button", "Quick return to main page", showHome) { showHome = it }
                }
            }
        }

        // Website Layout Section
        SettingsCard("Website Layout") {
            Text("Website Margins (dp)", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))
            Text("Control how much space around the website", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberInputField("Left", websiteMarginLeft, { websiteMarginLeft = it }, Modifier.weight(1f))
                NumberInputField("Top", websiteMarginTop, { websiteMarginTop = it }, Modifier.weight(1f))
                NumberInputField("Right", websiteMarginRight, { websiteMarginRight = it }, Modifier.weight(1f))
                NumberInputField("Bottom", websiteMarginBottom, { websiteMarginBottom = it }, Modifier.weight(1f))
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            Text("Background Color", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 4.dp))
            Text("Visible in margin areas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            ColorInputField("Hex Color", backgroundColor) { backgroundColor = it }
        }

        // Appearance Section
        SettingsCard("Appearance") {
            SettingsSwitch("Refresh Button", "Allow page refresh", showRefresh) { showRefresh = it }
            SettingsSwitch("Loading Indicator", "Show progress bar while loading", showProgress) { showProgress = it }
            AnimatedVisibility(visible = showProgress) {
                ColorInputField("Progress Bar Color", progressColorCustom, Modifier.padding(top = 8.dp)) { progressColorCustom = it }
            }
            SettingsSwitch("Immersive Mode", "Use full screen space", immersiveMode) { immersiveMode = it }
        }

        // Browser Section
        SettingsCard("Browser") {
            SettingsSwitch("Allow Zoom", "Enable pinch to zoom", allowZoom) { allowZoom = it }
            SettingsSwitch("Desktop Mode", "Request desktop version of sites", desktopMode) { desktopMode = it }
        }

        // Config Manager Section
        if (!isFirstSetup) {
            ConfigManagerSection(
                currentSettings = currentSettings,
                onImportSettings = { importedSettings ->
                    // Update all the state variables with imported values
                    urlInput = importedSettings.url ?: ""
                    showHeader = importedSettings.showHeader
                    headerTitle = importedSettings.headerTitle
                    showSubtitle = importedSettings.showSubtitle
                    subtitleText = importedSettings.subtitleText
                    headerColorType = importedSettings.headerColorType
                    headerColorPreset = importedSettings.headerColorPreset
                    headerColorCustom = importedSettings.headerColorCustom
                    headerSize = importedSettings.headerSize
                    headerElevation = importedSettings.headerElevation
                    headerOverlay = importedSettings.headerOverlay
                    headerPaddingLeft = importedSettings.headerPaddingLeft.toString()
                    headerPaddingRight = importedSettings.headerPaddingRight.toString()
                    headerPaddingTop = importedSettings.headerPaddingTop.toString()
                    headerPaddingBottom = importedSettings.headerPaddingBottom.toString()
                    showRefresh = importedSettings.showRefreshButton
                    showHome = importedSettings.showHomeButton
                    showBack = importedSettings.showBackButton
                    websiteMarginTop = importedSettings.websiteMarginTop.toString()
                    websiteMarginBottom = importedSettings.websiteMarginBottom.toString()
                    websiteMarginLeft = importedSettings.websiteMarginLeft.toString()
                    websiteMarginRight = importedSettings.websiteMarginRight.toString()
                    backgroundColor = importedSettings.backgroundColor
                    showProgress = importedSettings.showProgressBar
                    progressColorCustom = importedSettings.progressColorCustom
                    immersiveMode = importedSettings.immersiveMode
                    allowZoom = importedSettings.allowZoom
                    desktopMode = importedSettings.desktopMode
                }
            )
        }

        Spacer(Modifier.height(24.dp))

        // Save Button
        Button(
            onClick = {
                if (urlInput.isNotBlank()) {
                    onSettingsSaved(AppSettings(
                        url = urlInput,
                        showHeader = showHeader,
                        headerTitle = headerTitle,
                        showSubtitle = showSubtitle,
                        subtitleText = subtitleText,
                        headerColorType = headerColorType,
                        headerColorPreset = headerColorPreset,
                        headerColorCustom = headerColorCustom,
                        headerSize = headerSize,
                        headerElevation = headerElevation,
                        headerOverlay = headerOverlay,
                        headerPaddingLeft = headerPaddingLeft.toIntOrNull() ?: 4,
                        headerPaddingRight = headerPaddingRight.toIntOrNull() ?: 4,
                        headerPaddingTop = headerPaddingTop.toIntOrNull() ?: 0,
                        headerPaddingBottom = headerPaddingBottom.toIntOrNull() ?: 0,
                        showRefreshButton = showRefresh,
                        showHomeButton = showHome,
                        showBackButton = showBack,
                        websiteMarginTop = websiteMarginTop.toIntOrNull() ?: 0,
                        websiteMarginBottom = websiteMarginBottom.toIntOrNull() ?: 0,
                        websiteMarginLeft = websiteMarginLeft.toIntOrNull() ?: 0,
                        websiteMarginRight = websiteMarginRight.toIntOrNull() ?: 0,
                        backgroundColor = backgroundColor,
                        showProgressBar = showProgress,
                        progressColorCustom = progressColorCustom,
                        immersiveMode = immersiveMode,
                        allowZoom = allowZoom,
                        desktopMode = desktopMode
                    ))
                } else showError = true
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (isFirstSetup) "Get Started" else "Save Changes") }

        if (!isFirstSetup) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onSettingsCleared, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text("Reset App")
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}

@Composable
fun NumberInputField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue -> if (newValue.isEmpty() || newValue.all { it.isDigit() || it == '-' }) onValueChange(newValue) },
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
fun ColorInputField(label: String, value: String, modifier: Modifier = Modifier, onValueChange: (String) -> Unit) {
    val previewColor = try { Color(value.toColorInt()) } catch (e: Exception) { Color.Gray }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text("#000000") },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = {
            Box(Modifier.size(24.dp).clip(CircleShape).background(previewColor).border(1.dp, Color.Gray, CircleShape))
        }
    )
}

@Composable
fun ColorPresetItem(preset: ColorPreset, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .border(if (isSelected) 2.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(preset.containerColor).border(1.dp, Color.Gray.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) { if (isSelected) Icon(Icons.Default.Check, null, tint = preset.contentColor, modifier = Modifier.size(16.dp)) }
        Spacer(Modifier.height(4.dp))
        Text(preset.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
fun SettingsSwitch(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun ConfigManagerSection(
    currentSettings: AppSettings,
    onImportSettings: (AppSettings) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    var showJsonDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showQrCodeDialog by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    
    SettingsCard("Config Manager") {
        Text(
            "Export and share your configuration with others, or import configs from friends",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Export Button
            OutlinedButton(
                onClick = {
                    val jsonConfig = SettingsManager.exportToJson(currentSettings)
                    val clip = ClipData.newPlainText("Adaptive Config", jsonConfig)
                    clipboardManager.setPrimaryClip(clip)
                    Toast.makeText(context, "Config copied to clipboard!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("📤 Export")
            }
            
            // Import Button
            OutlinedButton(
                onClick = { 
                    showImportDialog = true
                    importJsonText = ""
                    importError = null
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Import")
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        // QR Code Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Generate QR Code Button
            Button(
                onClick = { showQrCodeDialog = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text("📱 Show QR")
            }
            
            // Scan QR Code Button
            Button(
                onClick = { showQrScanner = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Text("📷 Scan QR")
            }
        }
        
        Spacer(Modifier.height(4.dp))
        
        // View Config Button
        TextButton(
            onClick = { showJsonDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.MoreVert, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("View Current Config")
        }
    }
    
    // QR Scanner Dialog
    if (showQrScanner) {
        QrScannerDialog(
            onDismiss = { showQrScanner = false },
            onQrScanned = { scannedData ->
                showQrScanner = false
                
                // Parse the scanned QR - could be deep link or just data
                val data = when {
                    scannedData.startsWith("adaptive://import?data=") -> {
                        scannedData.substringAfter("data=")
                    }
                    scannedData.startsWith("gz:") || scannedData.startsWith("ey") -> {
                        // Direct encoded data
                        scannedData
                    }
                    else -> {
                        Toast.makeText(context, "Invalid QR code format", Toast.LENGTH_SHORT).show()
                        return@QrScannerDialog
                    }
                }
                
                try {
                    val settings = DeepLinkHandler.decodeConfig(data)
                    if (settings != null) {
                        onImportSettings(settings)
                        SettingsManager.saveSettings(context, settings)
                        Toast.makeText(context, "Config imported via QR!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Invalid config in QR code", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to import: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
    
    // QR Code Display Dialog
    if (showQrCodeDialog) {
        val deepLink = remember(currentSettings) { 
            DeepLinkHandler.generateDeepLink(currentSettings, compress = true) 
        }
        val qrBitmap = remember(deepLink) {
            QrCodeGenerator.generateQrBitmap(deepLink, 512)
        }
        
        Dialog(
            onDismissRequest = { showQrCodeDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Share Config QR Code",
                        style = MaterialTheme.typography.titleLarge
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Text(
                        "Scan this QR code with another device running Adaptive to import your config",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // QR Code Image
                    if (qrBitmap != null) {
                        Surface(
                            modifier = Modifier.size(280.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(280.dp)
                                .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Failed to generate QR code",
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Text(
                        "Data size: ${deepLink.length} characters",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clip = ClipData.newPlainText("Adaptive QR Link", deepLink)
                                clipboardManager.setPrimaryClip(clip)
                                Toast.makeText(context, "Link copied!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Copy Link")
                        }
                        
                        Button(
                            onClick = { showQrCodeDialog = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
    
    // View JSON Dialog
    if (showJsonDialog) {
        val jsonConfig = SettingsManager.exportToJson(currentSettings)
        AlertDialog(
            onDismissRequest = { showJsonDialog = false },
            title = { Text("Current Configuration") },
            text = {
                Column {
                    Text(
                        "This is your current config in JSON format. You can copy it to share with others.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            Modifier
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Text(
                                text = jsonConfig,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val clip = ClipData.newPlainText("Adaptive Config", jsonConfig)
                    clipboardManager.setPrimaryClip(clip)
                    Toast.makeText(context, "Config copied!", Toast.LENGTH_SHORT).show()
                    showJsonDialog = false
                }) {
                    Text("Copy & Close")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJsonDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
    
    // Import Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Configuration") },
            text = {
                Column {
                    Text(
                        "Paste a JSON config below to import settings. This will update all fields in the settings screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    OutlinedTextField(
                        value = importJsonText,
                        onValueChange = { 
                            importJsonText = it
                            importError = null
                        },
                        label = { Text("Paste JSON Config") },
                        placeholder = { Text("{ \"url\": \"...\", ... }") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 150.dp),
                        isError = importError != null,
                        supportingText = importError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                    
                    // Paste from clipboard button
                    TextButton(
                        onClick = {
                            val clipData = clipboardManager.primaryClip
                            if (clipData != null && clipData.itemCount > 0) {
                                importJsonText = clipData.getItemAt(0).text?.toString() ?: ""
                                importError = null
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Paste from Clipboard")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val imported = SettingsManager.importFromJson(importJsonText)
                        if (imported != null) {
                            onImportSettings(imported)
                            Toast.makeText(context, "Config imported! Save to apply changes.", Toast.LENGTH_LONG).show()
                            showImportDialog = false
                        } else {
                            importError = "Invalid JSON format. Please check and try again."
                        }
                    },
                    enabled = importJsonText.isNotBlank()
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    url: String,
    settings: AppSettings,
    onWebViewCreated: (WebView) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
    onProgressChanged: (Int) -> Unit
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                this.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = settings.allowZoom
                    displayZoomControls = false
                    setSupportZoom(settings.allowZoom)
                    allowFileAccess = true
                    allowContentAccess = true
                    mediaPlaybackRequiresUserGesture = false
                    databaseEnabled = true
                    javaScriptCanOpenWindowsAutomatically = true
                    if (settings.desktopMode) userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) { super.onPageStarted(view, url, favicon); onLoadingChanged(true) }
                    override fun onPageFinished(view: WebView?, url: String?) { super.onPageFinished(view, url); onLoadingChanged(false) }
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = false
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) { super.onProgressChanged(view, newProgress); onProgressChanged(newProgress) }
                }
                onWebViewCreated(this)
                loadUrl(url)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

// QR Scanner Composable with CameraX + MLKit
@Composable
fun QrScannerDialog(
    onDismiss: () -> Unit,
    onQrScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember { 
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var scanError by remember { mutableStateOf<String?>(null) }
    var hasScanned by remember { mutableStateOf(false) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            scanError = "Camera permission is required to scan QR codes"
        }
    }
    
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Scan QR Code",
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
                
                // Camera Preview or Error
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!hasCameraPermission) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                scanError ?: "Camera permission required",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                                Text("Grant Permission")
                            }
                        }
                    } else {
                        // Camera Preview
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                }
                                
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    
                                    val preview = Preview.Builder()
                                        .build()
                                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                                    
                                    val barcodeScanner = BarcodeScanning.getClient()
                                    val analysisExecutor = Executors.newSingleThreadExecutor()
                                    
                                    val imageAnalysis = ImageAnalysis.Builder()
                                        .setTargetResolution(Size(1280, 720))
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()
                                        .also { analysis ->
                                            analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                                @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
                                                val mediaImage = imageProxy.image
                                                if (mediaImage != null && !hasScanned) {
                                                    val image = InputImage.fromMediaImage(
                                                        mediaImage,
                                                        imageProxy.imageInfo.rotationDegrees
                                                    )
                                                    
                                                    barcodeScanner.process(image)
                                                        .addOnSuccessListener { barcodes ->
                                                            for (barcode in barcodes) {
                                                                if (barcode.valueType == Barcode.TYPE_TEXT || 
                                                                    barcode.valueType == Barcode.TYPE_URL) {
                                                                    barcode.rawValue?.let { value ->
                                                                        if (!hasScanned) {
                                                                            hasScanned = true
                                                                            onQrScanned(value)
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        .addOnCompleteListener {
                                                            imageProxy.close()
                                                        }
                                                } else {
                                                    imageProxy.close()
                                                }
                                            }
                                        }
                                    
                                    try {
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            imageAnalysis
                                        )
                                    } catch (e: Exception) {
                                        Log.e("QrScanner", "Camera binding failed", e)
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        // Scanner overlay frame
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(250.dp)
                                    .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                            )
                        }
                    }
                }
                
                // Instructions
                Text(
                    "Point your camera at an Adaptive QR code",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

fun formatUrl(url: String): String {
    val trimmedUrl = url.trim()
    return when {
        trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://") -> trimmedUrl
        trimmedUrl.startsWith("localhost") || trimmedUrl.startsWith("127.0.0.1") || trimmedUrl.startsWith("10.0.2.2") -> "http://$trimmedUrl"
        else -> "https://$trimmedUrl"
    }
}
