package com.example.htmllivewallpaper

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
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
            setPadding(28, 32, 28, 24)
        }
        root.addView(TextView(this).apply {
            text = "HTML Live Wallpaper"
            textSize = 24f
        })
        root.addView(TextView(this).apply {
            text = "Direct website URL या ZIP में HTML/CSS/JS प्रोजेक्ट चुनें। ZIP में मुख्य फाइल index.html होनी चाहिए।"
            textSize = 15f
            setPadding(0, 14, 0, 18)
        })
        urlInput = EditText(this).apply {
            hint = "https://example.com/wallpaper/"
            setSingleLine(true)
            setText(prefs.getString("source_url", ""))
        }
        root.addView(urlInput, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(makeButton("URL सेव करें", ::saveUrl))
        root.addView(makeButton("HTML प्रोजेक्ट ZIP चुनें", ::chooseZip))
        root.addView(makeButton("Live Wallpaper सेट करें", ::openWallpaperPicker))
        root.addView(makeButton("लोकल Wallpaper हटाएँ", ::clearLocal))
        status = TextView(this).apply {
            textSize = 14f
            setPadding(0, 18, 0, 0)
            text = currentStatus()
        }
        root.addView(status)
        setContentView(root)
    }

    private fun makeButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }

    private fun saveUrl() {
        val value = urlInput.text.toString().trim()
        if (!(value.startsWith("https://") || value.startsWith("http://"))) {
            toast("कृपया http:// या https:// वाला URL डालें")
            return
        }
        prefs.edit().putString("source_url", value).putString("source_type", "url")
            .remove("local_path").apply()
        status.text = "URL सेव हो गया। अब Live Wallpaper सेट करें।"
        toast("URL सेव हो गया")
    }

    private fun chooseZip() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
        }
        startActivityForResult(intent, pickZip)
    }

    @Deprecated("Deprecated by platform; kept for broad Android compatibility")
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
                        if (entry.isDirectory) out.mkdirs() else {
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
            prefs.edit().putString("source_type", "local")
                .putString("local_path", index.absolutePath).apply()
            status.text = "ZIP import हो गई: ${index.relativeTo(target).path}"
            toast("Wallpaper प्रोजेक्ट import हो गया")
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
        status.text = currentStatus()
        toast("लोकल ZIP प्रोजेक्ट हटाया गया")
    }

    private fun currentStatus(): String {
        return if (prefs.getString("source_type", "url") == "local") {
            "Mode: Offline ZIP\n${prefs.getString("local_path", "") ?: ""}"
        } else {
            "Mode: Website URL\n${prefs.getString("source_url", "अभी URL सेट नहीं है")}"
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
