package com.bileebilee.tv

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.LruCache
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
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
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private lateinit var navigationBar: LinearLayout
    private lateinit var recommendationsButton: Button
    private lateinit var historyButton: Button
    private lateinit var liveButton: Button
    private lateinit var loginButton: Button
    private lateinit var loginPanel: LinearLayout
    private lateinit var loginDetails: LinearLayout
    private lateinit var loginTitle: TextView
    private lateinit var qrImage: ImageView
    private lateinit var loginStatus: TextView
    private lateinit var followingAccountsButton: Button
    private lateinit var newQrButton: Button
    private lateinit var playerView: PlayerView
    private lateinit var playerHint: TextView
    private lateinit var recommendationsPanel: LinearLayout
    private lateinit var recommendationsStatus: TextView
    private lateinit var recommendationsScroll: ScrollView
    private lateinit var recommendationsGrid: GridLayout
    private lateinit var refreshRecommendationsButton: Button
    private lateinit var historyPanel: LinearLayout
    private lateinit var historyStatus: TextView
    private lateinit var historyScroll: ScrollView
    private lateinit var historyGrid: GridLayout
    private lateinit var moreHistoryButton: Button
    private lateinit var livePanel: LinearLayout
    private lateinit var liveTitle: TextView
    private lateinit var liveStatus: TextView
    private lateinit var liveScroll: ScrollView
    private lateinit var liveGrid: GridLayout
    private lateinit var followingLiveButton: Button
    private lateinit var popularLiveButton: Button
    private lateinit var moreLiveButton: Button
    private lateinit var followingPanel: LinearLayout
    private lateinit var followingTitle: TextView
    private lateinit var followingStatus: TextView
    private lateinit var followingScroll: ScrollView
    private lateinit var followingGrid: GridLayout
    private lateinit var followingBackButton: Button
    private lateinit var moreFollowingButton: Button
    private lateinit var authClient: BilibiliAuthClient

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var player: ExoPlayer? = null
    private var qrKey: String? = null
    private var qrCall: Call? = null
    private var accountCall: Call? = null
    private var recommendationsCall: Call? = null
    private var historyCall: Call? = null
    private var liveRoomsCall: Call? = null
    private var liveStreamCall: Call? = null
    private var videoCall: Call? = null
    private var followingCall: Call? = null
    private var creatorVideosCall: Call? = null
    private var playbackReturnScreen = PlaybackReturnScreen.RECOMMENDATIONS
    private var currentBrowseScreen = BrowseScreen.RECOMMENDATIONS
    private var currentAccount: BilibiliAuthClient.Account? = null
    private var accountCheckComplete = false
    private var accountCheckError: String? = null
    private var pendingTabSelection: Runnable? = null
    private var followingMode = FollowingMode.CREATORS
    private var followingReturnFocus: View? = null
    private var followedCreators: List<BilibiliAuthClient.FollowedCreator> = emptyList()
    private var creatorVideos: List<BilibiliAuthClient.CreatorVideo> = emptyList()
    private var followedCreatorFocusIndex = 0
    private var creatorVideoFocusIndex = 0
    private var followingPage = 0
    private var followingTotal = 0
    private var followingHasMore = false
    private var selectedCreator: BilibiliAuthClient.FollowedCreator? = null
    private var creatorVideoPage = 0
    private var creatorVideoTotal = 0
    private var creatorVideosHaveMore = false
    private var recommendationReturnFocus: View? = null
    private var recommendationPage = 0
    private var recommendationFeedSignedIn = false
    private var historyReturnFocus: View? = null
    private var historyPage = 0
    private var historyHasMore = false
    private var historySkipped = 0
    private var liveReturnFocus: View? = null
    private var livePage = 0
    private var liveSource = BilibiliAuthClient.LiveSource.FOLLOWING
    private var playbackHeartbeatCall: Call? = null
    private var activePlaybackTracking: BilibiliAuthClient.PlaybackTracking? = null
    private var playbackStartedAt = 0L
    private var playbackStartedRealtime = 0L
    private var lastReportedSecond = -1L
    private val coverCalls = mutableListOf<Call>()
    private val coverCache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val qrPollRunnable = Runnable { qrKey?.let(::pollQr) }
    private val playbackHeartbeatRunnable = object : Runnable {
        override fun run() {
            reportPlaybackHeartbeat()
            if (player != null && activePlaybackTracking != null) {
                mainHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        navigationBar = findViewById(R.id.navigation_bar)
        recommendationsButton = findViewById(R.id.recommendations_button)
        historyButton = findViewById(R.id.history_button)
        liveButton = findViewById(R.id.live_button)
        loginButton = findViewById(R.id.login_button)
        loginPanel = findViewById(R.id.login_panel)
        loginDetails = findViewById(R.id.login_details)
        loginTitle = findViewById(R.id.login_title)
        qrImage = findViewById(R.id.qr_image)
        loginStatus = findViewById(R.id.login_status)
        followingAccountsButton = findViewById(R.id.following_accounts_button)
        newQrButton = findViewById(R.id.new_qr_button)
        recommendationsPanel = findViewById(R.id.recommendations_panel)
        recommendationsStatus = findViewById(R.id.recommendations_status)
        recommendationsScroll = findViewById(R.id.recommendations_scroll)
        recommendationsGrid = findViewById(R.id.recommendations_grid)
        refreshRecommendationsButton = findViewById(R.id.refresh_recommendations_button)
        historyPanel = findViewById(R.id.history_panel)
        historyStatus = findViewById(R.id.history_status)
        historyScroll = findViewById(R.id.history_scroll)
        historyGrid = findViewById(R.id.history_grid)
        moreHistoryButton = findViewById(R.id.more_history_button)
        livePanel = findViewById(R.id.live_panel)
        liveTitle = findViewById(R.id.live_title)
        liveStatus = findViewById(R.id.live_status)
        liveScroll = findViewById(R.id.live_scroll)
        liveGrid = findViewById(R.id.live_grid)
        followingLiveButton = findViewById(R.id.following_live_button)
        popularLiveButton = findViewById(R.id.popular_live_button)
        moreLiveButton = findViewById(R.id.more_live_button)
        followingPanel = findViewById(R.id.following_panel)
        followingTitle = findViewById(R.id.following_title)
        followingStatus = findViewById(R.id.following_status)
        followingScroll = findViewById(R.id.following_scroll)
        followingGrid = findViewById(R.id.following_grid)
        followingBackButton = findViewById(R.id.following_back_button)
        moreFollowingButton = findViewById(R.id.more_following_button)
        recommendationsButton.isAllCaps = false
        historyButton.isAllCaps = false
        liveButton.isAllCaps = false
        loginButton.isAllCaps = false
        newQrButton.isAllCaps = false
        playerView = findViewById(R.id.player_view)
        playerHint = findViewById(R.id.player_hint)
        authClient = BilibiliAuthClient(this, httpClient)

        recommendationsButton.setOnClickListener { showRecommendations(focusContent = false) }
        historyButton.setOnClickListener { showHistory(focusContent = false) }
        liveButton.setOnClickListener { showLiveRooms(focusContent = false) }
        loginButton.setOnClickListener { showAccount() }
        followingAccountsButton.setOnClickListener { showFollowingCreators(reset = true) }
        newQrButton.setOnClickListener { startQrLogin() }
        refreshRecommendationsButton.setOnClickListener { loadRecommendations() }
        moreHistoryButton.setOnClickListener { loadHistory() }
        followingLiveButton.setOnClickListener {
            selectLiveSource(BilibiliAuthClient.LiveSource.FOLLOWING)
        }
        popularLiveButton.setOnClickListener {
            selectLiveSource(BilibiliAuthClient.LiveSource.POPULAR)
        }
        moreLiveButton.setOnClickListener { loadLiveRooms() }
        followingBackButton.setOnClickListener { showFollowingCreators(reset = false) }
        moreFollowingButton.setOnClickListener {
            if (followingMode == FollowingMode.CREATORS) loadFollowingCreators() else loadCreatorVideos()
        }
        refreshRecommendationsButton.isAllCaps = false
        moreHistoryButton.isAllCaps = false
        followingLiveButton.isAllCaps = false
        popularLiveButton.isAllCaps = false
        moreLiveButton.isAllCaps = false
        followingAccountsButton.isAllCaps = false
        followingBackButton.isAllCaps = false
        moreFollowingButton.isAllCaps = false
        installNavigationTab(recommendationsButton, BrowseScreen.RECOMMENDATIONS)
        installNavigationTab(historyButton, BrowseScreen.HISTORY)
        installNavigationTab(liveButton, BrowseScreen.LIVE)
        installNavigationTab(loginButton, BrowseScreen.ACCOUNT)
        installFocusFeedback(newQrButton)
        installFocusFeedback(followingAccountsButton)
        installFocusFeedback(followingBackButton)
        installFocusFeedback(moreFollowingButton)

        checkAccount()
        showRecommendations(focusContent = false)
        recommendationsButton.requestFocus()
    }

    private fun installFocusFeedback(button: Button) {
        button.setOnFocusChangeListener { view, hasFocus ->
            view.animate()
                .scaleX(if (hasFocus) 1.06f else 1f)
                .scaleY(if (hasFocus) 1.06f else 1f)
                .setDuration(120L)
                .start()
        }
    }

    private fun installNavigationTab(button: Button, screen: BrowseScreen) {
        button.setOnFocusChangeListener { _, hasFocus ->
            pendingTabSelection?.let(mainHandler::removeCallbacks)
            pendingTabSelection = null
            if (hasFocus && currentBrowseScreen != screen) {
                Runnable {
                    if (button.hasFocus()) showBrowseScreen(screen)
                }.also { selection ->
                    pendingTabSelection = selection
                    mainHandler.postDelayed(selection, TAB_SWITCH_DELAY_MS)
                }
            }
        }
    }

    private fun showBrowseScreen(screen: BrowseScreen) {
        when (screen) {
            BrowseScreen.RECOMMENDATIONS -> showRecommendations(focusContent = false)
            BrowseScreen.HISTORY -> showHistory(focusContent = false)
            BrowseScreen.LIVE -> showLiveRooms(focusContent = false)
            BrowseScreen.ACCOUNT -> showAccount()
        }
    }

    private fun leaveAccountIfNeeded() {
        if (currentBrowseScreen == BrowseScreen.ACCOUNT) {
            cancelQrLogin()
            followingCall?.cancel()
            creatorVideosCall?.cancel()
        }
    }

    private fun showRecommendations(focusContent: Boolean = true) {
        leaveAccountIfNeeded()
        loginPanel.visibility = View.GONE
        historyPanel.visibility = View.GONE
        livePanel.visibility = View.GONE
        followingPanel.visibility = View.GONE
        recommendationsPanel.visibility = View.VISIBLE
        currentBrowseScreen = BrowseScreen.RECOMMENDATIONS
        updateNavigation(recommendationsButton)
        if (recommendationsGrid.childCount == 0) {
            loadRecommendations()
        } else {
            recommendationsStatus.text = recommendationSummary()
            if (focusContent) restoreRecommendationFocus()
        }
    }

    private fun showHistory(focusContent: Boolean = true) {
        leaveAccountIfNeeded()
        recommendationsPanel.visibility = View.GONE
        loginPanel.visibility = View.GONE
        livePanel.visibility = View.GONE
        followingPanel.visibility = View.GONE
        historyPanel.visibility = View.VISIBLE
        currentBrowseScreen = BrowseScreen.HISTORY
        updateNavigation(historyButton)
        if (historyGrid.childCount == 0) {
            authClient.resetHistory()
            loadHistory()
        } else {
            historyStatus.text = historySummary()
            if (focusContent) restoreHistoryFocus()
        }
    }

    private fun hideHistory() {
        historyCall?.cancel()
        videoCall?.cancel()
        showRecommendations()
    }

    private fun showLiveRooms(focusContent: Boolean = true) {
        leaveAccountIfNeeded()
        recommendationsPanel.visibility = View.GONE
        historyPanel.visibility = View.GONE
        loginPanel.visibility = View.GONE
        followingPanel.visibility = View.GONE
        livePanel.visibility = View.VISIBLE
        currentBrowseScreen = BrowseScreen.LIVE
        updateNavigation(liveButton)
        if (liveGrid.childCount == 0) {
            selectLiveSource(BilibiliAuthClient.LiveSource.FOLLOWING)
        } else {
            liveStatus.text = liveSummary()
            if (focusContent) restoreLiveFocus()
        }
    }

    private fun hideLiveRooms() {
        liveRoomsCall?.cancel()
        liveStreamCall?.cancel()
        showRecommendations()
    }

    private fun updateNavigation(activeButton: Button) {
        navigationBar.visibility = View.VISIBLE
        recommendationsButton.isActivated = activeButton === recommendationsButton
        historyButton.isActivated = activeButton === historyButton
        liveButton.isActivated = activeButton === liveButton
        loginButton.isActivated = activeButton === loginButton
    }

    private fun navigationHasFocus(): Boolean =
        recommendationsButton.hasFocus() || historyButton.hasFocus() ||
            liveButton.hasFocus() || loginButton.hasFocus()

    private fun selectLiveSource(source: BilibiliAuthClient.LiveSource) {
        liveRoomsCall?.cancel()
        liveSource = source
        livePage = 0
        liveReturnFocus = null
        authClient.resetLiveRooms(source)
        liveTitle.text = getString(
            if (source == BilibiliAuthClient.LiveSource.FOLLOWING) {
                R.string.following_live_title
            } else {
                R.string.popular_live_title
            }
        )
        followingLiveButton.text = if (source == BilibiliAuthClient.LiveSource.FOLLOWING) {
            "${getString(R.string.following)} ✓"
        } else {
            getString(R.string.following)
        }
        popularLiveButton.text = if (source == BilibiliAuthClient.LiveSource.POPULAR) {
            "${getString(R.string.popular)} ✓"
        } else {
            getString(R.string.popular)
        }
        if (!navigationHasFocus()) selectedLiveSourceButton().requestFocus()
        loadLiveRooms()
    }

    private fun loadLiveRooms() {
        liveRoomsCall?.cancel()
        val source = liveSource
        liveStatus.text = if (source == BilibiliAuthClient.LiveSource.FOLLOWING) {
            "Loading followed live rooms…"
        } else {
            "Loading popular live rooms…"
        }
        moreLiveButton.isEnabled = false
        liveRoomsCall = authClient.fetchLiveRooms(source) { result ->
            runOnUiThread {
                if (source != liveSource || livePanel.visibility != View.VISIBLE) {
                    return@runOnUiThread
                }
                result.fold(
                    onSuccess = { page ->
                        livePage = page.page
                        renderLiveRooms(page.rooms)
                        liveStatus.text = if (page.rooms.isEmpty()) {
                            if (source == BilibiliAuthClient.LiveSource.FOLLOWING) {
                                "None of the accounts you follow are live right now."
                            } else {
                                "No active live rooms were returned."
                            }
                        } else {
                            liveSummary()
                        }
                        moreLiveButton.isEnabled = page.hasMore
                        if (page.rooms.isNotEmpty() && !navigationHasFocus()) {
                            liveGrid.post { liveGrid.getChildAt(0)?.requestFocus() }
                        } else if (!navigationHasFocus()) {
                            selectedLiveSourceButton().requestFocus()
                        }
                    },
                    onFailure = { error ->
                        liveStatus.text = "Live-room request failed: ${error.message.orEmpty()}"
                        moreLiveButton.isEnabled = false
                        if (!navigationHasFocus()) selectedLiveSourceButton().requestFocus()
                    }
                )
            }
        }
    }

    private fun renderLiveRooms(rooms: List<BilibiliAuthClient.LiveRoom>) {
        coverCalls.forEach(Call::cancel)
        coverCalls.clear()
        liveGrid.removeAllViews()
        liveReturnFocus = null
        val cardWidth = gridCardWidth()
        rooms.forEachIndexed { index, room ->
            val card = LayoutInflater.from(this)
                .inflate(R.layout.recommendation_card, liveGrid, false)
            card.id = View.generateViewId()
            if (index < GRID_COLUMN_COUNT) {
                card.nextFocusUpId = when (index) {
                    1 -> R.id.following_live_button
                    2 -> R.id.popular_live_button
                    3 -> R.id.more_live_button
                    else -> R.id.live_button
                }
            }
            if (index == 0) liveButton.nextFocusDownId = card.id
            val cover = card.findViewById<ImageView>(R.id.recommendation_cover)
            val area = listOf(room.parentArea, room.area)
                .filter(String::isNotBlank)
                .distinct()
                .joinToString(" · ")
            card.findViewById<TextView>(R.id.recommendation_title).text = room.title
            card.findViewById<TextView>(R.id.recommendation_duration).text =
                formatPopularity(room.popularity)
            card.findViewById<TextView>(R.id.recommendation_meta).text =
                listOf(room.anchor, area).filter(String::isNotBlank).joinToString("  •  ")
            card.contentDescription = listOf(
                room.title,
                room.anchor,
                area,
                formatPopularity(room.popularity)
            ).filter(String::isNotBlank).joinToString(", ")
            card.setOnClickListener {
                liveReturnFocus = card
                playLiveRoom(room)
            }
            card.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    liveReturnFocus = view
                    snapGridToFocusedRow(liveScroll, liveGrid, index)
                }
                view.animate()
                    .scaleX(if (hasFocus) 1.055f else 1f)
                    .scaleY(if (hasFocus) 1.055f else 1f)
                    .setDuration(120L)
                    .start()
                view.elevation = if (hasFocus) 18f else 0f
            }
            liveGrid.addView(
                card,
                GridLayout.LayoutParams().apply {
                    width = cardWidth
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    setMargins(dp(CARD_MARGIN_DP), dp(CARD_MARGIN_DP), dp(CARD_MARGIN_DP), dp(CARD_MARGIN_DP))
                }
            )
            loadCover(room.coverUrl, cover)
        }
    }

    private fun formatPopularity(value: Long): String = when {
        value >= 1_000_000L -> String.format(Locale.US, "%.1fm", value / 1_000_000.0)
        value >= 1_000L -> String.format(Locale.US, "%.1fk", value / 1_000.0)
        value > 0L -> value.toString()
        else -> "Live"
    }

    private fun liveSummary(): String =
        "${liveGrid.childCount} live • " +
            "${if (liveSource == BilibiliAuthClient.LiveSource.FOLLOWING) "Following" else "Popular"} " +
            "• Page $livePage"

    private fun selectedLiveSourceButton(): Button =
        if (liveSource == BilibiliAuthClient.LiveSource.FOLLOWING) {
            followingLiveButton
        } else {
            popularLiveButton
        }

    private fun restoreLiveFocus() {
        val target = liveReturnFocus
            ?.takeIf { it.parent === liveGrid }
            ?: liveGrid.getChildAt(0)
        target?.requestFocus()
    }

    private fun playLiveRoom(room: BilibiliAuthClient.LiveRoom) {
        liveStreamCall?.cancel()
        liveStatus.text = "Opening ${room.title}…"
        liveStreamCall = authClient.fetchLiveStreamUrl(room) { result ->
            runOnUiThread {
                result.fold(
                    onSuccess = { url ->
                        playerHint.text = getString(R.string.live_player_hint)
                        playMedia(
                            url = url,
                            referer = "https://live.bilibili.com/${room.roomId}",
                            returnScreen = PlaybackReturnScreen.LIVE
                        )
                    },
                    onFailure = { error ->
                        liveStatus.text = "Could not play live room: ${error.message.orEmpty()}"
                        restoreLiveFocus()
                    }
                )
            }
        }
    }

    private fun loadHistory() {
        historyCall?.cancel()
        historyStatus.text = "Loading account watch history…"
        moreHistoryButton.isEnabled = false
        historyCall = authClient.fetchHistory { result ->
            runOnUiThread {
                result.fold(
                    onSuccess = { page ->
                        historyPage = page.page
                        historyHasMore = page.hasMore
                        historySkipped = page.returnedCount - page.items.size
                        renderHistory(page.items)
                        historyStatus.text = if (page.items.isEmpty()) {
                            if (page.returnedCount == 0) "No more watch history." else
                                "This page has no playable video entries."
                        } else {
                            historySummary()
                        }
                        moreHistoryButton.isEnabled = page.hasMore
                        if (page.items.isNotEmpty() && !navigationHasFocus()) {
                            historyGrid.post { historyGrid.getChildAt(0)?.requestFocus() }
                        } else if (!navigationHasFocus()) {
                            moreHistoryButton.requestFocus()
                        }
                    },
                    onFailure = { error ->
                        historyStatus.text = "History request failed: ${error.message.orEmpty()}"
                        moreHistoryButton.isEnabled = true
                        if (!navigationHasFocus()) moreHistoryButton.requestFocus()
                    }
                )
            }
        }
    }

    private fun renderHistory(items: List<BilibiliAuthClient.HistoryItem>) {
        coverCalls.forEach(Call::cancel)
        coverCalls.clear()
        historyGrid.removeAllViews()
        historyReturnFocus = null
        val cardWidth = gridCardWidth()
        items.forEachIndexed { index, item ->
            val card = LayoutInflater.from(this)
                .inflate(R.layout.recommendation_card, historyGrid, false)
            card.id = View.generateViewId()
            if (index < GRID_COLUMN_COUNT) {
                card.nextFocusUpId = if (index == GRID_COLUMN_COUNT - 1) {
                    R.id.more_history_button
                } else {
                    R.id.history_button
                }
            }
            if (index == 0) historyButton.nextFocusDownId = card.id
            val cover = card.findViewById<ImageView>(R.id.recommendation_cover)
            val displayTitle = listOf(item.title, item.subtitle)
                .filter(String::isNotBlank)
                .joinToString(" · ")
            card.findViewById<TextView>(R.id.recommendation_title).text = displayTitle
            card.findViewById<TextView>(R.id.recommendation_duration).text =
                historyProgress(item.progressSeconds, item.durationSeconds)
            card.findViewById<TextView>(R.id.recommendation_meta).text =
                listOf(item.author, viewedAt(item.viewedAt))
                    .filter(String::isNotBlank)
                    .joinToString("  •  ")
            card.contentDescription = listOf(
                displayTitle,
                item.author,
                historyProgress(item.progressSeconds, item.durationSeconds)
            ).filter(String::isNotBlank).joinToString(", ")
            card.setOnClickListener {
                historyReturnFocus = card
                playHistory(item)
            }
            card.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    historyReturnFocus = view
                    snapGridToFocusedRow(historyScroll, historyGrid, index)
                }
                view.animate()
                    .scaleX(if (hasFocus) 1.055f else 1f)
                    .scaleY(if (hasFocus) 1.055f else 1f)
                    .setDuration(120L)
                    .start()
                view.elevation = if (hasFocus) 18f else 0f
            }
            historyGrid.addView(
                card,
                GridLayout.LayoutParams().apply {
                    width = cardWidth
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    setMargins(dp(CARD_MARGIN_DP), dp(CARD_MARGIN_DP), dp(CARD_MARGIN_DP), dp(CARD_MARGIN_DP))
                }
            )
            loadCover(item.coverUrl, cover)
        }
    }

    private fun historyProgress(progress: Long, duration: Long): String {
        if (duration <= 0L) return ""
        if (progress < 0L || progress >= duration) return "Watched • ${formatDuration(duration)}"
        if (progress == 0L) return formatDuration(duration)
        return "${formatDuration(progress)} / ${formatDuration(duration)}"
    }

    private fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600L
        val minutes = (seconds % 3600L) / 60L
        val remainingSeconds = seconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remainingSeconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, remainingSeconds)
        }
    }

    private fun viewedAt(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            .format(Date(timestamp * 1_000L))
    }

    private fun historySummary(): String {
        val skipped = if (historySkipped > 0) " • $historySkipped unsupported" else ""
        val more = if (historyHasMore) " • More available" else ""
        return "${historyGrid.childCount} playable • Page $historyPage$skipped$more"
    }

    private fun restoreHistoryFocus() {
        val target = historyReturnFocus
            ?.takeIf { it.parent === historyGrid }
            ?: historyGrid.getChildAt(0)
        target?.requestFocus()
    }

    private fun playHistory(item: BilibiliAuthClient.HistoryItem) {
        videoCall?.cancel()
        historyStatus.text = "Opening ${item.title}…"
        videoCall = authClient.fetchHistoryVideoUrl(item) { result ->
            runOnUiThread {
                result.fold(
                    onSuccess = { url ->
                        playerHint.text = getString(R.string.history_player_hint)
                        val referer = if (item.business == "pgc") {
                            "https://www.bilibili.com/bangumi/play/ep${item.epId}"
                        } else {
                            "https://www.bilibili.com/video/av${item.aid}"
                        }
                        val resumeSeconds = item.progressSeconds.takeIf {
                            it > 0L && (item.durationSeconds <= 0L || it < item.durationSeconds)
                        } ?: 0L
                        playMedia(
                            url = url,
                            referer = referer,
                            returnScreen = PlaybackReturnScreen.HISTORY,
                            startPositionMs = resumeSeconds * 1_000L,
                            tracking = BilibiliAuthClient.PlaybackTracking(
                                aid = item.aid,
                                cid = item.cid,
                                epId = item.epId,
                                business = item.business
                            )
                        )
                    },
                    onFailure = { error ->
                        historyStatus.text = "Could not play history item: ${error.message.orEmpty()}"
                    }
                )
            }
        }
    }

    private fun loadRecommendations() {
        recommendationsCall?.cancel()
        recommendationsStatus.text = "Loading the mobile recommendation feed…"
        refreshRecommendationsButton.isEnabled = false
        recommendationsCall = authClient.fetchRecommendations { result ->
            runOnUiThread {
                refreshRecommendationsButton.isEnabled = true
                result.fold(
                    onSuccess = { page ->
                        recommendationPage = page.page
                        recommendationFeedSignedIn = page.signedIn
                        renderRecommendations(page.videos)
                        recommendationsStatus.text = if (page.videos.isEmpty()) {
                            "No playable videos were returned."
                        } else {
                            recommendationSummary()
                        }
                        if (page.videos.isNotEmpty() && !navigationHasFocus()) {
                            recommendationsGrid.post { recommendationsGrid.getChildAt(0)?.requestFocus() }
                        }
                    },
                    onFailure = { error ->
                        recommendationsStatus.text =
                            "Recommendation request failed: ${error.message.orEmpty()}"
                        if (!navigationHasFocus()) refreshRecommendationsButton.requestFocus()
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
        val cardWidth = gridCardWidth()
        videos.forEachIndexed { index, video ->
            val card = LayoutInflater.from(this)
                .inflate(R.layout.recommendation_card, recommendationsGrid, false)
            card.id = View.generateViewId()
            if (index < GRID_COLUMN_COUNT) {
                card.nextFocusUpId = if (index == GRID_COLUMN_COUNT - 1) {
                    R.id.refresh_recommendations_button
                } else {
                    R.id.recommendations_button
                }
            }
            if (index == 0) recommendationsButton.nextFocusDownId = card.id
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
                if (hasFocus) {
                    recommendationReturnFocus = view
                    snapGridToFocusedRow(recommendationsScroll, recommendationsGrid, index)
                }
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
                    setMargins(dp(CARD_MARGIN_DP), dp(CARD_MARGIN_DP), dp(CARD_MARGIN_DP), dp(CARD_MARGIN_DP))
                }
            )
            loadCover(video.coverUrl, cover)
        }
    }

    private fun loadCover(url: String, imageView: ImageView) {
        if (url.isBlank()) return
        imageView.tag = url
        coverCache.get(url)?.let {
            imageView.setImageBitmap(it)
            return
        }
        val request = Request.Builder()
            .url(coverThumbnailUrl(url))
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
                    val bitmap = decodeCover(bytes) ?: return
                    coverCache.put(url, bitmap)
                    runOnUiThread {
                        if (imageView.tag == url) imageView.setImageBitmap(bitmap)
                    }
                }
            }
        })
    }

    private fun coverThumbnailUrl(url: String): String {
        return if (url.contains("hdslb.com/") && !url.substringAfterLast('/').contains('@')) {
            "$url@640w_360h_1c.webp"
        } else {
            url
        }
    }

    private fun decodeCover(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > COVER_WIDTH_PX ||
            bounds.outHeight / sampleSize > COVER_HEIGHT_PX
        ) {
            sampleSize *= 2
        }
        return try {
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
            )
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun gridCardWidth(): Int {
        val usableWidth = resources.displayMetrics.widthPixels - dp(GRID_SIDE_PADDING_DP * 2)
        return usableWidth / GRID_COLUMN_COUNT - dp(CARD_MARGIN_DP * 2)
    }

    private fun snapGridToFocusedRow(scrollView: ScrollView, grid: GridLayout, index: Int) {
        val focusedRow = index / GRID_COLUMN_COUNT
        val firstVisibleRow = (focusedRow - 1).coerceAtLeast(0)
        val anchor = grid.getChildAt(firstVisibleRow * GRID_COLUMN_COUNT) ?: return
        scrollView.post {
            val targetY = anchor.top.coerceAtLeast(0)
            if (kotlin.math.abs(scrollView.scrollY - targetY) > dp(2)) {
                scrollView.smoothScrollTo(0, targetY)
            }
        }
    }

    private fun recommendationSummary(): String {
        val session = if (recommendationFeedSignedIn) "signed in" else "guest"
        return "${recommendationsGrid.childCount} videos • Mobile $session • " +
            "Page $recommendationPage • Press OK to play"
    }

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
                            returnScreen = PlaybackReturnScreen.RECOMMENDATIONS,
                            tracking = BilibiliAuthClient.PlaybackTracking(video.aid, video.cid)
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

    private fun showFollowingCreators(reset: Boolean) {
        val account = currentAccount ?: run {
            showAccount()
            return
        }
        cancelQrLogin()
        recommendationsPanel.visibility = View.GONE
        historyPanel.visibility = View.GONE
        livePanel.visibility = View.GONE
        loginPanel.visibility = View.GONE
        followingPanel.visibility = View.VISIBLE
        currentBrowseScreen = BrowseScreen.ACCOUNT
        updateNavigation(loginButton)
        followingMode = FollowingMode.CREATORS
        followingTitle.text = getString(R.string.following_accounts_title)
        followingBackButton.visibility = View.GONE
        moreFollowingButton.text = getString(R.string.more_following)
        if (reset || followedCreators.isEmpty()) {
            authClient.resetFollowing()
            followingPage = 0
            followingTotal = 0
            followingHasMore = false
            followedCreatorFocusIndex = 0
            followingGrid.removeAllViews()
            followingScroll.scrollTo(0, 0)
            loadFollowingCreators(account.mid)
        } else {
            renderFollowedCreators(followedCreators)
            followingStatus.text = followingCreatorsSummary()
            moreFollowingButton.isEnabled = followingHasMore
            restoreFollowingFocus(followedCreatorFocusIndex)
        }
    }

    private fun loadFollowingCreators(accountId: Long? = currentAccount?.mid) {
        val resolvedAccountId = accountId ?: return
        followingCall?.cancel()
        followingStatus.text = "Loading followed creators…"
        moreFollowingButton.isEnabled = false
        followingCall = authClient.fetchFollowing(resolvedAccountId) { result ->
            runOnUiThread {
                if (followingMode != FollowingMode.CREATORS ||
                    followingPanel.visibility != View.VISIBLE
                ) return@runOnUiThread
                result.fold(
                    onSuccess = { page ->
                        followedCreators = page.creators
                        followingPage = page.page
                        followingTotal = page.total
                        followingHasMore = page.hasMore
                        followedCreatorFocusIndex = 0
                        renderFollowedCreators(page.creators)
                        followingStatus.text = if (page.creators.isEmpty()) {
                            "No followed creators were returned."
                        } else {
                            followingCreatorsSummary()
                        }
                        moreFollowingButton.isEnabled = page.hasMore
                        restoreFollowingFocus(0)
                    },
                    onFailure = { error ->
                        followingStatus.text =
                            "Following request failed: ${error.message.orEmpty()}"
                        moreFollowingButton.isEnabled = true
                        moreFollowingButton.requestFocus()
                    }
                )
            }
        }
    }

    private fun renderFollowedCreators(creators: List<BilibiliAuthClient.FollowedCreator>) {
        coverCalls.forEach(Call::cancel)
        coverCalls.clear()
        followingGrid.removeAllViews()
        val cardWidth = gridCardWidth()
        creators.forEachIndexed { index, creator ->
            val card = LayoutInflater.from(this)
                .inflate(R.layout.recommendation_card, followingGrid, false)
            card.id = View.generateViewId()
            if (index < GRID_COLUMN_COUNT) {
                card.nextFocusUpId = if (index == GRID_COLUMN_COUNT - 1) {
                    R.id.more_following_button
                } else {
                    R.id.login_button
                }
            }
            if (index == 0) loginButton.nextFocusDownId = card.id
            val avatar = card.findViewById<ImageView>(R.id.recommendation_cover)
            avatar.scaleType = ImageView.ScaleType.FIT_CENTER
            card.findViewById<TextView>(R.id.recommendation_title).text = creator.name
            card.findViewById<TextView>(R.id.recommendation_duration).text = ""
            card.findViewById<TextView>(R.id.recommendation_meta).text =
                creator.description.ifBlank { "Followed creator" }
            card.contentDescription = listOf(creator.name, creator.description)
                .filter(String::isNotBlank)
                .joinToString(", ")
            card.setOnClickListener {
                followedCreatorFocusIndex = index
                selectedCreator = creator
                showCreatorVideos(creator, reset = true)
            }
            card.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    followedCreatorFocusIndex = index
                    followingReturnFocus = view
                    snapGridToFocusedRow(followingScroll, followingGrid, index)
                }
                view.animate()
                    .scaleX(if (hasFocus) 1.055f else 1f)
                    .scaleY(if (hasFocus) 1.055f else 1f)
                    .setDuration(120L)
                    .start()
                view.elevation = if (hasFocus) 18f else 0f
            }
            followingGrid.addView(
                card,
                GridLayout.LayoutParams().apply {
                    width = cardWidth
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    setMargins(
                        dp(CARD_MARGIN_DP), dp(CARD_MARGIN_DP),
                        dp(CARD_MARGIN_DP), dp(CARD_MARGIN_DP)
                    )
                }
            )
            loadCover(creator.avatarUrl, avatar)
        }
    }

    private fun showCreatorVideos(
        creator: BilibiliAuthClient.FollowedCreator,
        reset: Boolean
    ) {
        followingMode = FollowingMode.VIDEOS
        selectedCreator = creator
        followingPanel.visibility = View.VISIBLE
        loginPanel.visibility = View.GONE
        followingTitle.text = creator.name
        followingBackButton.visibility = View.VISIBLE
        moreFollowingButton.text = getString(R.string.more_creator_videos)
        if (reset || creatorVideos.isEmpty()) {
            creatorVideoPage = 0
            creatorVideoTotal = 0
            creatorVideosHaveMore = false
            creatorVideoFocusIndex = 0
            creatorVideos = emptyList()
            followingGrid.removeAllViews()
            followingScroll.scrollTo(0, 0)
            loadCreatorVideos()
        } else {
            renderCreatorVideos(creatorVideos)
            followingStatus.text = creatorVideosSummary()
            moreFollowingButton.isEnabled = creatorVideosHaveMore
            restoreFollowingFocus(creatorVideoFocusIndex)
        }
    }

    private fun loadCreatorVideos() {
        val creator = selectedCreator ?: return
        creatorVideosCall?.cancel()
        val requestedPage = creatorVideoPage + 1
        followingStatus.text = "Loading recent videos…"
        moreFollowingButton.isEnabled = false
        creatorVideosCall = authClient.fetchCreatorVideos(creator, requestedPage) { result ->
            runOnUiThread {
                if (followingMode != FollowingMode.VIDEOS || selectedCreator != creator ||
                    followingPanel.visibility != View.VISIBLE
                ) return@runOnUiThread
                result.fold(
                    onSuccess = { page ->
                        creatorVideos = page.videos
                        creatorVideoPage = page.page
                        creatorVideoTotal = page.total
                        creatorVideosHaveMore = page.hasMore
                        creatorVideoFocusIndex = 0
                        renderCreatorVideos(page.videos)
                        followingStatus.text = if (page.videos.isEmpty()) {
                            "No public videos were returned for this creator."
                        } else {
                            creatorVideosSummary()
                        }
                        moreFollowingButton.isEnabled = page.hasMore
                        restoreFollowingFocus(0)
                    },
                    onFailure = { error ->
                        followingStatus.text =
                            "Creator-video request failed: ${error.message.orEmpty()}"
                        moreFollowingButton.isEnabled = true
                        moreFollowingButton.requestFocus()
                    }
                )
            }
        }
    }

    private fun renderCreatorVideos(videos: List<BilibiliAuthClient.CreatorVideo>) {
        coverCalls.forEach(Call::cancel)
        coverCalls.clear()
        followingGrid.removeAllViews()
        val cardWidth = gridCardWidth()
        videos.forEachIndexed { index, video ->
            val card = LayoutInflater.from(this)
                .inflate(R.layout.recommendation_card, followingGrid, false)
            card.id = View.generateViewId()
            if (index < GRID_COLUMN_COUNT) {
                card.nextFocusUpId = when (index) {
                    2 -> R.id.following_back_button
                    3 -> R.id.more_following_button
                    else -> R.id.login_button
                }
            }
            if (index == 0) loginButton.nextFocusDownId = card.id
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
                creatorVideoFocusIndex = index
                followingReturnFocus = card
                playCreatorVideo(video)
            }
            card.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    creatorVideoFocusIndex = index
                    followingReturnFocus = view
                    snapGridToFocusedRow(followingScroll, followingGrid, index)
                }
                view.animate()
                    .scaleX(if (hasFocus) 1.055f else 1f)
                    .scaleY(if (hasFocus) 1.055f else 1f)
                    .setDuration(120L)
                    .start()
                view.elevation = if (hasFocus) 18f else 0f
            }
            followingGrid.addView(
                card,
                GridLayout.LayoutParams().apply {
                    width = cardWidth
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    setMargins(
                        dp(CARD_MARGIN_DP), dp(CARD_MARGIN_DP),
                        dp(CARD_MARGIN_DP), dp(CARD_MARGIN_DP)
                    )
                }
            )
            loadCover(video.coverUrl, cover)
        }
    }

    private fun playCreatorVideo(video: BilibiliAuthClient.CreatorVideo) {
        videoCall?.cancel()
        followingStatus.text = "Opening ${video.title}…"
        videoCall = authClient.fetchCreatorVideoUrl(video) { result ->
            runOnUiThread {
                result.fold(
                    onSuccess = { url ->
                        playerHint.text = getString(R.string.following_player_hint)
                        playMedia(
                            url = url,
                            referer = "https://www.bilibili.com/video/av${video.aid}",
                            returnScreen = PlaybackReturnScreen.FOLLOWING
                        )
                    },
                    onFailure = { error ->
                        followingStatus.text =
                            "Could not play creator video: ${error.message.orEmpty()}"
                        restoreFollowingFocus(creatorVideoFocusIndex)
                    }
                )
            }
        }
    }

    private fun followingCreatorsSummary(): String =
        "${followedCreators.size} creators • Page $followingPage • $followingTotal total"

    private fun creatorVideosSummary(): String =
        "${creatorVideos.size} videos • Page $creatorVideoPage • $creatorVideoTotal total"

    private fun restoreFollowingFocus(index: Int) {
        followingGrid.post {
            followingGrid.getChildAt(index.coerceIn(0, (followingGrid.childCount - 1).coerceAtLeast(0)))
                ?.requestFocus()
        }
    }

    private fun checkAccount() {
        accountCall?.cancel()
        accountCheckComplete = false
        accountCheckError = null
        accountCall = authClient.checkSession { result ->
            runOnUiThread {
                result.fold(
                    onSuccess = { account ->
                        currentAccount = account
                        accountCheckComplete = true
                        loginButton.contentDescription = account?.let {
                            "Account, signed in as ${it.name}"
                        } ?: "Account, not signed in"
                        if (currentBrowseScreen == BrowseScreen.ACCOUNT) {
                            account?.let(::showSignedInAccount) ?: startQrLogin()
                        }
                    },
                    onFailure = { error ->
                        currentAccount = null
                        accountCheckComplete = true
                        accountCheckError = error.message.orEmpty()
                        loginButton.contentDescription =
                            "Account check failed: ${error.message.orEmpty()}"
                        if (currentBrowseScreen == BrowseScreen.ACCOUNT) {
                            showAccountError(error.message.orEmpty())
                        }
                    }
                )
            }
        }
    }

    private fun showAccount() {
        followingCall?.cancel()
        creatorVideosCall?.cancel()
        recommendationsPanel.visibility = View.GONE
        historyPanel.visibility = View.GONE
        livePanel.visibility = View.GONE
        followingPanel.visibility = View.GONE
        loginPanel.visibility = View.VISIBLE
        currentBrowseScreen = BrowseScreen.ACCOUNT
        updateNavigation(loginButton)
        when {
            !accountCheckComplete -> showAccountChecking()
            currentAccount != null -> showSignedInAccount(currentAccount!!)
            accountCheckError != null -> showAccountError(accountCheckError.orEmpty())
            else -> startQrLogin()
        }
    }

    private fun showAccountChecking() {
        cancelQrLogin()
        qrImage.visibility = View.GONE
        setLoginDetailsStartMargin(0)
        loginTitle.text = getString(R.string.account_title)
        loginStatus.text = getString(R.string.account_checking)
        followingAccountsButton.visibility = View.GONE
        newQrButton.visibility = View.GONE
    }

    private fun showSignedInAccount(account: BilibiliAuthClient.Account) {
        cancelQrLogin()
        qrImage.visibility = View.GONE
        setLoginDetailsStartMargin(0)
        loginTitle.text = getString(R.string.account_title)
        loginStatus.text = getString(R.string.account_signed_in, account.name, account.mid)
        loginButton.nextFocusDownId = R.id.following_accounts_button
        followingAccountsButton.visibility = View.VISIBLE
        newQrButton.text = getString(R.string.use_another_account)
        newQrButton.isEnabled = true
        newQrButton.visibility = View.VISIBLE
    }

    private fun showAccountError(message: String) {
        cancelQrLogin()
        qrImage.visibility = View.GONE
        setLoginDetailsStartMargin(0)
        loginTitle.text = getString(R.string.account_title)
        loginStatus.text = "Could not check your account: $message"
        followingAccountsButton.visibility = View.GONE
        newQrButton.text = getString(R.string.new_qr_code)
        newQrButton.isEnabled = true
        newQrButton.visibility = View.VISIBLE
    }

    private fun setLoginDetailsStartMargin(marginDp: Int) {
        (loginDetails.layoutParams as LinearLayout.LayoutParams).let { params ->
            params.marginStart = dp(marginDp)
            loginDetails.layoutParams = params
        }
    }

    private fun startQrLogin() {
        cancelQrLogin()
        recommendationsPanel.visibility = View.GONE
        historyPanel.visibility = View.GONE
        livePanel.visibility = View.GONE
        followingPanel.visibility = View.GONE
        currentBrowseScreen = BrowseScreen.ACCOUNT
        updateNavigation(loginButton)
        loginPanel.visibility = View.VISIBLE
        qrImage.visibility = View.VISIBLE
        qrImage.setImageDrawable(null)
        setLoginDetailsStartMargin(36)
        loginTitle.text = getString(R.string.qr_login_title)
        loginStatus.text = "Requesting a QR code…"
        loginButton.nextFocusDownId = R.id.new_qr_button
        followingAccountsButton.visibility = View.GONE
        newQrButton.text = getString(R.string.new_qr_code)
        newQrButton.visibility = View.VISIBLE
        newQrButton.isEnabled = false

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
                                    loginStatus.text = "Signed in. Loading your account…"
                                    checkAccount()
                                }, 500L)
                            }
                            BilibiliAuthClient.QrState.EXPIRED -> {
                                qrKey = null
                                if (!navigationHasFocus()) newQrButton.requestFocus()
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

    private fun hideAccount() {
        cancelQrLogin()
        loginPanel.visibility = View.GONE
        showRecommendations()
    }

    @androidx.annotation.OptIn(markerClass = [UnstableApi::class])
    private fun playMedia(
        url: String,
        referer: String,
        returnScreen: PlaybackReturnScreen,
        startPositionMs: Long = 0L,
        tracking: BilibiliAuthClient.PlaybackTracking? = null
    ) {
        val requestHeaders = mapOf(
            "Referer" to referer,
            "Origin" to "https://www.bilibili.com"
        )
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setDefaultRequestProperties(requestHeaders)
            .setAllowCrossProtocolRedirects(true)

        finishPlaybackTracking()
        releasePlayer()
        playbackReturnScreen = returnScreen
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
                navigationBar.visibility = View.GONE
                recommendationsPanel.visibility = View.GONE
                historyPanel.visibility = View.GONE
                livePanel.visibility = View.GONE
                loginPanel.visibility = View.GONE
                followingPanel.visibility = View.GONE
                playerView.visibility = View.VISIBLE
                playerHint.visibility = View.VISIBLE
                exoPlayer.setMediaItem(MediaItem.fromUri(url))
                if (startPositionMs > 0L) exoPlayer.seekTo(startPositionMs)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
                startPlaybackTracking(tracking)
            }
    }

    private fun startPlaybackTracking(tracking: BilibiliAuthClient.PlaybackTracking?) {
        mainHandler.removeCallbacks(playbackHeartbeatRunnable)
        activePlaybackTracking = tracking
        lastReportedSecond = -1L
        if (tracking == null) return
        playbackStartedAt = System.currentTimeMillis() / 1_000L
        playbackStartedRealtime = SystemClock.elapsedRealtime()
        mainHandler.postDelayed(playbackHeartbeatRunnable, FIRST_HEARTBEAT_DELAY_MS)
    }

    private fun reportPlaybackHeartbeat(force: Boolean = false) {
        val tracking = activePlaybackTracking ?: return
        val exoPlayer = player ?: return
        val playedSeconds = (exoPlayer.currentPosition / 1_000L).coerceAtLeast(0L)
        if (!force && playedSeconds == lastReportedSecond) return
        val realtimeSeconds =
            ((SystemClock.elapsedRealtime() - playbackStartedRealtime) / 1_000L).coerceAtLeast(0L)
        playbackHeartbeatCall?.cancel()
        playbackHeartbeatCall = authClient.reportPlaybackProgress(
            tracking = tracking,
            playedSeconds = playedSeconds,
            realtimeSeconds = realtimeSeconds,
            startedAt = playbackStartedAt
        )
        if (playbackHeartbeatCall != null) lastReportedSecond = playedSeconds
    }

    private fun finishPlaybackTracking() {
        mainHandler.removeCallbacks(playbackHeartbeatRunnable)
        if (activePlaybackTracking != null) reportPlaybackHeartbeat(force = true)
        activePlaybackTracking = null
        lastReportedSecond = -1L
    }

    private fun releasePlayer() {
        playerView.player = null
        player?.release()
        player = null
        playerView.visibility = View.GONE
        playerHint.visibility = View.GONE
    }

    private fun stopPlayback() {
        val returnScreen = playbackReturnScreen
        finishPlaybackTracking()
        releasePlayer()
        navigationBar.visibility = View.VISIBLE
        recommendationsPanel.visibility = View.GONE
        historyPanel.visibility = View.GONE
        livePanel.visibility = View.GONE
        loginPanel.visibility = View.GONE
        followingPanel.visibility = View.GONE
        when (returnScreen) {
            PlaybackReturnScreen.RECOMMENDATIONS -> {
                currentBrowseScreen = BrowseScreen.RECOMMENDATIONS
                updateNavigation(recommendationsButton)
                recommendationsPanel.visibility = View.VISIBLE
                recommendationsStatus.text = recommendationSummary()
            }
            PlaybackReturnScreen.HISTORY -> {
                currentBrowseScreen = BrowseScreen.HISTORY
                updateNavigation(historyButton)
                historyPanel.visibility = View.VISIBLE
                historyStatus.text = historySummary()
            }
            PlaybackReturnScreen.LIVE -> {
                currentBrowseScreen = BrowseScreen.LIVE
                updateNavigation(liveButton)
                livePanel.visibility = View.VISIBLE
                liveStatus.text = liveSummary()
            }
            PlaybackReturnScreen.FOLLOWING -> {
                currentBrowseScreen = BrowseScreen.ACCOUNT
                updateNavigation(loginButton)
                followingPanel.visibility = View.VISIBLE
                followingStatus.text = creatorVideosSummary()
            }
        }
    }

    private fun showPlaybackError(error: PlaybackException) {
        val returnScreen = playbackReturnScreen
        val message = "Playback failed: ${error.errorCodeName} • ${error.message.orEmpty()}"
        stopPlayback()
        when (returnScreen) {
            PlaybackReturnScreen.RECOMMENDATIONS -> {
                recommendationsStatus.text = message
                restoreRecommendationFocus()
            }
            PlaybackReturnScreen.HISTORY -> {
                historyStatus.text = message
                restoreHistoryFocus()
            }
            PlaybackReturnScreen.LIVE -> {
                liveStatus.text = message
                restoreLiveFocus()
            }
            PlaybackReturnScreen.FOLLOWING -> {
                followingStatus.text = message
                restoreFollowingFocus(creatorVideoFocusIndex)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && player != null) {
            val returnScreen = playbackReturnScreen
            stopPlayback()
            when (returnScreen) {
                PlaybackReturnScreen.RECOMMENDATIONS -> restoreRecommendationFocus()
                PlaybackReturnScreen.HISTORY -> restoreHistoryFocus()
                PlaybackReturnScreen.LIVE -> restoreLiveFocus()
                PlaybackReturnScreen.FOLLOWING -> restoreFollowingFocus(creatorVideoFocusIndex)
            }
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && loginPanel.visibility == View.VISIBLE) {
            hideAccount()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && followingPanel.visibility == View.VISIBLE) {
            if (followingMode == FollowingMode.VIDEOS) {
                showFollowingCreators(reset = false)
            } else {
                showAccount()
                followingAccountsButton.requestFocus()
            }
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && historyPanel.visibility == View.VISIBLE) {
            hideHistory()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && livePanel.visibility == View.VISIBLE) {
            hideLiveRooms()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (player != null) {
            val returnScreen = playbackReturnScreen
            stopPlayback()
            when (returnScreen) {
                PlaybackReturnScreen.RECOMMENDATIONS -> restoreRecommendationFocus()
                PlaybackReturnScreen.HISTORY -> restoreHistoryFocus()
                PlaybackReturnScreen.LIVE -> restoreLiveFocus()
                PlaybackReturnScreen.FOLLOWING -> restoreFollowingFocus(creatorVideoFocusIndex)
            }
        } else if (loginPanel.visibility == View.VISIBLE) {
            hideAccount()
        } else if (followingPanel.visibility == View.VISIBLE) {
            if (followingMode == FollowingMode.VIDEOS) {
                showFollowingCreators(reset = false)
            } else {
                showAccount()
                followingAccountsButton.requestFocus()
            }
        } else if (historyPanel.visibility == View.VISIBLE) {
            hideHistory()
        } else if (livePanel.visibility == View.VISIBLE) {
            hideLiveRooms()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        pendingTabSelection?.let(mainHandler::removeCallbacks)
        cancelQrLogin()
        accountCall?.cancel()
        recommendationsCall?.cancel()
        historyCall?.cancel()
        liveRoomsCall?.cancel()
        liveStreamCall?.cancel()
        followingCall?.cancel()
        creatorVideosCall?.cancel()
        videoCall?.cancel()
        coverCalls.forEach(Call::cancel)
        coverCalls.clear()
        finishPlaybackTracking()
        releasePlayer()
        super.onDestroy()
    }

    private enum class PlaybackReturnScreen {
        RECOMMENDATIONS,
        HISTORY,
        LIVE,
        FOLLOWING
    }

    private enum class FollowingMode {
        CREATORS,
        VIDEOS
    }

    private enum class BrowseScreen {
        RECOMMENDATIONS,
        HISTORY,
        LIVE,
        ACCOUNT
    }

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 5.1; BileebileeTV/0.1) AppleWebKit/537.36 Mobile Safari/537.36"
        const val QR_POLL_INTERVAL_MS = 2_000L
        const val TAB_SWITCH_DELAY_MS = 150L
        const val FIRST_HEARTBEAT_DELAY_MS = 5_000L
        const val HEARTBEAT_INTERVAL_MS = 15_000L
        const val COVER_WIDTH_PX = 640
        const val COVER_HEIGHT_PX = 360
        const val GRID_SIDE_PADDING_DP = 72
        const val GRID_COLUMN_COUNT = 4
        const val CARD_MARGIN_DP = 6
    }
}
