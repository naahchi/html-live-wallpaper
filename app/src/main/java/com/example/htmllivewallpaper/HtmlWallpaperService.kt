package com.example.htmllivewallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File
import kotlin.random.Random

class HtmlWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine {
        val mode = getSharedPreferences("wallpaper_settings", MODE_PRIVATE)
            .getString("renderer_mode", "html")
        return if (mode == "native") NativeMatrixEngine() else HtmlEngine()
    }

    /**
     * HTML mode remains a compatibility renderer for arbitrary web projects.
     * It uses a bounded bitmap copy because WallpaperService does not provide
     * a normal Activity window in which a WebView can be attached.
     */
    inner class HtmlEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private var webView: WebView? = null
        private var bitmap: Bitmap? = null
        private var visibleNow = false
        private var widthPx = 0
        private var heightPx = 0
        private val frameDelay = 1000L / 24L

        private val drawTask = object : Runnable {
            override fun run() {
                if (!visibleNow) return
                renderFrame()
                handler.postDelayed(this, frameDelay)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(false)
            createWebView()
        }

        private fun createWebView() {
            val wv = WebView(applicationContext)
            webView = wv
            wv.setBackgroundColor(Color.BLACK)
            // Do not force SOFTWARE rendering. Keep WebView's normal rendering
            // configuration; bitmap transfer remains a limitation of this mode.
            wv.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                allowFileAccess = true
                allowContentAccess = false
                loadsImagesAutomatically = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
            wv.webChromeClient = WebChromeClient()
            wv.webViewClient = WebViewClient()

            val prefs = getSharedPreferences("wallpaper_settings", MODE_PRIVATE)
            if (prefs.getString("source_type", "url") == "local") {
                val path = prefs.getString("local_path", null)
                if (path != null && File(path).exists()) {
                    wv.loadUrl("file://${File(path).absolutePath}")
                } else {
                    wv.loadData(errorHtml("ZIP project नहीं मिली। ऐप खोलकर ZIP फिर से import करें।"), "text/html", "UTF-8")
                }
            } else {
                val url = prefs.getString("source_url", null)
                if (!url.isNullOrBlank()) {
                    wv.loadUrl(url)
                } else {
                    wv.loadData(errorHtml("ऐप खोलकर Wallpaper URL सेव करें।"), "text/html", "UTF-8")
                }
            }
        }

        private fun errorHtml(message: String) =
            "<html><body style='background:#111;color:white;font:18px sans-serif;padding:20px'>$message</body></html>"

        override fun onVisibilityChanged(visible: Boolean) {
            visibleNow = visible
            handler.removeCallbacks(drawTask)
            if (visible) handler.post(drawTask)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            widthPx = width.coerceAtLeast(1)
            heightPx = height.coerceAtLeast(1)
            webView?.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(widthPx, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(heightPx, android.view.View.MeasureSpec.EXACTLY)
            )
            webView?.layout(0, 0, widthPx, heightPx)
            bitmap?.recycle()
            bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            if (visibleNow) {
                handler.removeCallbacks(drawTask)
                handler.post(drawTask)
            }
        }

        private fun renderFrame() {
            val wv = webView ?: return
            val bmp = bitmap ?: return
            if (widthPx <= 0 || heightPx <= 0 || bmp.isRecycled) return
            try {
                wv.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(widthPx, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(heightPx, android.view.View.MeasureSpec.EXACTLY)
                )
                wv.layout(0, 0, widthPx, heightPx)
                val viewCanvas = Canvas(bmp)
                viewCanvas.drawColor(Color.BLACK)
                wv.draw(viewCanvas)

                val canvas = surfaceHolder.lockCanvas()
                if (canvas != null) {
                    try {
                        canvas.drawBitmap(bmp, 0f, 0f, null)
                    } finally {
                        surfaceHolder.unlockCanvasAndPost(canvas)
                    }
                }
            } catch (_: Exception) {
                // Surface may be unavailable while launcher changes screens.
            }
        }

        override fun onDestroy() {
            visibleNow = false
            handler.removeCallbacks(drawTask)
            webView?.apply {
                stopLoading()
                loadUrl("about:blank")
                destroy()
            }
            webView = null
            bitmap?.recycle()
            bitmap = null
            super.onDestroy()
        }
    }

    /**
     * Native Matrix renderer: draws directly into the wallpaper surface using
     * Android's hardware Canvas when available, with a software Canvas fallback.
     * It does not load a website or execute HTML/JavaScript.
     */
    inner class NativeMatrixEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(190, 255, 205)
            textAlign = Paint.Align.CENTER
            isSubpixelText = true
        }
        private val trailPaint = Paint().apply { color = Color.BLACK }
        private val glyphs = "अआइईउऊऋएऐओऔकखगघचछजझटठडढतथदधनपफबभमयरलवशषसह0123456789"
        private var visibleNow = false
        private var widthPx = 0
        private var heightPx = 0
        private var textSizePx = 30f
        private var columnCount = 0
        private var heads = FloatArray(0)
        private var speeds = FloatArray(0)
        private var fps = 30
        private var frameDelay = 1000L / 30L
        private var lastFrameNs = 0L

        private val drawTask = object : Runnable {
            override fun run() {
                if (!visibleNow) return
                drawNativeFrame()
                handler.postDelayed(this, frameDelay)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(false)
            val configuredFps = getSharedPreferences("wallpaper_settings", MODE_PRIVATE)
                .getInt("native_fps", 30)
            fps = if (configuredFps >= 60) 60 else 30
            frameDelay = 1000L / fps
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            widthPx = width.coerceAtLeast(1)
            heightPx = height.coerceAtLeast(1)
            textSizePx = (widthPx / 24f).coerceIn(20f, 42f)
            paint.textSize = textSizePx
            columnCount = (widthPx / textSizePx).toInt().coerceAtLeast(1) + 1
            heads = FloatArray(columnCount) { Random.nextFloat() * heightPx }
            speeds = FloatArray(columnCount) { textSizePx * (5f + Random.nextFloat() * 10f) }
            if (visibleNow) {
                handler.removeCallbacks(drawTask)
                handler.post(drawTask)
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            visibleNow = visible
            handler.removeCallbacks(drawTask)
            if (visible) {
                lastFrameNs = 0L
                handler.post(drawTask)
            }
        }

        private fun drawNativeFrame() {
            if (widthPx <= 0 || heightPx <= 0 || columnCount <= 0) return
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockHardwareCanvas()
            } catch (_: Exception) {
                try {
                    canvas = holder.lockCanvas()
                } catch (_: Exception) {
                    return
                }
            }

            if (canvas == null) return
            try {
                canvas.drawColor(Color.BLACK)
                val now = System.nanoTime()
                val deltaSeconds = if (lastFrameNs == 0L) {
                    1f / fps
                } else {
                    ((now - lastFrameNs) / 1_000_000_000f).coerceIn(0f, 0.05f)
                }
                lastFrameNs = now

                for (column in 0 until columnCount) {
                    val x = column * textSizePx + textSizePx * 0.5f
                    val headY = heads[column]
                    val trailLength = 10
                    for (trail in 0 until trailLength) {
                        val y = headY - trail * textSizePx
                        if (y < -textSizePx || y > heightPx + textSizePx) continue
                        val alpha = (235 - trail * 21).coerceIn(25, 235)
                        paint.color = if (trail == 0) Color.rgb(225, 255, 230)
                            else Color.argb(alpha, 75, 210, 115)
                        val glyph = glyphs[Random.nextInt(glyphs.length)].toString()
                        canvas.drawText(glyph, x, y, paint)
                    }
                    heads[column] += speeds[column] * deltaSeconds
                    if (heads[column] - trailLength * textSizePx > heightPx) {
                        heads[column] = -Random.nextFloat() * heightPx * 0.5f
                        speeds[column] = textSizePx * (5f + Random.nextFloat() * 10f)
                    }
                }
            } catch (_: Exception) {
                // Ignore transient surface errors.
            } finally {
                try {
                    holder.unlockCanvasAndPost(canvas)
                } catch (_: Exception) {
                    // Surface can be destroyed during a launcher transition.
                }
            }
        }

        override fun onDestroy() {
            visibleNow = false
            handler.removeCallbacks(drawTask)
            super.onDestroy()
        }
    }
}
