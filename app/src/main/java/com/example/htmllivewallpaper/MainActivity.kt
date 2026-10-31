package com.example.htmllivewallpaper

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.zip.ZipInputStream

class MainActivity : Activity() {
    private lateinit var urlInput: EditText
    private lateinit var status: TextView
    private val prefs by lazy { getSharedPreferences("wallpaper_settings", MODE_PRIVATE) }
    private val pickZip = 4102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 20)
        }

        root.addView(TextView(this).apply {
            text = "HTML Live Wallpaper V2"
            textSize = 23f
        })
        root.addView(TextView(this).apply {
            text = "दो Rendering Modes: HTML Website/ZIP और Native Matrix Animation"
            textSize = 14f
            setPadding(0, 10, 0, 16)
        })

        root.addView(TextView(this).apply {
            text = "1. HTML Renderer"
            textSize = 18f
        })
        urlInput = EditText(this).apply {
            hint = "https://example.com/wallpaper/"
            setSingleLine(true)
            setText(prefs.getString("source_url", ""))
        }
        root.addView(urlInput, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(makeButton("URL सेव करें और HTML Mode चुनें") { saveUrlAndSelectHtml() })
        root.addView(makeButton("HTML / CSS / JS ZIP Import करें") { chooseZip() })

        root.addView(TextView(this).apply {
            text = "2. Native Renderer"
            textSize = 18f
            setPadding(0, 18, 0, 4)
        })
        root.addView(makeButton("Native Matrix Mode चुनें") { selectMode("native") })
        root.addView(makeButton("HTML WebView Mode चुनें") { selectMode("html") })

        root.addView(TextView(this).apply {
            text = "Native Animation FPS"
            textSize = 14f
            setPadding(0, 12, 0, 2)
        })
        root.addView(makeButton("30 FPS (कम बैटरी/हीट)") {
            prefs.edit().putInt("native_fps", 30).apply()
            refreshStatus()
            toast("Native FPS 30 पर सेट")
        })
        root.addView(makeButton("60 FPS (ज्यादा स्मूद, ज्यादा लोड)") {
            prefs.edit().putInt("native_fps", 60).apply()
            refreshStatus()
            toast("Native FPS 60 पर सेट")
        })

        root.addView(makeButton("Live Wallpaper Preview / Set करें") { openWallpaperPicker() })
        root.addView(makeButton("Offline ZIP Project हटाएँ") { clearLocal() })

        status = TextView(this).apply {
            textSize = 13f
            setPadding(0, 16, 0, 0)
        }
        root.addView(status)
        refreshStatus()
        setContentView(root)
    }

    private fun makeButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }

    private fun saveUrlAndSelectHtml() {
        val value = urlInput.text.toString().trim()
        if (!(value.startsWith("https://") || value.startsWith("http://"))) {
            toast("कृपया http:// या https:// वाला URL डालें")
            return
        }
        prefs.edit()
            .putString("source_url", value)
            .putString("source_type", "url")
            .putString("renderer_mode", "html")
            .remove("local_path")
            .apply()
        refreshStatus()
        toast("URL सेव हुआ और HTML Mode चुना गया")
    }

    private fun selectMode(mode: String) {
        prefs.edit().putString("renderer_mode", mode).apply()
        refreshStatus()
        toast(if (mode == "native") "Native Matrix Mode चुना गया" else "HTML WebView Mode चुना गया")
    }

    private fun chooseZip() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
        }
        startActivityForResult(intent, pickZip)
    }

    @Deprecated("Deprecated by platform; retained for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != pickZip || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val target = File(filesDir, "wallpaper_project")
            if (target.exists()) target.deleteRecursively()
            target.mkdirs()
            contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "ZIP नहीं पढ़ पाई" }
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    var total = 0L
                    while (entry != null) {
                        val out = File(target, entry.name).canonicalFile
                        require(out.path.startsWith(target.canonicalPath + File.separator) || out == target) {
                            "ZIP में असुरक्षित file path मिला"
                        }
                        if (entry.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile?.mkdirs()
                            out.outputStream().use { output ->
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val count = zip.read(buffer)
                                    if (count <= 0) break
                                    total += count
                                    require(total <= 100L * 1024 * 1024) { "ZIP 100 MB से बड़ी है" }
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            val index = findIndexHtml(target)
            require(index != null) { "ZIP के अंदर index.html नहीं मिली" }
            prefs.edit()
                .putString("source_type", "local")
                .putString("local_path", index.absolutePath)
                .putString("renderer_mode", "html")
                .apply()
            refreshStatus()
            toast("ZIP import हुई और HTML Mode चुना गया")
        } catch (e: Exception) {
            toast("Import असफल: ${e.message}")
        }
    }

    private fun findIndexHtml(dir: File): File? {
        val direct = File(dir, "index.html")
        if (direct.exists()) return direct
        return dir.walkTopDown().firstOrNull { it.isFile && it.name.equals("index.html", true) }
    }

    private fun openWallpaperPicker() {
        val component = ComponentName(this, HtmlWallpaperService::class.java)
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
        }
        startActivity(intent)
    }

    private fun clearLocal() {
        File(filesDir, "wallpaper_project").deleteRecursively()
        prefs.edit().remove("local_path").putString("source_type", "url").apply()
        refreshStatus()
        toast("लोकल ZIP प्रोजेक्ट हटाया गया")
    }

    private fun refreshStatus() {
        if (!::status.isInitialized) return
        val mode = prefs.getString("renderer_mode", "html")
        val source = prefs.getString("source_type", "url")
        val location = if (source == "local") {
            "ZIP: ${prefs.getString("local_path", "unknown")}"
        } else {
            "URL: ${prefs.getString("source_url", "अभी URL सेट नहीं है")}"
        }
        status.text = "Current Mode: ${if (mode == "native") "Native Matrix" else "HTML WebView"}\n$location\nNative FPS: ${prefs.getInt("native_fps", 30)}"
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
