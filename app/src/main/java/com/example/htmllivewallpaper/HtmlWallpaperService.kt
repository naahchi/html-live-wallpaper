package com.example.htmllivewallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File

class HtmlWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = HtmlEngine()

    inner class HtmlEngine : Engine() {
        private val mainHandler = Handler(Looper.getMainLooper())
        private var webView: WebView? = null
        private var bitmap: Bitmap? = null
        private var visibleNow = false
        private var frameDelay = 1000L / 15L
        private var widthPx = 0
        private var heightPx = 0

        private val drawTask = object : Runnable {
            override fun run() {
                if (!visibleNow) return
                renderFrame()
                mainHandler.postDelayed(this, frameDelay)
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
            wv.setBackgroundColor(android.graphics.Color.BLACK)
            wv.setLayerType(WebView.LAYER_TYPE_SOFTWARE, null)
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
            wv.webViewClient = object : WebViewClient() {}
            val prefs = getSharedPreferences("wallpaper_settings", MODE_PRIVATE)
            if (prefs.getString("source_type", "url") == "local") {
                val path = prefs.getString("local_path", null)
                if (path != null && File(path).exists()) {
                    wv.loadUrl(UriHelper.fileUrl(path))
                } else {
                    wv.loadData("<html><body style='background:#111;color:white;font:20px sans-serif'>ZIP project नहीं मिली। ऐप खोलकर ZIP फिर से import करें।</body></html>", "text/html", "UTF-8")
                }
            } else {
                val url = prefs.getString("source_url", null)
                if (!url.isNullOrBlank()) wv.loadUrl(url)
                else wv.loadData("<html><body style='background:#111;color:white;font:20px sans-serif'>ऐप खोलकर Wallpaper URL सेट करें।</body></html>", "text/html", "UTF-8")
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            visibleNow = visible
            mainHandler.removeCallbacks(drawTask)
            if (visible) mainHandler.post(drawTask)
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
                mainHandler.removeCallbacks(drawTask)
                mainHandler.post(drawTask)
            }
        }

        private fun renderFrame() {
            val wv = webView ?: return
            val bmp = bitmap ?: return
            if (widthPx <= 0 || heightPx <= 0) return
            try {
                wv.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(widthPx, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(heightPx, android.view.View.MeasureSpec.EXACTLY)
                )
                wv.layout(0, 0, widthPx, heightPx)
                val viewCanvas = Canvas(bmp)
                viewCanvas.drawColor(android.graphics.Color.BLACK)
                wv.draw(viewCanvas)
                val holder = surfaceHolder
                val canvas = holder.lockCanvas()
                if (canvas != null) {
                    try {
                        canvas.drawBitmap(bmp, 0f, 0f, null)
                    } finally {
                        holder.unlockCanvasAndPost(canvas)
                    }
                }
            } catch (_: Exception) {
                // Surface may be unavailable while the launcher changes screens.
            }
        }

        override fun onDestroy() {
            visibleNow = false
            mainHandler.removeCallbacks(drawTask)
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
}

private object UriHelper {
    fun fileUrl(path: String): String = "file://${File(path).absolutePath}"
}
