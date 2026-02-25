import groovy.json.JsonSlurper
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.net.URL
import javax.imageio.ImageIO

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Read app config
val appConfig = file("${rootProject.projectDir}/app_config.json").let { configFile ->
    if (configFile.exists()) {
        JsonSlurper().parseText(configFile.readText()) as Map<*, *>
    } else {
        mapOf("appName" to "Adaptive")
    }
}

val configAppName = appConfig["appName"]?.toString() ?: "Adaptive"

android {
    namespace = "com.flarecon.adaptive"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.flarecon.adaptive"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Set app name from config
        resValue("string", "app_name", configAppName)
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    
    // QR code generation
    implementation(libs.zxing.core)
    
    // Camera for QR scanning
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode)
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Task to generate app icon from config
tasks.register("generateAppIcon") {
    group = "build setup"
    description = "Generates app icon from app_config.json"
    
    doLast {
        // Clean up any existing webp icons and anydpi folder
        val resDir = file("src/main/res")
        listOf("mipmap-mdpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi").forEach { folder ->
            file("$resDir/$folder").listFiles()?.filter { it.extension == "webp" }?.forEach { it.delete() }
        }
        file("$resDir/mipmap-anydpi").deleteRecursively()
        
        val iconConfig = appConfig["icon"] as? Map<*, *>
        if (iconConfig == null) {
            println("No icon config found, using default icon")
            return@doLast
        }
        
        val iconType = iconConfig["type"]?.toString() ?: "text"
        val sizes = mapOf(
            "mipmap-mdpi" to 48,
            "mipmap-hdpi" to 72,
            "mipmap-xhdpi" to 96,
            "mipmap-xxhdpi" to 144,
            "mipmap-xxxhdpi" to 192
        )
        
        sizes.forEach { (folder, size) ->
            val outputDir = file("src/main/res/$folder")
            outputDir.mkdirs()
            
            val image: BufferedImage = when (iconType) {
                "url" -> {
                    val urlString = iconConfig["url"]?.toString()
                    if (urlString != null) {
                        try {
                            println("Downloading icon from: $urlString")
                            val originalImage = ImageIO.read(URL(urlString))
                            // Resize to target size
                            val resized = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
                            val g = resized.createGraphics()
                            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                            g.drawImage(originalImage, 0, 0, size, size, null)
                            g.dispose()
                            resized
                        } catch (e: Exception) {
                            println("Failed to download icon: ${e.message}, generating default")
                            generateTextIcon(size, "A", "#6200EE", "#FFFFFF", 64, "bold", 25)
                        }
                    } else {
                        generateTextIcon(size, "A", "#6200EE", "#FFFFFF", 64, "bold", 25)
                    }
                }
                else -> {
                    val text = iconConfig["text"]?.toString() ?: "A"
                    val bgColor = iconConfig["backgroundColor"]?.toString() ?: "#6200EE"
                    val textColor = iconConfig["textColor"]?.toString() ?: "#FFFFFF"
                    val fontSize = (iconConfig["fontSize"] as? Number)?.toInt() ?: 64
                    val fontStyle = iconConfig["fontStyle"]?.toString() ?: "bold"
                    val cornerRadius = (iconConfig["cornerRadius"] as? Number)?.toInt() ?: 25
                    generateTextIcon(size, text, bgColor, textColor, fontSize, fontStyle, cornerRadius)
                }
            }
            
            // Save foreground icon
            val foregroundFile = File(outputDir, "ic_launcher_foreground.png")
            ImageIO.write(image, "PNG", foregroundFile)
            println("Generated: ${foregroundFile.absolutePath}")
            
            // Save regular icon
            val regularFile = File(outputDir, "ic_launcher.png")
            ImageIO.write(image, "PNG", regularFile)
            
            // Save round icon
            val roundFile = File(outputDir, "ic_launcher_round.png")
            ImageIO.write(image, "PNG", roundFile)
        }
        
        println("App icon generated successfully!")
    }
}

fun generateTextIcon(size: Int, text: String, bgColor: String, textColor: String, fontSize: Int, fontStyle: String, cornerRadius: Int): BufferedImage {
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    
    // Enable anti-aliasing
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    
    // Parse colors
    val bg = Color.decode(bgColor)
    val fg = Color.decode(textColor)
    
    // Draw rounded rectangle background
    g.color = bg
    val radius = (size * cornerRadius / 100)
    g.fillRoundRect(0, 0, size, size, radius, radius)
    
    // Draw text
    g.color = fg
    val scaledFontSize = (size * fontSize / 192f).toInt()
    val style = when (fontStyle.lowercase()) {
        "bold" -> Font.BOLD
        "italic" -> Font.ITALIC
        else -> Font.PLAIN
    }
    g.font = Font("SansSerif", style, scaledFontSize)
    
    val fm = g.fontMetrics
    val textWidth = fm.stringWidth(text)
    val textHeight = fm.height
    val x = (size - textWidth) / 2
    val y = (size - textHeight) / 2 + fm.ascent
    
    g.drawString(text, x, y)
    g.dispose()
    
    return image
}

// Make icon generation run before build
tasks.named("preBuild") {
    dependsOn("generateAppIcon")
}