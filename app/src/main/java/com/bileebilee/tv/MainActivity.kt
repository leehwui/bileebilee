package com.bileebilee.tv

import android.app.Activity
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private lateinit var diagnosticsPanel: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var focusStatus: TextView
    private lateinit var refreshButton: Button
    private lateinit var liveButton: Button
    private lateinit var playerView: PlayerView
    private lateinit var playerHint: TextView

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var player: ExoPlayer? = null
    private var isFetchingStream = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        diagnosticsPanel = findViewById(R.id.diagnostics_panel)
        statusText = findViewById(R.id.status_text)
        focusStatus = findViewById(R.id.focus_status)
        refreshButton = findViewById(R.id.refresh_button)
        liveButton = findViewById(R.id.live_button)
        refreshButton.isAllCaps = false
        liveButton.isAllCaps = false
        playerView = findViewById(R.id.player_view)
        playerHint = findViewById(R.id.player_hint)

        refreshButton.setOnClickListener { refreshDiagnostics() }
        liveButton.setOnClickListener { startLiveStreamTest() }
        installFocusFeedback(refreshButton, getString(R.string.refresh_diagnostics))
        installFocusFeedback(liveButton, getString(R.string.test_live_stream))

        refreshDiagnostics()
        refreshButton.requestFocus()
    }

    private fun installFocusFeedback(button: Button, label: String) {
        button.setOnFocusChangeListener { view, hasFocus ->
            view.animate()
                .scaleX(if (hasFocus) 1.06f else 1f)
                .scaleY(if (hasFocus) 1.06f else 1f)
                .setDuration(120L)
                .start()
            if (hasFocus) focusStatus.text = "Focused: $label • Press OK / Enter"
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && ::focusStatus.isInitialized) {
            val focusedLabel = when (currentFocus?.id) {
                R.id.refresh_button -> getString(R.string.refresh_diagnostics)
                R.id.live_button -> getString(R.string.test_live_stream)
                else -> "none"
            }
            focusStatus.text = "Input: ${KeyEvent.keyCodeToString(event.keyCode)} • Focused: $focusedLabel"
        }
        return super.dispatchKeyEvent(event)
    }

    private fun refreshDiagnostics() {
        statusText.text = buildString {
            appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android: ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Display: ${resources.displayMetrics.widthPixels}x${resources.displayMetrics.heightPixels}")
            appendLine()
            appendLine("Hardware video decoders:")
            append(codecSummary())
            appendLine()
            appendLine("D-pad input: ready")
            appendLine("Network: press “Test public live stream”")
        }
    }

    private fun codecSummary(): String {
        val wantedTypes = setOf("video/avc", "video/hevc")
        val matches = mutableListOf<String>()
        return try {
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
                .filterNot(MediaCodecInfo::isEncoder)
                .forEach { codec ->
                    codec.supportedTypes
                        .filter { it.lowercase() in wantedTypes }
                        .forEach { type -> matches += "• $type — ${codec.name}" }
                }
            if (matches.isEmpty()) "• No AVC/HEVC decoder reported\n" else matches.joinToString("\n", postfix = "\n")
        } catch (error: Exception) {
            "• Could not enumerate codecs: ${error.message}\n"
        }
    }

    private fun startLiveStreamTest() {
        if (isFetchingStream) return
        isFetchingStream = true
        liveButton.isEnabled = false
        statusText.append("\nFinding an active Bilibili live room…")

        val roomListRequest = Request.Builder()
            .url("https://api.live.bilibili.com/room/v3/area/getRoomList?parent_area_id=0&area_id=0&page=1&page_size=20&sort_type=online")
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://live.bilibili.com/")
            .build()

        httpClient.newCall(roomListRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) = failLiveTest("Room list failed", error)

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) return failLiveTest("Room list HTTP ${it.code}")
                    val body = it.body?.string().orEmpty()
                    val rooms = JSONObject(body).optJSONObject("data")?.optJSONArray("list")
                    val roomId = rooms?.firstLong("roomid")
                        ?: return failLiveTest("No active live room was returned")
                    fetchPlayableStream(roomId)
                }
            }
        })
    }

    private fun fetchPlayableStream(roomId: Long) {
        val url = "https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo" +
            "?room_id=$roomId&protocol=0,1&format=0,1,2&codec=0&qn=80&platform=web&ptype=8"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://live.bilibili.com/$roomId")
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) = failLiveTest("Stream lookup failed", error)

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) return failLiveTest("Stream lookup HTTP ${it.code}")
                    val root = JSONObject(it.body?.string().orEmpty())
                    if (root.optInt("code", -1) != 0) {
                        return failLiveTest("Bilibili API: ${root.optString("message", "unknown error")}")
                    }
                    val playurl = root.optJSONObject("data")
                        ?.optJSONObject("playurl_info")
                        ?.optJSONObject("playurl")
                        ?: return failLiveTest("Playback data was missing")
                    val streamUrl = selectStreamUrl(playurl.optJSONArray("stream"))
                        ?: return failLiveTest("No AVC playback URL was returned")
                    runOnUiThread { play(roomId, streamUrl) }
                }
            }
        })
    }

    private fun selectStreamUrl(streams: JSONArray?): String? {
        if (streams == null) return null
        val candidates = mutableListOf<StreamCandidate>()
        for (streamIndex in 0 until streams.length()) {
            val stream = streams.optJSONObject(streamIndex) ?: continue
            val protocol = stream.optString("protocol_name")
            val formats = stream.optJSONArray("format") ?: continue
            for (formatIndex in 0 until formats.length()) {
                val format = formats.optJSONObject(formatIndex) ?: continue
                val formatName = format.optString("format_name")
                val codecs = format.optJSONArray("codec") ?: continue
                for (codecIndex in 0 until codecs.length()) {
                    val codec = codecs.optJSONObject(codecIndex) ?: continue
                    if (codec.optString("codec_name") != "avc") continue
                    val baseUrl = codec.optString("base_url")
                    val urlInfo = codec.optJSONArray("url_info")?.optJSONObject(0) ?: continue
                    val fullUrl = urlInfo.optString("host") + baseUrl + urlInfo.optString("extra")
                    if (fullUrl.startsWith("http")) {
                        val rank = when {
                            protocol.contains("hls") && formatName.contains("ts") -> 0
                            protocol.contains("hls") -> 1
                            formatName.contains("flv") -> 2
                            else -> 3
                        }
                        candidates += StreamCandidate(rank, fullUrl)
                    }
                }
            }
        }
        return candidates.minByOrNull(StreamCandidate::rank)?.url
    }

    @androidx.annotation.OptIn(markerClass = [UnstableApi::class])
    private fun play(roomId: Long, url: String) {
        isFetchingStream = false
        liveButton.isEnabled = true

        val requestHeaders = mapOf(
            "Referer" to "https://live.bilibili.com/$roomId",
            "Origin" to "https://live.bilibili.com"
        )
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setDefaultRequestProperties(requestHeaders)
            .setAllowCrossProtocolRedirects(true)

        stopPlayback()
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .also { exoPlayer ->
                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        showDiagnostics("Playback failed: ${error.errorCodeName}\n${error.message.orEmpty()}")
                    }
                })
                playerView.player = exoPlayer
                diagnosticsPanel.visibility = View.GONE
                playerView.visibility = View.VISIBLE
                playerHint.visibility = View.VISIBLE
                exoPlayer.setMediaItem(MediaItem.fromUri(url))
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }
    }

    private fun stopPlayback() {
        playerView.player = null
        player?.release()
        player = null
        playerView.visibility = View.GONE
        playerHint.visibility = View.GONE
        diagnosticsPanel.visibility = View.VISIBLE
    }

    private fun showDiagnostics(message: String) {
        stopPlayback()
        statusText.append("\n$message")
        liveButton.requestFocus()
    }

    private fun failLiveTest(message: String, error: Throwable? = null) {
        runOnUiThread {
            isFetchingStream = false
            liveButton.isEnabled = true
            statusText.append("\n$message${error?.message?.let { ": $it" }.orEmpty()}")
            liveButton.requestFocus()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && player != null) {
            stopPlayback()
            liveButton.requestFocus()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (player != null) {
            stopPlayback()
            liveButton.requestFocus()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }

    private fun JSONArray.firstLong(key: String): Long? {
        for (index in 0 until length()) {
            val value = optJSONObject(index)?.optLong(key, 0L) ?: 0L
            if (value > 0L) return value
        }
        return null
    }

    private data class StreamCandidate(val rank: Int, val url: String)

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 5.1; BileebileeTV/0.1) AppleWebKit/537.36 Mobile Safari/537.36"
    }
}
