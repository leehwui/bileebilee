package com.bileebilee.tv

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
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
    private lateinit var recommendationsButton: Button
    private lateinit var liveButton: Button
    private lateinit var loginButton: Button
    private lateinit var loginPanel: LinearLayout
    private lateinit var qrImage: ImageView
    private lateinit var loginStatus: TextView
    private lateinit var newQrButton: Button
    private lateinit var playerView: PlayerView
    private lateinit var playerHint: TextView
    private lateinit var recommendationsPanel: LinearLayout
    private lateinit var recommendationsStatus: TextView
    private lateinit var recommendationsGrid: GridLayout
    private lateinit var refreshRecommendationsButton: Button
    private lateinit var authClient: BilibiliAuthClient

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var player: ExoPlayer? = null
    private var isFetchingStream = false
    private var accountStatus = "Account: checking…"
    private var qrKey: String? = null
    private var qrCall: Call? = null
    private var accountCall: Call? = null
    private var recommendationsCall: Call? = null
    private var videoCall: Call? = null
    private var playbackReturnsToRecommendations = false
    private var recommendationReturnFocus: View? = null
    private val coverCalls = mutableListOf<Call>()
    private val coverCache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val qrPollRunnable = Runnable { qrKey?.let(::pollQr) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        diagnosticsPanel = findViewById(R.id.diagnostics_panel)
        statusText = findViewById(R.id.status_text)
        focusStatus = findViewById(R.id.focus_status)
        recommendationsButton = findViewById(R.id.recommendations_button)
        liveButton = findViewById(R.id.live_button)
        loginButton = findViewById(R.id.login_button)
        loginPanel = findViewById(R.id.login_panel)
        qrImage = findViewById(R.id.qr_image)
        loginStatus = findViewById(R.id.login_status)
        newQrButton = findViewById(R.id.new_qr_button)
        recommendationsPanel = findViewById(R.id.recommendations_panel)
        recommendationsStatus = findViewById(R.id.recommendations_status)
        recommendationsGrid = findViewById(R.id.recommendations_grid)
        refreshRecommendationsButton = findViewById(R.id.refresh_recommendations_button)
        recommendationsButton.isAllCaps = false
        liveButton.isAllCaps = false
        loginButton.isAllCaps = false
        newQrButton.isAllCaps = false
        playerView = findViewById(R.id.player_view)
        playerHint = findViewById(R.id.player_hint)
        authClient = BilibiliAuthClient(this, httpClient)

        recommendationsButton.setOnClickListener { showRecommendations() }
        liveButton.setOnClickListener { startLiveStreamTest() }
        loginButton.setOnClickListener { startQrLogin() }
        newQrButton.setOnClickListener { startQrLogin() }
        refreshRecommendationsButton.setOnClickListener { loadRecommendations() }
        refreshRecommendationsButton.isAllCaps = false
        installFocusFeedback(recommendationsButton, getString(R.string.recommendations))
        installFocusFeedback(liveButton, getString(R.string.test_live_stream))
        installFocusFeedback(loginButton, getString(R.string.qr_login))
        installFocusFeedback(newQrButton, getString(R.string.new_qr_code))

        refreshDiagnostics()
        checkAccount()
        recommendationsButton.requestFocus()
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
                R.id.recommendations_button -> getString(R.string.recommendations)
                R.id.live_button -> getString(R.string.test_live_stream)
                R.id.login_button -> getString(R.string.qr_login)
                R.id.new_qr_button -> getString(R.string.new_qr_code)
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
            appendLine(accountStatus)
            appendLine()
            appendLine("Hardware video decoders:")
            append(codecSummary())
            appendLine()
            appendLine("D-pad input: ready")
            appendLine("Network: press “Test public live stream”")
        }
    }

    private fun showRecommendations() {
        diagnosticsPanel.visibility = View.GONE
        loginPanel.visibility = View.GONE
        recommendationsPanel.visibility = View.VISIBLE
        refreshRecommendationsButton.requestFocus()
        loadRecommendations()
    }

    private fun hideRecommendations() {
        recommendationsCall?.cancel()
        videoCall?.cancel()
        recommendationsPanel.visibility = View.GONE
        diagnosticsPanel.visibility = View.VISIBLE
        recommendationsButton.requestFocus()
    }

    private fun loadRecommendations() {
        recommendationsCall?.cancel()
        recommendationsStatus.text = "Loading the mobile recommendation feed…"
        refreshRecommendationsButton.isEnabled = false
        recommendationsCall = authClient.fetchRecommendations { result ->
            runOnUiThread {
                refreshRecommendationsButton.isEnabled = true
                result.fold(
                    onSuccess = { videos ->
                        renderRecommendations(videos)
                        recommendationsStatus.text = if (videos.isEmpty()) {
                            "No playable videos were returned."
                        } else {
                            "${videos.size} videos • Press OK to play • Back for device tools"
                        }
                        if (videos.isNotEmpty()) {
                            recommendationsGrid.post { recommendationsGrid.getChildAt(0)?.requestFocus() }
                        }
                    },
                    onFailure = { error ->
                        recommendationsStatus.text =
                            "Recommendation request failed: ${error.message.orEmpty()}"
                        refreshRecommendationsButton.requestFocus()
                    }
                )
            }
        }
    }

    private fun renderRecommendations(videos: List<BilibiliAuthClient.Recommendation>) {
        coverCalls.forEach(Call::cancel)
        coverCalls.clear()
        recommendationsGrid.removeAllViews()
        recommendationReturnFocus = null
        val horizontalPadding = dp(96)
        val cardWidth = (resources.displayMetrics.widthPixels - horizontalPadding) / 4
        videos.forEach { video ->
            val card = LayoutInflater.from(this)
                .inflate(R.layout.recommendation_card, recommendationsGrid, false)
            val cover = card.findViewById<ImageView>(R.id.recommendation_cover)
            card.findViewById<TextView>(R.id.recommendation_title).text = video.title
            card.findViewById<TextView>(R.id.recommendation_duration).text = video.duration
            card.findViewById<TextView>(R.id.recommendation_meta).text =
                listOf(video.uploader, video.viewCount)
                    .filter(String::isNotBlank)
                    .joinToString("  •  ")
            card.contentDescription = listOf(video.title, video.uploader, video.duration)
                .filter(String::isNotBlank)
                .joinToString(", ")
            card.setOnClickListener {
                recommendationReturnFocus = card
                playRecommendation(video)
            }
            card.setOnFocusChangeListener { view, hasFocus ->
                view.animate()
                    .scaleX(if (hasFocus) 1.055f else 1f)
                    .scaleY(if (hasFocus) 1.055f else 1f)
                    .setDuration(120L)
                    .start()
                view.elevation = if (hasFocus) 18f else 0f
            }
            recommendationsGrid.addView(
                card,
                GridLayout.LayoutParams().apply {
                    width = cardWidth
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                }
            )
            loadCover(video.coverUrl, cover)
        }
    }

    private fun loadCover(url: String, imageView: ImageView) {
        imageView.tag = url
        coverCache.get(url)?.let {
            imageView.setImageBitmap(it)
            return
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://www.bilibili.com/")
            .build()
        val call = httpClient.newCall(request)
        coverCalls += call
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) = Unit

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) return
                    val bytes = it.body?.bytes() ?: return
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
                    coverCache.put(url, bitmap)
                    runOnUiThread {
                        if (imageView.tag == url) imageView.setImageBitmap(bitmap)
                    }
                }
            }
        })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun restoreRecommendationFocus() {
        val target = recommendationReturnFocus
            ?.takeIf { it.parent === recommendationsGrid }
            ?: recommendationsGrid.getChildAt(0)
        target?.requestFocus()
    }

    private fun playRecommendation(video: BilibiliAuthClient.Recommendation) {
        videoCall?.cancel()
        recommendationsStatus.text = "Opening ${video.title}…"
        videoCall = authClient.fetchVideoUrl(video) { result ->
            runOnUiThread {
                result.fold(
                    onSuccess = { url ->
                        playerHint.text = getString(R.string.video_player_hint)
                        playMedia(
                            url = url,
                            referer = "https://www.bilibili.com/video/av${video.aid}",
                            returnToRecommendations = true
                        )
                    },
                    onFailure = { error ->
                        recommendationsStatus.text =
                            "Could not play video: ${error.message.orEmpty()}"
                    }
                )
            }
        }
    }

    private fun checkAccount() {
        accountCall?.cancel()
        accountCall = authClient.checkSession { result ->
            runOnUiThread {
                accountStatus = result.fold(
                    onSuccess = { account ->
                        account?.let { "Account: ${it.name} (UID ${it.mid})" }
                            ?: "Account: not signed in"
                    },
                    onFailure = { error -> "Account check failed: ${error.message.orEmpty()}" }
                )
                refreshDiagnostics()
            }
        }
    }

    private fun startQrLogin() {
        cancelQrLogin()
        stopPlayback()
        loginPanel.visibility = View.VISIBLE
        qrImage.setImageDrawable(null)
        loginStatus.text = "Requesting a QR code…"
        newQrButton.isEnabled = false
        newQrButton.requestFocus()

        qrCall = authClient.generateQr { result ->
            runOnUiThread {
                result.fold(
                    onSuccess = { challenge ->
                        qrKey = challenge.key
                        qrImage.setImageBitmap(createQrBitmap(challenge.url))
                        loginStatus.text = getString(R.string.qr_login_instructions)
                        newQrButton.isEnabled = true
                        scheduleQrPoll()
                    },
                    onFailure = { error ->
                        loginStatus.text = "Could not create QR code: ${error.message.orEmpty()}"
                        newQrButton.isEnabled = true
                    }
                )
            }
        }
    }

    private fun createQrBitmap(value: String): Bitmap {
        val size = 720
        val matrix = QRCodeWriter().encode(
            value,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(EncodeHintType.MARGIN to 2)
        )
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, size, 0, 0, size, size)
        }
    }

    private fun scheduleQrPoll() {
        mainHandler.removeCallbacks(qrPollRunnable)
        mainHandler.postDelayed(qrPollRunnable, QR_POLL_INTERVAL_MS)
    }

    private fun pollQr(key: String) {
        qrCall = authClient.pollQr(key) { result ->
            runOnUiThread {
                if (key != qrKey || loginPanel.visibility != View.VISIBLE) return@runOnUiThread
                result.fold(
                    onSuccess = { poll ->
                        loginStatus.text = poll.message
                        when (poll.state) {
                            BilibiliAuthClient.QrState.AUTHENTICATED -> {
                                qrKey = null
                                mainHandler.postDelayed({
                                    hideQrLogin()
                                    accountStatus = "Account: verifying sign-in…"
                                    refreshDiagnostics()
                                    checkAccount()
                                }, 900L)
                            }
                            BilibiliAuthClient.QrState.EXPIRED -> {
                                qrKey = null
                                newQrButton.requestFocus()
                            }
                            else -> scheduleQrPoll()
                        }
                    },
                    onFailure = { error ->
                        loginStatus.text = "QR status check failed: ${error.message.orEmpty()}"
                        scheduleQrPoll()
                    }
                )
            }
        }
    }

    private fun cancelQrLogin() {
        mainHandler.removeCallbacks(qrPollRunnable)
        qrCall?.cancel()
        qrCall = null
        qrKey = null
    }

    private fun hideQrLogin() {
        cancelQrLogin()
        loginPanel.visibility = View.GONE
        loginButton.requestFocus()
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
                    runOnUiThread {
                        playerHint.text = getString(R.string.player_hint)
                        isFetchingStream = false
                        liveButton.isEnabled = true
                        playMedia(
                            url = streamUrl,
                            referer = "https://live.bilibili.com/$roomId",
                            returnToRecommendations = false
                        )
                    }
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
    private fun playMedia(
        url: String,
        referer: String,
        returnToRecommendations: Boolean
    ) {
        val requestHeaders = mapOf(
            "Referer" to referer,
            "Origin" to "https://www.bilibili.com"
        )
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setDefaultRequestProperties(requestHeaders)
            .setAllowCrossProtocolRedirects(true)

        releasePlayer()
        playbackReturnsToRecommendations = returnToRecommendations
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .also { exoPlayer ->
                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        showPlaybackError(error)
                    }
                })
                playerView.player = exoPlayer
                diagnosticsPanel.visibility = View.GONE
                recommendationsPanel.visibility = View.GONE
                loginPanel.visibility = View.GONE
                playerView.visibility = View.VISIBLE
                playerHint.visibility = View.VISIBLE
                exoPlayer.setMediaItem(MediaItem.fromUri(url))
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }
    }

    private fun releasePlayer() {
        playerView.player = null
        player?.release()
        player = null
        playerView.visibility = View.GONE
        playerHint.visibility = View.GONE
    }

    private fun stopPlayback() {
        val returnToRecommendations = playbackReturnsToRecommendations
        releasePlayer()
        playbackReturnsToRecommendations = false
        if (returnToRecommendations) {
            diagnosticsPanel.visibility = View.GONE
            recommendationsPanel.visibility = View.VISIBLE
            recommendationsStatus.text =
                "${recommendationsGrid.childCount} videos • Press OK to play • Back for device tools"
        } else {
            recommendationsPanel.visibility = View.GONE
            diagnosticsPanel.visibility = View.VISIBLE
        }
    }

    private fun showPlaybackError(error: PlaybackException) {
        val returnToRecommendations = playbackReturnsToRecommendations
        val message = "Playback failed: ${error.errorCodeName} • ${error.message.orEmpty()}"
        stopPlayback()
        if (returnToRecommendations) {
            recommendationsStatus.text = message
            restoreRecommendationFocus()
        } else {
            statusText.append("\n$message")
            liveButton.requestFocus()
        }
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
            val returnToRecommendations = playbackReturnsToRecommendations
            stopPlayback()
            if (returnToRecommendations) {
                restoreRecommendationFocus()
            } else {
                liveButton.requestFocus()
            }
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && loginPanel.visibility == View.VISIBLE) {
            hideQrLogin()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && recommendationsPanel.visibility == View.VISIBLE) {
            hideRecommendations()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (player != null) {
            val returnToRecommendations = playbackReturnsToRecommendations
            stopPlayback()
            if (returnToRecommendations) {
                restoreRecommendationFocus()
            } else {
                liveButton.requestFocus()
            }
        } else if (loginPanel.visibility == View.VISIBLE) {
            hideQrLogin()
        } else if (recommendationsPanel.visibility == View.VISIBLE) {
            hideRecommendations()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        cancelQrLogin()
        accountCall?.cancel()
        recommendationsCall?.cancel()
        videoCall?.cancel()
        coverCalls.forEach(Call::cancel)
        coverCalls.clear()
        releasePlayer()
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
        const val QR_POLL_INTERVAL_MS = 2_000L
    }
}
