package com.bileebilee.tv

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Html
import android.util.LruCache
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
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
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
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
    private lateinit var playerSettingsPanel: LinearLayout
    private lateinit var playerSettingsTitle: TextView
    private lateinit var playerSettingsScroll: ScrollView
    private lateinit var playerSettingsOptions: LinearLayout
    private lateinit var recommendationsPanel: LinearLayout
    private lateinit var recommendationsStatus: TextView
    private lateinit var recommendationsScroll: ScrollView
    private lateinit var recommendationsGrid: GridLayout
    private lateinit var refreshRecommendationsButton: Button
    private lateinit var historyPanel: LinearLayout
    private lateinit var historyStatus: TextView
    private lateinit var historyScroll: ScrollView
    private lateinit var historyGrid: GridLayout
    private lateinit var livePanel: LinearLayout
    private lateinit var liveTitle: TextView
    private lateinit var liveStatus: TextView
    private lateinit var liveScroll: ScrollView
    private lateinit var liveGrid: GridLayout
    private lateinit var followingLiveButton: Button
    private lateinit var popularLiveButton: Button
    private lateinit var followingPanel: LinearLayout
    private lateinit var followingTitle: TextView
    private lateinit var followingStatus: TextView
    private lateinit var followingScroll: ScrollView
    private lateinit var followingGrid: GridLayout
    private lateinit var followingBackButton: Button
    private lateinit var searchPanel: LinearLayout
    private lateinit var searchStatus: TextView
    private lateinit var searchField: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var searchScroll: ScrollView
    private lateinit var searchGrid: GridLayout
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
    private var searchCall: Call? = null
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
    private var followingLoading = false
    private var selectedCreator: BilibiliAuthClient.FollowedCreator? = null
    private var creatorVideoPage = 0
    private var creatorVideoTotal = 0
    private var creatorVideosHaveMore = false
    private var creatorVideosLoading = false
    private var recommendationReturnFocus: View? = null
    private var recommendationPage = 0
    private var recommendationFeedSignedIn = false
    private var recommendationsHaveMore = true
    private var recommendationsLoading = false
    private var historyReturnFocus: View? = null
    private var historyPage = 0
    private var historyHasMore = false
    private var historySkipped = 0
    private var historyLoading = false
    private var liveReturnFocus: View? = null
    private var livePage = 0
    private var liveSource = BilibiliAuthClient.LiveSource.FOLLOWING
    private var liveHasMore = false
    private var liveLoading = false
    private var searchQuery = ""
    private var searchPage = 0
    private var searchTotal = 0
    private var searchHasMore = false
    private var searchLoading = false
    private var searchReturnFocus: View? = null
    private var playerRevealKeyCode = KeyEvent.KEYCODE_UNKNOWN
    private var playerControlsVisible = false
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
    private val playerHintHideRunnable = Runnable {
        if (player != null && playerHint.visibility == View.VISIBLE) {
            playerHint.animate()
                .alpha(0f)
                .setDuration(PLAYER_HINT_FADE_MS)
                .withEndAction {
                    if (player != null) playerHint.visibility = View.GONE
                }
                .start()
        }
    }
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
        livePanel = findViewById(R.id.live_panel)
        liveTitle = findViewById(R.id.live_title)
        liveStatus = findViewById(R.id.live_status)
        liveScroll = findViewById(R.id.live_scroll)
        liveGrid = findViewById(R.id.live_grid)
        followingLiveButton = findViewById(R.id.following_live_button)
        popularLiveButton = findViewById(R.id.popular_live_button)
        followingPanel = findViewById(R.id.following_panel)
        followingTitle = findViewById(R.id.following_title)
        followingStatus = findViewById(R.id.following_status)
        followingScroll = findViewById(R.id.following_scroll)
        followingGrid = findViewById(R.id.following_grid)
        followingBackButton = findViewById(R.id.following_back_button)
        searchPanel = findViewById(R.id.search_panel)
        searchStatus = findViewById(R.id.search_status)
        searchField = findViewById(R.id.search_field)
        searchInput = findViewById(R.id.search_input)
        searchScroll = findViewById(R.id.search_scroll)
        searchGrid = findViewById(R.id.search_grid)
        recommendationsButton.isAllCaps = false
        historyButton.isAllCaps = false
        liveButton.isAllCaps = false
        loginButton.isAllCaps = false
        newQrButton.isAllCaps = false
        playerView = findViewById(R.id.player_view)
        playerHint = findViewById(R.id.player_hint)
        playerSettingsPanel = findViewById(R.id.player_settings_panel)
        playerSettingsTitle = findViewById(R.id.player_settings_title)
        playerSettingsScroll = findViewById(R.id.player_settings_scroll)
        playerSettingsOptions = findViewById(R.id.player_settings_options)
        applyPlayerControllerSafeArea()
        playerView.findViewById<View>(androidx.media3.ui.R.id.exo_settings)
            ?.setOnClickListener { showPlayerSettingsMain() }
        playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                playerControlsVisible = visibility == View.VISIBLE
            }
        )
        authClient = BilibiliAuthClient(this, httpClient)

        recommendationsButton.setOnClickListener { showRecommendations(focusContent = false) }
        historyButton.setOnClickListener { showHistory(focusContent = false) }
        liveButton.setOnClickListener { showLiveRooms(focusContent = false) }
        loginButton.setOnClickListener { showAccount() }
        followingAccountsButton.setOnClickListener { showFollowingCreators(reset = true) }
        newQrButton.setOnClickListener { startQrLogin() }
        refreshRecommendationsButton.setOnClickListener { refreshRecommendations() }
        followingLiveButton.setOnClickListener {
            selectLiveSource(BilibiliAuthClient.LiveSource.FOLLOWING)
        }
        popularLiveButton.setOnClickListener {
            selectLiveSource(BilibiliAuthClient.LiveSource.POPULAR)
        }
        followingBackButton.setOnClickListener { showFollowingCreators(reset = false) }
        searchInput.setOnFocusChangeListener { _, hasFocus ->
            searchField.isActivated = hasFocus
        }
        searchInput.setOnEditorActionListener { _, actionId, event ->
            val enterEvent = event?.keyCode == KeyEvent.KEYCODE_ENTER
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enterEvent) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH || event?.action == KeyEvent.ACTION_DOWN) {
                    submitSearch()
                }
                true
            } else {
                false
            }
        }
        refreshRecommendationsButton.isAllCaps = false
        followingLiveButton.isAllCaps = false
        popularLiveButton.isAllCaps = false
        followingAccountsButton.isAllCaps = false
        followingBackButton.isAllCaps = false
        installNavigationTab(recommendationsButton, BrowseScreen.RECOMMENDATIONS)
        installNavigationTab(historyButton, BrowseScreen.HISTORY)
        installNavigationTab(liveButton, BrowseScreen.LIVE)
        installNavigationTab(loginButton, BrowseScreen.ACCOUNT)
        installFocusFeedback(newQrButton)
        installFocusFeedback(followingAccountsButton)
        installFocusFeedback(followingBackButton)

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

    private fun applyPlayerControllerSafeArea() {
        insetPlayerControllerView(
            androidx.media3.ui.R.id.exo_bottom_bar,
            sideInsetDp = PLAYER_CONTROLS_SIDE_INSET_DP,
            bottomInsetDp = PLAYER_CONTROLS_BOTTOM_INSET_DP
        )
        insetPlayerControllerView(
            androidx.media3.ui.R.id.exo_progress,
            sideInsetDp = PLAYER_CONTROLS_SIDE_INSET_DP,
            bottomInsetDp = PLAYER_CONTROLS_BOTTOM_INSET_DP
        )
        insetPlayerControllerView(
            androidx.media3.ui.R.id.exo_minimal_controls,
            sideInsetDp = PLAYER_CONTROLS_SIDE_INSET_DP,
            bottomInsetDp = PLAYER_CONTROLS_BOTTOM_INSET_DP
        )
    }

    private fun insetPlayerControllerView(
        viewId: Int,
        sideInsetDp: Int,
        bottomInsetDp: Int
    ) {
        val control = playerView.findViewById<View>(viewId) ?: return
        val params = control.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        params.marginStart += dp(sideInsetDp)
        params.marginEnd += dp(sideInsetDp)
        params.bottomMargin += dp(bottomInsetDp)
        control.layoutParams = params
    }

    private fun showPlayerSettingsMain() {
        val exoPlayer = player ?: return
        showPlayerSettings(
            getString(R.string.player_settings_title),
            listOf(
                "${getString(R.string.player_speed)}  •  ${formatPlaybackSpeed(exoPlayer.playbackParameters.speed)}" to
                    ::showPlayerSpeedSettings,
                "${getString(R.string.player_audio)}  •  ${selectedAudioLabel(exoPlayer)}" to
                    ::showPlayerAudioSettings
            )
        )
    }

    private fun showPlayerSpeedSettings() {
        val exoPlayer = player ?: return
        val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        showPlayerSettings(
            getString(R.string.player_speed),
            speeds.map { speed ->
                val selected = kotlin.math.abs(exoPlayer.playbackParameters.speed - speed) < 0.01f
                (if (selected) "✓  " else "") + formatPlaybackSpeed(speed) to {
                    exoPlayer.setPlaybackSpeed(speed)
                    hidePlayerSettings()
                }
            }
        )
    }

    private fun showPlayerAudioSettings() {
        val exoPlayer = player ?: return
        val choices = mutableListOf<Pair<String, () -> Unit>>()
        choices += getString(R.string.automatic) to {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .build()
            hidePlayerSettings()
        }
        exoPlayer.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_AUDIO }
            .forEach { group ->
                for (trackIndex in 0 until group.length) {
                    if (!group.isTrackSupported(trackIndex)) continue
                    val format = group.getTrackFormat(trackIndex)
                    val label = format.label?.takeUnless(String::isBlank)
                        ?: format.language?.takeUnless(String::isBlank)
                            ?.let { Locale.forLanguageTag(it).displayLanguage }
                            ?.takeUnless(String::isBlank)
                        ?: getString(R.string.audio_track, choices.size)
                    val selectedPrefix = if (group.isTrackSelected(trackIndex)) "✓  " else ""
                    choices += "$selectedPrefix$label" to {
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setOverrideForType(
                                TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
                            )
                            .build()
                        hidePlayerSettings()
                    }
                }
            }
        showPlayerSettings(getString(R.string.player_audio), choices.distinctBy { it.first })
    }

    private fun showPlayerSettings(
        title: String,
        options: List<Pair<String, () -> Unit>>
    ) {
        playerView.setControllerShowTimeoutMs(0)
        playerView.showController()
        playerSettingsTitle.text = title
        playerSettingsOptions.removeAllViews()
        options.forEachIndexed { index, (label, action) ->
            val button = Button(this).apply {
                id = View.generateViewId()
                isAllCaps = false
                text = label
                textSize = 17f
                setTextColor(Color.WHITE)
                gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.START
                isFocusable = true
                isFocusableInTouchMode = true
                background = getDrawable(R.drawable.player_settings_option_background)
                setPadding(dp(24), 0, dp(18), 0)
                setOnClickListener { action() }
            }
            playerSettingsOptions.addView(
                button,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(PLAYER_SETTINGS_ROW_HEIGHT_DP)
                ).apply {
                    if (index > 0) topMargin = dp(PLAYER_SETTINGS_ROW_GAP_DP)
                }
            )
        }
        for (index in 0 until playerSettingsOptions.childCount) {
            val option = playerSettingsOptions.getChildAt(index)
            option.nextFocusUpId = playerSettingsOptions
                .getChildAt(maxOf(0, index - 1)).id
            option.nextFocusDownId = playerSettingsOptions
                .getChildAt(minOf(playerSettingsOptions.childCount - 1, index + 1)).id
        }
        playerSettingsScroll.layoutParams = playerSettingsScroll.layoutParams.apply {
            height = dp(
                minOf(
                    options.size * PLAYER_SETTINGS_ROW_HEIGHT_DP +
                        maxOf(0, options.size - 1) * PLAYER_SETTINGS_ROW_GAP_DP,
                    PLAYER_SETTINGS_MAX_LIST_HEIGHT_DP
                )
            )
        }
        playerSettingsPanel.visibility = View.VISIBLE
        playerSettingsScroll.scrollTo(0, 0)
        playerSettingsOptions.getChildAt(0)?.let { firstOption ->
            firstOption.post {
                playerView.clearFocus()
                firstOption.requestFocus()
            }
        }
    }

    private fun hidePlayerSettings() {
        playerSettingsPanel.visibility = View.GONE
        playerSettingsOptions.removeAllViews()
        playerView.setControllerShowTimeoutMs(PLAYER_CONTROLS_TIMEOUT_MS)
        playerView.showController()
    }

    private fun selectedAudioLabel(exoPlayer: ExoPlayer): String = exoPlayer.currentTracks.groups
        .firstNotNullOfOrNull { group ->
            if (group.type != C.TRACK_TYPE_AUDIO) return@firstNotNullOfOrNull null
            (0 until group.length).firstOrNull(group::isTrackSelected)?.let { index ->
                val format = group.getTrackFormat(index)
                format.label?.takeUnless(String::isBlank)
                    ?: format.language?.takeUnless(String::isBlank)
                        ?.let { Locale.forLanguageTag(it).displayLanguage }
            }
        }?.takeUnless(String::isBlank)
        ?: getString(R.string.automatic)

    private fun formatPlaybackSpeed(speed: Float): String =
        if (speed % 1f == 0f) "${speed.toInt()}×" else "$speed×"

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
            BrowseScreen.SEARCH -> showSearch(focusInput = false)
        }
    }

    private fun leaveAccountIfNeeded() {
        if (currentBrowseScreen == BrowseScreen.ACCOUNT) {
            cancelQrLogin()
            followingCall?.cancel()
            creatorVideosCall?.cancel()
        }
        if (currentBrowseScreen == BrowseScreen.SEARCH) {
            searchCall?.cancel()
            searchLoading = false
        }
    }

    private fun showRecommendations(focusContent: Boolean = true) {
        leaveAccountIfNeeded()
        loginPanel.visibility = View.GONE
        historyPanel.visibility = View.GONE
        livePanel.visibility = View.GONE
        followingPanel.visibility = View.GONE
        searchPanel.visibility = View.GONE
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

    private fun refreshRecommendations() {
        recommendationsCall?.cancel()
        recommendationsLoading = false
        recommendationsHaveMore = true
        recommendationPage = 0
        recommendationReturnFocus = null
        authClient.resetRecommendations()
        recommendationsGrid.removeAllViews()
        recommendationsScroll.scrollTo(0, 0)
        loadRecommendations()
    }

    private fun showHistory(focusContent: Boolean = true) {
        leaveAccountIfNeeded()
        recommendationsPanel.visibility = View.GONE
        loginPanel.visibility = View.GONE
        livePanel.visibility = View.GONE
        followingPanel.visibility = View.GONE
        searchPanel.visibility = View.GONE
        historyPanel.visibility = View.VISIBLE
        currentBrowseScreen = BrowseScreen.HISTORY
        updateNavigation(historyButton)
        if (historyGrid.childCount == 0) {
            authClient.resetHistory()
            historyPage = 0
            historyHasMore = true
            historySkipped = 0
            historyLoading = false
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
        searchPanel.visibility = View.GONE
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

    private fun updateNavigation(activeButton: Button?) {
        navigationBar.visibility = View.VISIBLE
        recommendationsButton.isActivated = activeButton === recommendationsButton
        historyButton.isActivated = activeButton === historyButton
        liveButton.isActivated = activeButton === liveButton
        loginButton.isActivated = activeButton === loginButton
    }

    private fun navigationHasFocus(): Boolean =
        recommendationsButton.hasFocus() || historyButton.hasFocus() ||
            liveButton.hasFocus() || loginButton.hasFocus() || searchInput.hasFocus()

    private fun selectLiveSource(source: BilibiliAuthClient.LiveSource) {
        liveRoomsCall?.cancel()
        liveLoading = false
        liveSource = source
        livePage = 0
        liveHasMore = true
        liveReturnFocus = null
        liveGrid.removeAllViews()
        liveScroll.scrollTo(0, 0)
        authClient.resetLiveRooms(source)
        liveTitle.text = getString(
            if (source == BilibiliAuthClient.LiveSource.FOLLOWING) {
                R.string.following_live_title
            } else {
                R.string.popular_live_title
            }
        )
        followingLiveButton.text = if (source == BilibiliAuthClient.LiveSource.FOLLOWING) {
            getString(R.string.selected_option, getString(R.string.following))
        } else {
            getString(R.string.following)
        }
        popularLiveButton.text = if (source == BilibiliAuthClient.LiveSource.POPULAR) {
            getString(R.string.selected_option, getString(R.string.popular))
        } else {
            getString(R.string.popular)
        }
        liveButton.nextFocusDownId = selectedLiveSourceButton().id
        if (!navigationHasFocus()) selectedLiveSourceButton().requestFocus()
        loadLiveRooms()
    }

    private fun loadLiveRooms() {
        if (liveLoading || (livePage > 0 && !liveHasMore)) return
        liveLoading = true
        val source = liveSource
        val firstPage = livePage == 0
        liveStatus.text = if (!firstPage) {
            getString(R.string.loading_more_live)
        } else if (source == BilibiliAuthClient.LiveSource.FOLLOWING) {
            getString(R.string.loading_followed_live)
        } else {
            getString(R.string.loading_popular_live)
        }
        lateinit var requestCall: Call
        requestCall = authClient.fetchLiveRooms(source) { result ->
            runOnUiThread {
                if (liveRoomsCall !== requestCall) return@runOnUiThread
                if (source != liveSource || livePanel.visibility != View.VISIBLE) {
                    liveLoading = false
                    return@runOnUiThread
                }
                result.fold(
                    onSuccess = { page ->
                        livePage = page.page
                        liveHasMore = page.hasMore
                        renderLiveRooms(page.rooms, append = !firstPage)
                        liveLoading = false
                        liveStatus.text = if (liveGrid.childCount == 0) {
                            if (source == BilibiliAuthClient.LiveSource.FOLLOWING) {
                                getString(R.string.no_followed_live)
                            } else {
                                getString(R.string.no_popular_live)
                            }
                        } else {
                            liveSummary()
                        }
                        if (firstPage && page.rooms.isNotEmpty() && !navigationHasFocus()) {
                            liveGrid.post { liveGrid.getChildAt(0)?.requestFocus() }
                        } else if (firstPage && !navigationHasFocus()) {
                            selectedLiveSourceButton().requestFocus()
                        }
                    },
                    onFailure = { error ->
                        liveLoading = false
                        liveStatus.text = getString(R.string.live_request_failed)
                        if (firstPage && !navigationHasFocus()) selectedLiveSourceButton().requestFocus()
                    }
                )
            }
        }
        liveRoomsCall = requestCall
    }

    private fun renderLiveRooms(rooms: List<BilibiliAuthClient.LiveRoom>, append: Boolean) {
        if (!append) {
            coverCalls.forEach(Call::cancel)
            coverCalls.clear()
            liveGrid.removeAllViews()
            liveReturnFocus = null
        }
        val startIndex = liveGrid.childCount
        val cardWidth = gridCardWidth()
        rooms.forEachIndexed { pageIndex, room ->
            val index = startIndex + pageIndex
            val card = LayoutInflater.from(this)
                .inflate(R.layout.recommendation_card, liveGrid, false)
            card.id = View.generateViewId()
            if (index < GRID_COLUMN_COUNT) {
                card.nextFocusUpId = if (index < GRID_COLUMN_COUNT / 2) {
                    R.id.following_live_button
                } else {
                    R.id.popular_live_button
                }
            }
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
                    if (shouldPrefetch(index, liveGrid.childCount) && liveHasMore) {
                        loadLiveRooms()
                    }
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
        value >= 1_000_000L -> String.format(Locale.getDefault(), "%.1fm", value / 1_000_000.0)
        value >= 1_000L -> String.format(Locale.getDefault(), "%.1fk", value / 1_000.0)
        value > 0L -> String.format(Locale.getDefault(), "%,d", value)
        else -> getString(R.string.live_label)
    }

    private fun liveSummary(): String = joinStatus(
        resources.getQuantityString(R.plurals.live_room_count, liveGrid.childCount, liveGrid.childCount),
        getString(if (liveSource == BilibiliAuthClient.LiveSource.FOLLOWING) R.string.following else R.string.popular),
        getString(R.string.page_number, livePage),
        getString(R.string.scroll_for_more).takeIf { liveHasMore }
    )

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
        liveStatus.text = getString(R.string.opening_item, room.title)
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
                        liveStatus.text = getString(R.string.play_live_failed)
                        restoreLiveFocus()
                    }
                )
            }
        }
    }

    private fun loadHistory() {
        if (historyLoading || (historyPage > 0 && !historyHasMore)) return
        historyLoading = true
        val firstPage = historyPage == 0
        historyStatus.text = if (firstPage) {
            getString(R.string.loading_history)
        } else {
            getString(R.string.loading_more_history)
        }
        lateinit var requestCall: Call
        requestCall = authClient.fetchHistory { result ->
            runOnUiThread {
                if (historyCall !== requestCall) return@runOnUiThread
                if (historyPanel.visibility != View.VISIBLE) {
                    historyLoading = false
                    return@runOnUiThread
                }
                result.fold(
                    onSuccess = { page ->
                        historyPage = page.page
                        historyHasMore = page.hasMore
                        historySkipped += page.returnedCount - page.items.size
                        renderHistory(page.items, append = !firstPage)
                        historyLoading = false
                        historyStatus.text = if (historyGrid.childCount == 0) {
                            if (!page.hasMore) getString(R.string.no_history) else
                                getString(R.string.looking_for_history)
                        } else {
                            historySummary()
                        }
                        if (firstPage && page.items.isNotEmpty() && !navigationHasFocus()) {
                            historyGrid.post { historyGrid.getChildAt(0)?.requestFocus() }
                        }
                        if (page.items.isEmpty() && page.hasMore) {
                            historyGrid.post { loadHistory() }
                        }
                    },
                    onFailure = { error ->
                        historyLoading = false
                        historyStatus.text = getString(R.string.history_request_failed)
                        if (firstPage && !navigationHasFocus()) historyButton.requestFocus()
                    }
                )
            }
        }
        historyCall = requestCall
    }

    private fun renderHistory(items: List<BilibiliAuthClient.HistoryItem>, append: Boolean) {
        if (!append) {
            coverCalls.forEach(Call::cancel)
            coverCalls.clear()
            historyGrid.removeAllViews()
            historyReturnFocus = null
        }
        val startIndex = historyGrid.childCount
        val cardWidth = gridCardWidth()
        items.forEachIndexed { pageIndex, item ->
            val index = startIndex + pageIndex
            val card = LayoutInflater.from(this)
                .inflate(R.layout.recommendation_card, historyGrid, false)
            card.id = View.generateViewId()
            if (index < GRID_COLUMN_COUNT) {
                card.nextFocusUpId = R.id.history_button
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
                    if (shouldPrefetch(index, historyGrid.childCount) && historyHasMore) {
                        loadHistory()
                    }
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
        if (progress < 0L || progress >= duration) {
            return getString(R.string.watched_duration, formatDuration(duration))
        }
        if (progress == 0L) return formatDuration(duration)
        return "${formatDuration(progress)} / ${formatDuration(duration)}"
    }

    private fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600L
        val minutes = (seconds % 3600L) / 60L
        val remainingSeconds = seconds % 60L
        return if (hours > 0L) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, remainingSeconds)
        } else {
            String.format(Locale.getDefault(), "%d:%02d", minutes, remainingSeconds)
        }
    }

    private fun viewedAt(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(timestamp * 1_000L))
    }

    private fun historySummary(): String {
        return joinStatus(
            resources.getQuantityString(R.plurals.playable_count, historyGrid.childCount, historyGrid.childCount),
            getString(R.string.page_number, historyPage),
            resources.getQuantityString(
                R.plurals.unsupported_count,
                historySkipped,
                historySkipped
            ).takeIf { historySkipped > 0 },
            getString(R.string.scroll_for_more).takeIf { historyHasMore }
        )
    }

    private fun restoreHistoryFocus() {
        val target = historyReturnFocus
            ?.takeIf { it.parent === historyGrid }
            ?: historyGrid.getChildAt(0)
        target?.requestFocus()
    }

    private fun playHistory(item: BilibiliAuthClient.HistoryItem) {
        videoCall?.cancel()
        historyStatus.text = getString(R.string.opening_item, item.title)
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
                        historyStatus.text = getString(R.string.play_history_failed)
                    }
                )
            }
        }
    }

    private fun loadRecommendations() {
        if (recommendationsLoading || (recommendationPage > 0 && !recommendationsHaveMore)) return
        recommendationsLoading = true
        val firstPage = recommendationPage == 0
        recommendationsStatus.text = if (firstPage) {
            getString(R.string.loading_recommendations)
        } else {
            getString(R.string.loading_more_recommendations)
        }
        refreshRecommendationsButton.isEnabled = false
        lateinit var requestCall: Call
        requestCall = authClient.fetchRecommendations { result ->
            runOnUiThread {
                if (recommendationsCall !== requestCall) return@runOnUiThread
                refreshRecommendationsButton.isEnabled = true
                result.fold(
                    onSuccess = { page ->
                        recommendationPage = page.page
                        recommendationFeedSignedIn = page.signedIn
                        recommendationsHaveMore = page.hasMore
                        renderRecommendations(page.videos, append = !firstPage)
                        recommendationsLoading = false
                        recommendationsStatus.text = if (recommendationsGrid.childCount == 0) {
                            getString(R.string.no_recommendations)
                        } else {
                            recommendationSummary()
                        }
                        if (firstPage && page.videos.isNotEmpty() && !navigationHasFocus() &&
                            recommendationsPanel.visibility == View.VISIBLE
                        ) {
                            recommendationsGrid.post { recommendationsGrid.getChildAt(0)?.requestFocus() }
                        }
                        if (page.videos.isEmpty() && page.hasMore) {
                            recommendationsGrid.post { loadRecommendations() }
                        }
                    },
                    onFailure = {
                        recommendationsLoading = false
                        recommendationsStatus.text = getString(R.string.recommendations_request_failed)
                        if (!navigationHasFocus() && recommendationsPanel.visibility == View.VISIBLE) {
                            refreshRecommendationsButton.requestFocus()
                        }
                    }
                )
            }
        }
        recommendationsCall = requestCall
    }

    private fun renderRecommendations(
        videos: List<BilibiliAuthClient.Recommendation>,
        append: Boolean
    ) {
        if (!append) {
            coverCalls.forEach(Call::cancel)
            coverCalls.clear()
            recommendationsGrid.removeAllViews()
            recommendationReturnFocus = null
        }
        val startIndex = recommendationsGrid.childCount
        val cardWidth = gridCardWidth()
        videos.forEachIndexed { pageIndex, video ->
            val index = startIndex + pageIndex
            val card = LayoutInflater.from(this)
                .inflate(R.layout.recommendation_card, recommendationsGrid, false)
            card.id = View.generateViewId()
            if (index < GRID_COLUMN_COUNT) {
                card.nextFocusUpId = R.id.refresh_recommendations_button
            }
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
                    if (shouldPrefetch(index, recommendationsGrid.childCount) &&
                        recommendationsHaveMore
                    ) {
                        loadRecommendations()
                    }
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
        val resolvedUrl = if (url.startsWith("//")) "https:$url" else url
        imageView.tag = resolvedUrl
        coverCache.get(resolvedUrl)?.let {
            imageView.setImageBitmap(it)
            return
        }
        val request = Request.Builder()
            .url(coverThumbnailUrl(resolvedUrl))
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
                    coverCache.put(resolvedUrl, bitmap)
                    runOnUiThread {
                        if (imageView.tag == resolvedUrl) imageView.setImageBitmap(bitmap)
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

    private fun shouldPrefetch(focusedIndex: Int, loadedItemCount: Int): Boolean =
        loadedItemCount > 0 && focusedIndex >= loadedItemCount - PAGINATION_PREFETCH_ITEMS

    private fun recommendationSummary(): String {
        val session = getString(if (recommendationFeedSignedIn) R.string.signed_in else R.string.guest)
        return joinStatus(
            resources.getQuantityString(
                R.plurals.video_count,
                recommendationsGrid.childCount,
                recommendationsGrid.childCount
            ),
            getString(R.string.mobile_session, session),
            getString(R.string.page_number, recommendationPage),
            getString(R.string.scroll_for_more).takeIf { recommendationsHaveMore },
            getString(R.string.press_ok_to_play)
        )
    }

    private fun restoreRecommendationFocus() {
        val target = recommendationReturnFocus
            ?.takeIf { it.parent === recommendationsGrid }
            ?: recommendationsGrid.getChildAt(0)
        target?.requestFocus()
    }

    private fun playRecommendation(video: BilibiliAuthClient.Recommendation) {
        videoCall?.cancel()
        recommendationsStatus.text = getString(R.string.opening_item, video.title)
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
                    onFailure = {
                        recommendationsStatus.text = getString(R.string.play_video_failed)
                    }
                )
            }
        }
    }

    private fun showSearch(focusInput: Boolean = true) {
        if (currentBrowseScreen != BrowseScreen.SEARCH) leaveAccountIfNeeded()
        recommendationsPanel.visibility = View.GONE
        historyPanel.visibility = View.GONE
        livePanel.visibility = View.GONE
        loginPanel.visibility = View.GONE
        followingPanel.visibility = View.GONE
        searchPanel.visibility = View.VISIBLE
        currentBrowseScreen = BrowseScreen.SEARCH
        updateNavigation(null)
        searchStatus.text = if (searchQuery.isBlank()) {
            getString(R.string.search_prompt)
        } else {
            searchSummary()
        }
        if (focusInput) searchInput.requestFocus()
    }

    private fun submitSearch() {
        val query = searchInput.text.toString().trim()
        showSearch(focusInput = false)
        if (query.isBlank()) {
            searchStatus.text = getString(R.string.search_empty)
            searchInput.requestFocus()
            return
        }
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(searchInput.windowToken, 0)
        searchCall?.cancel()
        searchQuery = query
        searchPage = 0
        searchTotal = 0
        searchHasMore = true
        searchLoading = false
        searchReturnFocus = null
        searchGrid.removeAllViews()
        searchScroll.scrollTo(0, 0)
        loadSearchResults()
    }

    private fun loadSearchResults() {
        if (searchQuery.isBlank() || searchLoading || (searchPage > 0 && !searchHasMore)) return
        searchLoading = true
        val firstPage = searchPage == 0
        val requestedPage = searchPage + 1
        searchStatus.text = if (firstPage) {
            getString(R.string.searching_for, searchQuery)
        } else {
            getString(R.string.loading_more_search)
        }
        lateinit var requestCall: Call
        try {
            requestCall = authClient.fetchSearchVideos(searchQuery, requestedPage) { result ->
                runOnUiThread {
                    if (searchCall !== requestCall) return@runOnUiThread
                    if (searchPanel.visibility != View.VISIBLE) {
                        searchLoading = false
                        return@runOnUiThread
                    }
                    result.fold(
                        onSuccess = { page ->
                            searchPage = page.page
                            searchTotal = page.total
                            searchHasMore = page.hasMore
                            renderSearchResults(page.videos, append = !firstPage)
                            searchLoading = false
                            searchStatus.text = if (searchGrid.childCount == 0) {
                                getString(R.string.no_search_results, searchQuery)
                            } else {
                                searchSummary()
                            }
                            if (firstPage && page.videos.isNotEmpty()) {
                                searchGrid.post { searchGrid.getChildAt(0)?.requestFocus() }
                            }
                        },
                        onFailure = {
                            searchLoading = false
                            searchStatus.text = getString(R.string.search_failed)
                            if (firstPage) searchInput.requestFocus()
                        }
                    )
                }
            }
        } catch (error: IllegalStateException) {
            searchLoading = false
            searchStatus.text = getString(R.string.search_starting)
            searchInput.requestFocus()
            return
        }
        searchCall = requestCall
    }

    private fun renderSearchResults(
        videos: List<BilibiliAuthClient.SearchVideo>,
        append: Boolean
    ) {
        if (!append) {
            coverCalls.forEach(Call::cancel)
            coverCalls.clear()
            searchGrid.removeAllViews()
            searchReturnFocus = null
        }
        val startIndex = searchGrid.childCount
        val cardWidth = gridCardWidth()
        videos.forEachIndexed { pageIndex, video ->
            val index = startIndex + pageIndex
            val card = LayoutInflater.from(this)
                .inflate(R.layout.recommendation_card, searchGrid, false)
            card.id = View.generateViewId()
            if (index < GRID_COLUMN_COUNT) {
                card.nextFocusUpId = R.id.search_input
            }
            if (index == 0) {
                searchInput.nextFocusDownId = card.id
            }
            val title = cleanSearchText(video.title)
            val duration = normalizeSearchDuration(video.duration)
            val cover = card.findViewById<ImageView>(R.id.recommendation_cover)
            card.findViewById<TextView>(R.id.recommendation_title).text = title
            card.findViewById<TextView>(R.id.recommendation_duration).text = duration
            card.findViewById<TextView>(R.id.recommendation_meta).text =
                listOf(video.uploader, video.viewCount)
                    .filter(String::isNotBlank)
                    .joinToString("  •  ")
            card.contentDescription = listOf(title, video.uploader, duration)
                .filter(String::isNotBlank)
                .joinToString(", ")
            card.setOnClickListener {
                searchReturnFocus = card
                playSearchResult(video)
            }
            card.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    searchReturnFocus = view
                    snapGridToFocusedRow(searchScroll, searchGrid, index)
                    if (shouldPrefetch(index, searchGrid.childCount) && searchHasMore) {
                        loadSearchResults()
                    }
                }
                view.animate()
                    .scaleX(if (hasFocus) 1.055f else 1f)
                    .scaleY(if (hasFocus) 1.055f else 1f)
                    .setDuration(120L)
                    .start()
                view.elevation = if (hasFocus) 18f else 0f
            }
            searchGrid.addView(
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

    @Suppress("DEPRECATION")
    private fun cleanSearchText(value: String): String = Html.fromHtml(value).toString().trim()

    private fun normalizeSearchDuration(value: String): String = value.split(':')
        .mapIndexed { index, part -> if (index == 0) part else part.padStart(2, '0') }
        .joinToString(":")

    private fun searchSummary(): String = joinStatus(
        resources.getQuantityString(
            R.plurals.video_loaded_count,
            searchGrid.childCount,
            searchGrid.childCount
        ),
        getString(R.string.page_number, searchPage),
        resources.getQuantityString(R.plurals.results_total, searchTotal, searchTotal),
        getString(R.string.scroll_for_more).takeIf { searchHasMore }
    )

    private fun restoreSearchFocus() {
        val target = searchReturnFocus
            ?.takeIf { it.parent === searchGrid }
            ?: searchGrid.getChildAt(0)
            ?: searchInput
        target.requestFocus()
    }

    private fun playSearchResult(video: BilibiliAuthClient.SearchVideo) {
        videoCall?.cancel()
        searchStatus.text = getString(R.string.opening_item, cleanSearchText(video.title))
        videoCall = authClient.fetchSearchVideoUrl(video) { result ->
            runOnUiThread {
                result.fold(
                    onSuccess = { url ->
                        playerHint.text = getString(R.string.search_player_hint)
                        playMedia(
                            url = url,
                            referer = "https://www.bilibili.com/video/av${video.aid}",
                            returnScreen = PlaybackReturnScreen.SEARCH
                        )
                    },
                    onFailure = {
                        searchStatus.text = getString(R.string.play_search_failed)
                        restoreSearchFocus()
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
        // Keep focus on a visible Account control while the first creator page loads.
        // Otherwise older Android TV versions may move focus to the first nav tab when
        // the focused Following button is hidden, which navigates back to recommendations.
        loginButton.requestFocus()
        recommendationsPanel.visibility = View.GONE
        historyPanel.visibility = View.GONE
        livePanel.visibility = View.GONE
        loginPanel.visibility = View.GONE
        searchPanel.visibility = View.GONE
        followingPanel.visibility = View.VISIBLE
        currentBrowseScreen = BrowseScreen.ACCOUNT
        updateNavigation(loginButton)
        followingMode = FollowingMode.CREATORS
        followingTitle.text = getString(R.string.following_accounts_title)
        followingBackButton.visibility = View.GONE
        loginButton.nextFocusDownId = View.NO_ID
        if (reset || followedCreators.isEmpty()) {
            followingCall?.cancel()
            followingLoading = false
            authClient.resetFollowing()
            followingPage = 0
            followingTotal = 0
            followingHasMore = true
            followedCreatorFocusIndex = 0
            followedCreators = emptyList()
            followingGrid.removeAllViews()
            followingScroll.scrollTo(0, 0)
            loadFollowingCreators(account.mid)
        } else {
            renderFollowedCreators(followedCreators, append = false)
            followingStatus.text = followingCreatorsSummary()
            restoreFollowingFocus(followedCreatorFocusIndex)
        }
    }

    private fun loadFollowingCreators(accountId: Long? = currentAccount?.mid) {
        val resolvedAccountId = accountId ?: return
        if (followingLoading || (followingPage > 0 && !followingHasMore)) return
        followingLoading = true
        val firstPage = followingPage == 0
        followingStatus.text = if (firstPage) {
            getString(R.string.loading_following)
        } else {
            getString(R.string.loading_more_following)
        }
        lateinit var requestCall: Call
        requestCall = authClient.fetchFollowing(resolvedAccountId) { result ->
            runOnUiThread {
                if (followingCall !== requestCall) return@runOnUiThread
                if (followingMode != FollowingMode.CREATORS ||
                    followingPanel.visibility != View.VISIBLE
                ) {
                    followingLoading = false
                    return@runOnUiThread
                }
                result.fold(
                    onSuccess = { page ->
                        followedCreators = if (firstPage) {
                            page.creators
                        } else {
                            followedCreators + page.creators
                        }
                        followingPage = page.page
                        followingTotal = page.total
                        followingHasMore = page.hasMore
                        renderFollowedCreators(page.creators, append = !firstPage)
                        followingLoading = false
                        followingStatus.text = if (followingGrid.childCount == 0) {
                            getString(R.string.no_following)
                        } else {
                            followingCreatorsSummary()
                        }
                        if (firstPage) {
                            followedCreatorFocusIndex = 0
                            restoreFollowingFocus(0)
                        }
                    },
                    onFailure = {
                        followingLoading = false
                        followingStatus.text = getString(R.string.following_request_failed)
                        if (firstPage && !navigationHasFocus()) loginButton.requestFocus()
                    }
                )
            }
        }
        followingCall = requestCall
    }

    private fun renderFollowedCreators(
        creators: List<BilibiliAuthClient.FollowedCreator>,
        append: Boolean
    ) {
        if (!append) {
            coverCalls.forEach(Call::cancel)
            coverCalls.clear()
            followingGrid.removeAllViews()
        }
        val startIndex = followingGrid.childCount
        val cardWidth = gridCardWidth()
        creators.forEachIndexed { pageIndex, creator ->
            val index = startIndex + pageIndex
            val card = LayoutInflater.from(this)
                .inflate(R.layout.recommendation_card, followingGrid, false)
            card.id = View.generateViewId()
            if (index < GRID_COLUMN_COUNT) {
                card.nextFocusUpId = R.id.login_button
            }
            if (index == 0) loginButton.nextFocusDownId = card.id
            val avatar = card.findViewById<ImageView>(R.id.recommendation_cover)
            avatar.scaleType = ImageView.ScaleType.FIT_CENTER
            card.findViewById<TextView>(R.id.recommendation_title).text = creator.name
            card.findViewById<TextView>(R.id.recommendation_duration).text = ""
            card.findViewById<TextView>(R.id.recommendation_meta).text =
                creator.description.ifBlank { getString(R.string.followed_creator) }
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
                    if (shouldPrefetch(index, followingGrid.childCount) && followingHasMore) {
                        loadFollowingCreators()
                    }
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
        loginButton.nextFocusDownId = R.id.following_back_button
        followingBackButton.requestFocus()
        if (reset || creatorVideos.isEmpty()) {
            creatorVideosCall?.cancel()
            creatorVideosLoading = false
            creatorVideoPage = 0
            creatorVideoTotal = 0
            creatorVideosHaveMore = true
            creatorVideoFocusIndex = 0
            creatorVideos = emptyList()
            followingGrid.removeAllViews()
            followingScroll.scrollTo(0, 0)
            loadCreatorVideos()
        } else {
            renderCreatorVideos(creatorVideos, append = false)
            followingStatus.text = creatorVideosSummary()
            restoreFollowingFocus(creatorVideoFocusIndex)
        }
    }

    private fun loadCreatorVideos() {
        val creator = selectedCreator ?: return
        if (creatorVideosLoading || (creatorVideoPage > 0 && !creatorVideosHaveMore)) return
        creatorVideosLoading = true
        val firstPage = creatorVideoPage == 0
        val requestedPage = creatorVideoPage + 1
        followingStatus.text = if (firstPage) {
            getString(R.string.loading_creator_videos)
        } else {
            getString(R.string.loading_more_creator_videos)
        }
        lateinit var requestCall: Call
        requestCall = authClient.fetchCreatorVideos(creator, requestedPage) { result ->
            runOnUiThread {
                if (creatorVideosCall !== requestCall) return@runOnUiThread
                if (followingMode != FollowingMode.VIDEOS || selectedCreator != creator ||
                    followingPanel.visibility != View.VISIBLE
                ) {
                    creatorVideosLoading = false
                    return@runOnUiThread
                }
                result.fold(
                    onSuccess = { page ->
                        creatorVideos = if (firstPage) {
                            page.videos
                        } else {
                            creatorVideos + page.videos
                        }
                        creatorVideoPage = page.page
                        creatorVideoTotal = page.total
                        creatorVideosHaveMore = page.hasMore
                        renderCreatorVideos(page.videos, append = !firstPage)
                        creatorVideosLoading = false
                        followingStatus.text = if (followingGrid.childCount == 0) {
                            getString(R.string.no_creator_videos)
                        } else {
                            creatorVideosSummary()
                        }
                        if (firstPage) {
                            creatorVideoFocusIndex = 0
                            restoreFollowingFocus(0)
                        }
                    },
                    onFailure = {
                        creatorVideosLoading = false
                        followingStatus.text = getString(R.string.creator_videos_request_failed)
                        if (firstPage && !navigationHasFocus()) followingBackButton.requestFocus()
                    }
                )
            }
        }
        creatorVideosCall = requestCall
    }

    private fun renderCreatorVideos(
        videos: List<BilibiliAuthClient.CreatorVideo>,
        append: Boolean
    ) {
        if (!append) {
            coverCalls.forEach(Call::cancel)
            coverCalls.clear()
            followingGrid.removeAllViews()
        }
        val startIndex = followingGrid.childCount
        val cardWidth = gridCardWidth()
        videos.forEachIndexed { pageIndex, video ->
            val index = startIndex + pageIndex
            val card = LayoutInflater.from(this)
                .inflate(R.layout.recommendation_card, followingGrid, false)
            card.id = View.generateViewId()
            if (index < GRID_COLUMN_COUNT) {
                card.nextFocusUpId = R.id.following_back_button
            }
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
                    if (shouldPrefetch(index, followingGrid.childCount) &&
                        creatorVideosHaveMore
                    ) {
                        loadCreatorVideos()
                    }
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
        followingStatus.text = getString(R.string.opening_item, video.title)
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
                    onFailure = {
                        followingStatus.text = getString(R.string.play_creator_video_failed)
                        restoreFollowingFocus(creatorVideoFocusIndex)
                    }
                )
            }
        }
    }

    private fun followingCreatorsSummary(): String = joinStatus(
        resources.getQuantityString(
            R.plurals.creator_loaded_count,
            followedCreators.size,
            followedCreators.size
        ),
        getString(R.string.page_number, followingPage),
        resources.getQuantityString(R.plurals.total_count, followingTotal, followingTotal),
        getString(R.string.scroll_for_more).takeIf { followingHasMore }
    )

    private fun creatorVideosSummary(): String = joinStatus(
        resources.getQuantityString(
            R.plurals.video_loaded_count,
            creatorVideos.size,
            creatorVideos.size
        ),
        getString(R.string.page_number, creatorVideoPage),
        resources.getQuantityString(R.plurals.total_count, creatorVideoTotal, creatorVideoTotal),
        getString(R.string.scroll_for_more).takeIf { creatorVideosHaveMore }
    )

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
                            getString(R.string.account_signed_in_description, it.name)
                        } ?: getString(R.string.account_signed_out_description)
                        if (currentBrowseScreen == BrowseScreen.ACCOUNT) {
                            account?.let(::showSignedInAccount) ?: startQrLogin()
                        }
                    },
                    onFailure = {
                        currentAccount = null
                        accountCheckComplete = true
                        accountCheckError = getString(R.string.account_check_failed)
                        loginButton.contentDescription = getString(R.string.account_check_failed_description)
                        if (currentBrowseScreen == BrowseScreen.ACCOUNT) {
                            showAccountError()
                        }
                    }
                )
            }
        }
    }

    private fun showAccount() {
        if (currentBrowseScreen == BrowseScreen.SEARCH) {
            searchCall?.cancel()
            searchLoading = false
        }
        followingCall?.cancel()
        creatorVideosCall?.cancel()
        recommendationsPanel.visibility = View.GONE
        historyPanel.visibility = View.GONE
        livePanel.visibility = View.GONE
        followingPanel.visibility = View.GONE
        searchPanel.visibility = View.GONE
        loginPanel.visibility = View.VISIBLE
        currentBrowseScreen = BrowseScreen.ACCOUNT
        updateNavigation(loginButton)
        when {
            !accountCheckComplete -> showAccountChecking()
            currentAccount != null -> showSignedInAccount(currentAccount!!)
            accountCheckError != null -> showAccountError()
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
        loginButton.nextFocusDownId = View.NO_ID
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
        newQrButton.nextFocusUpId = R.id.following_accounts_button
        newQrButton.isEnabled = true
        newQrButton.visibility = View.VISIBLE
    }

    private fun showAccountError() {
        cancelQrLogin()
        qrImage.visibility = View.GONE
        setLoginDetailsStartMargin(0)
        loginTitle.text = getString(R.string.account_title)
        loginStatus.text = getString(R.string.account_check_failed)
        followingAccountsButton.visibility = View.GONE
        newQrButton.text = getString(R.string.new_qr_code)
        newQrButton.isEnabled = true
        newQrButton.visibility = View.VISIBLE
        loginButton.nextFocusDownId = R.id.new_qr_button
        newQrButton.nextFocusUpId = R.id.login_button
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
        searchPanel.visibility = View.GONE
        currentBrowseScreen = BrowseScreen.ACCOUNT
        updateNavigation(loginButton)
        loginPanel.visibility = View.VISIBLE
        qrImage.visibility = View.VISIBLE
        qrImage.setImageDrawable(null)
        setLoginDetailsStartMargin(36)
        loginTitle.text = getString(R.string.qr_login_title)
        loginStatus.text = getString(R.string.requesting_qr)
        loginButton.nextFocusDownId = R.id.new_qr_button
        followingAccountsButton.visibility = View.GONE
        newQrButton.text = getString(R.string.new_qr_code)
        newQrButton.nextFocusUpId = R.id.login_button
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
                    onFailure = {
                        loginStatus.text = getString(R.string.create_qr_failed)
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
                        loginStatus.text = getString(qrStateString(poll.state))
                        when (poll.state) {
                            BilibiliAuthClient.QrState.AUTHENTICATED -> {
                                qrKey = null
                                mainHandler.postDelayed({
                                    loginStatus.text = getString(R.string.loading_account)
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
                    onFailure = {
                        loginStatus.text = getString(R.string.qr_check_failed)
                        scheduleQrPoll()
                    }
                )
            }
        }
    }

    private fun qrStateString(state: BilibiliAuthClient.QrState): Int = when (state) {
        BilibiliAuthClient.QrState.WAITING_FOR_SCAN -> R.string.qr_waiting_scan
        BilibiliAuthClient.QrState.WAITING_FOR_CONFIRMATION -> R.string.qr_waiting_confirmation
        BilibiliAuthClient.QrState.EXPIRED -> R.string.qr_expired
        BilibiliAuthClient.QrState.AUTHENTICATED -> R.string.qr_signed_in
    }

    private fun joinStatus(vararg parts: String?): String =
        parts.filterNotNull().filter(String::isNotBlank).joinToString(" • ")

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

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) schedulePlayerHintHide()
                    }
                })
                playerView.player = exoPlayer
                playerView.setControllerShowTimeoutMs(PLAYER_CONTROLS_TIMEOUT_MS)
                playerView.setControllerAutoShow(false)
                playerView.hideController()
                navigationBar.visibility = View.GONE
                recommendationsPanel.visibility = View.GONE
                historyPanel.visibility = View.GONE
                livePanel.visibility = View.GONE
                loginPanel.visibility = View.GONE
                followingPanel.visibility = View.GONE
                searchPanel.visibility = View.GONE
                playerView.visibility = View.VISIBLE
                mainHandler.removeCallbacks(playerHintHideRunnable)
                playerHint.animate().cancel()
                playerHint.alpha = 1f
                playerHint.visibility = View.VISIBLE
                exoPlayer.setMediaItem(MediaItem.fromUri(url))
                if (startPositionMs > 0L) exoPlayer.seekTo(startPositionMs)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
                startPlaybackTracking(tracking)
            }
    }

    private fun schedulePlayerHintHide() {
        mainHandler.removeCallbacks(playerHintHideRunnable)
        mainHandler.postDelayed(playerHintHideRunnable, PLAYER_HINT_VISIBLE_MS)
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
        mainHandler.removeCallbacks(playerHintHideRunnable)
        playerRevealKeyCode = KeyEvent.KEYCODE_UNKNOWN
        playerControlsVisible = false
        playerSettingsPanel.visibility = View.GONE
        playerSettingsOptions.removeAllViews()
        playerHint.animate().cancel()
        playerHint.alpha = 1f
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
        searchPanel.visibility = View.GONE
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
            PlaybackReturnScreen.SEARCH -> {
                currentBrowseScreen = BrowseScreen.SEARCH
                updateNavigation(null)
                searchPanel.visibility = View.VISIBLE
                searchStatus.text = searchSummary()
            }
        }
    }

    private fun showPlaybackError(error: PlaybackException) {
        val returnScreen = playbackReturnScreen
        val message = getString(R.string.playback_failed, error.errorCodeName)
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
            PlaybackReturnScreen.SEARCH -> {
                searchStatus.text = message
                restoreSearchFocus()
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (player != null) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    if (playerSettingsPanel.visibility == View.VISIBLE) {
                        hidePlayerSettings()
                    } else if (playerControlsVisible) {
                        playerView.hideController()
                    } else {
                        stopPlaybackAndRestoreFocus()
                    }
                }
                return true
            }

            if (isPlayerNavigationKey(event.keyCode)) {
                if (event.action == KeyEvent.ACTION_UP &&
                    playerRevealKeyCode == event.keyCode
                ) {
                    playerRevealKeyCode = KeyEvent.KEYCODE_UNKNOWN
                    return true
                }
                if (event.action == KeyEvent.ACTION_DOWN &&
                    !playerControlsVisible
                ) {
                    playerRevealKeyCode = event.keyCode
                    playerView.showController()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun isPlayerNavigationKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER -> true
        else -> false
    }

    private fun stopPlaybackAndRestoreFocus() {
        val returnScreen = playbackReturnScreen
        stopPlayback()
        when (returnScreen) {
            PlaybackReturnScreen.RECOMMENDATIONS -> restoreRecommendationFocus()
            PlaybackReturnScreen.HISTORY -> restoreHistoryFocus()
            PlaybackReturnScreen.LIVE -> restoreLiveFocus()
            PlaybackReturnScreen.FOLLOWING -> restoreFollowingFocus(creatorVideoFocusIndex)
            PlaybackReturnScreen.SEARCH -> restoreSearchFocus()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
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
        if (keyCode == KeyEvent.KEYCODE_BACK && searchPanel.visibility == View.VISIBLE) {
            searchCall?.cancel()
            searchLoading = false
            showRecommendations()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (player != null) {
            if (playerControlsVisible) playerView.hideController()
            else stopPlaybackAndRestoreFocus()
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
        } else if (searchPanel.visibility == View.VISIBLE) {
            searchCall?.cancel()
            searchLoading = false
            showRecommendations()
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
        searchCall?.cancel()
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
        FOLLOWING,
        SEARCH
    }

    private enum class FollowingMode {
        CREATORS,
        VIDEOS
    }

    private enum class BrowseScreen {
        RECOMMENDATIONS,
        HISTORY,
        LIVE,
        ACCOUNT,
        SEARCH
    }

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 5.1; BileebileeTV/0.1) AppleWebKit/537.36 Mobile Safari/537.36"
        const val QR_POLL_INTERVAL_MS = 2_000L
        const val TAB_SWITCH_DELAY_MS = 150L
        const val FIRST_HEARTBEAT_DELAY_MS = 5_000L
        const val HEARTBEAT_INTERVAL_MS = 15_000L
        const val PLAYER_HINT_VISIBLE_MS = 3_500L
        const val PLAYER_HINT_FADE_MS = 250L
        const val PLAYER_CONTROLS_TIMEOUT_MS = 4_000
        const val PLAYER_CONTROLS_SIDE_INSET_DP = 48
        const val PLAYER_CONTROLS_BOTTOM_INSET_DP = 64
        const val PLAYER_SETTINGS_ROW_HEIGHT_DP = 48
        const val PLAYER_SETTINGS_ROW_GAP_DP = 0
        const val PLAYER_SETTINGS_MAX_LIST_HEIGHT_DP = 264
        const val COVER_WIDTH_PX = 640
        const val COVER_HEIGHT_PX = 360
        const val GRID_SIDE_PADDING_DP = 72
        const val GRID_COLUMN_COUNT = 4
        const val PAGINATION_PREFETCH_ITEMS = GRID_COLUMN_COUNT * 2
        const val CARD_MARGIN_DP = 6
    }
}
