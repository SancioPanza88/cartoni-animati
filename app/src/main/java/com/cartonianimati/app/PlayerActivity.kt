package com.cartonianimati.app

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.ByteArrayInputStream

class PlayerActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    private var webView: WebView? = null
    private var useWebView = false

    // Block ad / tracker / popunder domains to make embeds load cleanly.
    private val adPatterns = listOf(
        "doubleclick.net", "googleadservices.com", "googlesyndication.com",
        "google-analytics.com", "googletagmanager.com", "adservice.google.com",
        "pagead2.googlesyndication.com", "adsystem.com", "adnxs.com", "popads.net",
        "propellerads.com", "outbrain.com", "taboola.com", "scorecardresearch.com",
        "criteo.com", "pubmatic.com", "rubiconproject.com", "openx.net",
        "amazon-adsystem.com", "2mdn.net", "moatads.com", "adsrvr.org",
        "smartadserver.com", "innity.com", "popcash.net", "vidoza", "exoclick",
        "clickbank", "mgid.com", "revcontent.com", "shareus", "ytimg.com",
        "banner", "ads.", ".ads", "ad.", "tracker", "analytics", "beacons",
        "pixel", "tag.", "counter", "popunder", "preroll", "vast", "imasdk"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        val videoUrl = intent.getStringExtra("video_url") ?: ""
        val title = intent.getStringExtra("title") ?: ""

        if (videoUrl.isNotBlank()) {
            resolveAndPlay(videoUrl, title)
        } else {
            Toast.makeText(this, "URL non valido", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun isDirectStream(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".m3u8") || lower.contains(".mp4") || lower.contains(".mkv") ||
                lower.contains(".webm") || lower.contains(".ts") || lower.contains(".mov")
    }

    private fun resolveAndPlay(url: String, title: String) {
        if (isDirectStream(url)) {
            showExoPlayer()
            playVideo(url)
            return
        }
        // Any embed host (uqload, chuckle-tube, voe, vidhide, rpmplay, filelions, ...)
        // is opened in a WebView so the site's own player handles playback.
        Toast.makeText(this, "Caricamento player...", Toast.LENGTH_SHORT).show()
        showWebView(url, title)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showWebView(url: String, title: String) {
        useWebView = true
        val wv = WebView(this)
        wv.setBackgroundColor(Color.BLACK)
        val settings = wv.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
        @Suppress("DEPRECATION")
        settings.pluginState = WebSettings.PluginState.ON
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)

        wv.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                // Allow popups (many embeds open the player in a new window)
                val newWebView = WebView(this@PlayerActivity)
                newWebView.settings.javaScriptEnabled = true
                newWebView.settings.javaScriptCanOpenWindowsAutomatically = true
                newWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val u = request?.url?.toString() ?: view?.url ?: ""
                        if (u.isNotBlank() && !isAdRequest(u)) showWebView(u, title)
                        return true
                    }
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        if (url != null && !isAdRequest(url)) showWebView(url, title)
                        return true
                    }
                }
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = newWebView
                resultMsg?.sendToTarget()
                return true
            }
        }
        wv.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val u = request?.url?.toString() ?: return null
                if (isAdRequest(u)) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
                }
                return null
            }

            @Deprecated("Deprecated in Java")
            override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? {
                if (url != null && isAdRequest(url)) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
                }
                return null
            }

            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                runOnUiThread {
                    Toast.makeText(this@PlayerActivity, "Impossibile caricare il player. Prova un altro server.", Toast.LENGTH_LONG).show()
                }
            }
        }
        wv.loadUrl(url)
        webView = wv
        setContentView(wv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun isAdRequest(url: String): Boolean {
        val lower = url.lowercase()
        return adPatterns.any { it in lower }
    }

    private fun showExoPlayer() {
        useWebView = false
        val playerView = PlayerView(this)
        playerView.setBackgroundColor(Color.BLACK)
        setContentView(playerView)
        player = ExoPlayer.Builder(this).build()
        playerView.player = player
    }

    private fun playVideo(url: String) {
        val mediaItem = MediaItem.fromUri(Uri.parse(url))
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.playWhenReady = true
        player?.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                runOnUiThread {
                    Toast.makeText(this@PlayerActivity, "Errore riproduzione: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
        if (useWebView) webView?.onPause()
    }

    @Suppress("DEPRECATION")
    override fun onResume() {
        super.onResume()
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        if (useWebView) webView?.onResume()
    }

    override fun onBackPressed() {
        if (useWebView && webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
        webView?.destroy()
        webView = null
    }
}
