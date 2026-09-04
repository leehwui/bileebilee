package com.bileebilee.tv

import android.content.Context
import android.os.Build
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

class BilibiliAuthClient(
    context: Context,
    private val httpClient: OkHttpClient
) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val deviceName = Build.MODEL.orEmpty().ifBlank { "Android TV" }
    private val buvid = stableIdentifier(BUVID_KEY, "XY", "infoc", 32)
    private val feedSessionId = stableIdentifier(FEED_SESSION_KEY, length = 16)
    private val mobileUserAgent =
        "Mozilla/5.0 BiliDroid/$ANDROID_APP_VERSION os/android model/$deviceName " +
            "mobi_app/android build/$ANDROID_BUILD channel/master " +
            "osVer/${Build.VERSION.RELEASE} network/2"
    private var feedCursor = 0L
    private var feedPage = 0
    private var historyCursorMax = 0L
    private var historyCursorViewAt = 0L
    private var historyCursorBusiness = ""
    private var historyPage = 0
    private var livePage = 0

    fun generateQr(callback: (Result<QrChallenge>) -> Unit): Call {
        val request = Request.Builder()
            .url("https://passport.bilibili.com/x/passport-login/web/qrcode/generate")
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://www.bilibili.com/")
            .build()
        return enqueueJson(request, callback) { root ->
            requireApiSuccess(root)
            val data = root.getJSONObject("data")
            QrChallenge(
                url = data.getString("url"),
                key = data.getString("qrcode_key")
            )
        }
    }

    fun pollQr(key: String, callback: (Result<QrPollResult>) -> Unit): Call {
        val request = Request.Builder()
            .url("https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=$key")
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://www.bilibili.com/")
            .build()
        val call = httpClient.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (!call.isCanceled()) callback(Result.failure(error))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    callback(runCatching {
                        if (!it.isSuccessful) error("HTTP ${it.code}")
                        val root = JSONObject(it.body?.string().orEmpty())
                        requireApiSuccess(root)
                        val data = root.getJSONObject("data")
                        when (val code = data.getInt("code")) {
                            0 -> {
                                saveSession(it, data.optString("url"))
                                QrPollResult(QrState.AUTHENTICATED, "Signed in successfully")
                            }
                            86101 -> QrPollResult(QrState.WAITING_FOR_SCAN, "Waiting to be scanned…")
                            86090 -> QrPollResult(
                                QrState.WAITING_FOR_CONFIRMATION,
                                "Scanned. Confirm sign-in on your phone."
                            )
                            86038 -> QrPollResult(QrState.EXPIRED, "This QR code has expired.")
                            else -> error(data.optString("message", "QR login state $code"))
                        }
                    })
                }
            }
        })
        return call
    }

    fun checkSession(callback: (Result<Account?>) -> Unit): Call? {
        val cookies = cookieHeader()
        if (cookies.isBlank()) {
            callback(Result.success(null))
            return null
        }
        val request = Request.Builder()
            .url("https://api.bilibili.com/x/web-interface/nav")
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://www.bilibili.com/")
            .header("Cookie", cookies)
            .build()
        return enqueueJson(request, callback) { root ->
            requireApiSuccess(root)
            val data = root.getJSONObject("data")
            if (!data.optBoolean("isLogin")) {
                clearSession()
                null
            } else {
                Account(data.getLong("mid"), data.getString("uname"))
            }
        }
    }

    fun fetchRecommendations(callback: (Result<RecommendationPage>) -> Unit): Call {
        val initialRequest = feedCursor == 0L
        val statistics = JSONObject()
            .put("appId", 1)
            .put("platform", 3)
            .put("version", ANDROID_APP_VERSION)
            .put("abtest", "")
            .toString()
        val url = FEED_URL.toHttpUrl().newBuilder()
            .addQueryParameter("appkey", ANDROID_APP_KEY)
            .addQueryParameter("build", ANDROID_BUILD)
            .addQueryParameter("mobi_app", "android")
            .addQueryParameter("platform", "android")
            .addQueryParameter("device", "phone")
            .addQueryParameter("device_name", deviceName)
            .addQueryParameter("channel", "master")
            .addQueryParameter("network", "wifi")
            .addQueryParameter("column", "4")
            .addQueryParameter("pull", initialRequest.toString())
            .addQueryParameter("idx", feedCursor.toString())
            .addQueryParameter("login_event", if (cookieHeader().isBlank()) "1" else "0")
            .addQueryParameter("c_locale", "zh_CN")
            .addQueryParameter("s_locale", "zh_CN")
            .addQueryParameter("fnval", "4048")
            .addQueryParameter("fnver", "0")
            .addQueryParameter("force_host", "2")
            .addQueryParameter("fourk", "0")
            .addQueryParameter("inline_danmu", "2")
            .addQueryParameter("inline_sound", "0")
            .addQueryParameter("recsys_mode", "0")
            .addQueryParameter("statistics", statistics)
            .addQueryParameter("ts", (System.currentTimeMillis() / 1_000L).toString())
            .build()
        val request = authenticatedRequest(url.toString())
            .header("User-Agent", mobileUserAgent)
            .header("APP-KEY", "android64")
            .header("Buvid", buvid)
            .header("env", "prod")
            .header("session_id", feedSessionId)
            .build()
        return enqueueJson(request, callback) { root ->
            requireApiSuccess(root)
            val items = root.getJSONObject("data").getJSONArray("items")
            var nextCursor = feedCursor
            val videos = buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    item.optLong("idx").takeIf { it > 0L }?.let { nextCursor = it }
                    if (item.optString("goto") !in PLAYABLE_FEED_TYPES ||
                        item.optInt("can_play", 1) == 0
                    ) continue
                    val playerArgs = item.optJSONObject("player_args") ?: continue
                    val aid = playerArgs.optLong("aid")
                    val cid = playerArgs.optLong("cid")
                    if (aid <= 0L || cid <= 0L) continue
                    add(
                        Recommendation(
                            aid = aid,
                            cid = cid,
                            title = item.optString("title", "Untitled video"),
                            coverUrl = item.optString("cover").replace("http://", "https://"),
                            uploader = item.optJSONObject("desc_button")?.optString("text").orEmpty(),
                            viewCount = item.optString("cover_left_text_1"),
                            duration = item.optString("cover_right_text")
                        )
                    )
                }
            }.take(20)
            if (nextCursor > 0L) feedCursor = nextCursor
            feedPage += 1
            RecommendationPage(
                videos = videos,
                page = feedPage,
                signedIn = cookieHeader().isNotBlank()
            )
        }
    }

    fun fetchVideoUrl(
        video: Recommendation,
        callback: (Result<String>) -> Unit
    ): Call = fetchArchiveVideoUrl(video.aid, video.cid, callback)

    fun resetLiveRooms() {
        livePage = 0
    }

    fun fetchLiveRooms(callback: (Result<LiveRoomPage>) -> Unit): Call {
        val requestedPage = livePage + 1
        val url = LIVE_ROOMS_URL.toHttpUrl().newBuilder()
            .addQueryParameter("parent_area_id", "0")
            .addQueryParameter("area_id", "0")
            .addQueryParameter("page", requestedPage.toString())
            .addQueryParameter("page_size", LIVE_PAGE_SIZE.toString())
            .addQueryParameter("sort_type", "online")
            .build()
        val request = authenticatedRequest(url.toString())
            .header("Referer", "https://live.bilibili.com/")
            .build()
        return enqueueJson(request, callback) { root ->
            requireApiSuccess(root)
            val data = root.getJSONObject("data")
            val list = data.optJSONArray("list")
            val rooms = buildList {
                if (list != null) {
                    for (index in 0 until list.length()) {
                        val item = list.optJSONObject(index) ?: continue
                        val roomId = item.optLong("roomid")
                        if (roomId <= 0L) continue
                        val cover = item.optString("cover")
                            .ifBlank { item.optString("system_cover") }
                            .replace("http://", "https://")
                        add(
                            LiveRoom(
                                roomId = roomId,
                                title = item.optString("title", "Untitled live room"),
                                coverUrl = cover,
                                anchor = item.optString("uname"),
                                popularity = item.optLong("online"),
                                parentArea = item.optString("parent_name"),
                                area = item.optString("area_name")
                            )
                        )
                    }
                }
            }
            livePage = requestedPage
            LiveRoomPage(
                rooms = rooms,
                page = livePage,
                hasMore = rooms.size >= LIVE_PAGE_SIZE
            )
        }
    }

    fun fetchLiveStreamUrl(
        room: LiveRoom,
        callback: (Result<String>) -> Unit
    ): Call {
        val url = LIVE_PLAY_URL.toHttpUrl().newBuilder()
            .addQueryParameter("room_id", room.roomId.toString())
            .addQueryParameter("protocol", "0,1")
            .addQueryParameter("format", "0,1,2")
            .addQueryParameter("codec", "0")
            .addQueryParameter("qn", "80")
            .addQueryParameter("platform", "web")
            .addQueryParameter("ptype", "8")
            .build()
        val request = authenticatedRequest(url.toString())
            .header("Referer", "https://live.bilibili.com/${room.roomId}")
            .build()
        return enqueueJson(request, callback) { root ->
            requireApiSuccess(root)
            val playUrl = root.optJSONObject("data")
                ?.optJSONObject("playurl_info")
                ?.optJSONObject("playurl")
                ?: error("Playback data was missing")
            selectLiveStreamUrl(playUrl.optJSONArray("stream"))
                ?: error("No AVC playback URL was returned")
        }
    }

    fun resetHistory() {
        historyCursorMax = 0L
        historyCursorViewAt = 0L
        historyCursorBusiness = ""
        historyPage = 0
    }

    fun fetchHistory(callback: (Result<HistoryPage>) -> Unit): Call {
        val url = HISTORY_URL.toHttpUrl().newBuilder()
            .addQueryParameter("max", historyCursorMax.toString())
            .addQueryParameter("view_at", historyCursorViewAt.toString())
            .addQueryParameter("business", historyCursorBusiness)
            .addQueryParameter("ps", "20")
            .build()
        val request = authenticatedRequest(url.toString()).build()
        return enqueueJson(request, callback) { root ->
            requireApiSuccess(root)
            val data = root.getJSONObject("data")
            val list = data.optJSONArray("list")
            val items = buildList {
                if (list != null) {
                    for (index in 0 until list.length()) {
                        val item = list.optJSONObject(index) ?: continue
                        val history = item.optJSONObject("history") ?: continue
                        val business = history.optString("business")
                        val aid = history.optLong("oid")
                        val cid = history.optLong("cid")
                        if (business !in PLAYABLE_HISTORY_TYPES || aid <= 0L || cid <= 0L) continue
                        add(
                            HistoryItem(
                                aid = aid,
                                cid = cid,
                                epId = history.optLong("epid"),
                                business = business,
                                title = item.optString("title", "Untitled video"),
                                subtitle = item.optString("long_title"),
                                coverUrl = item.optString("cover").replace("http://", "https://"),
                                author = item.optString("author_name"),
                                durationSeconds = item.optLong("duration"),
                                progressSeconds = item.optLong("progress"),
                                viewedAt = item.optLong("view_at")
                            )
                        )
                    }
                }
            }
            val cursor = data.optJSONObject("cursor")
            if (cursor != null) {
                historyCursorMax = cursor.optLong("max")
                historyCursorViewAt = cursor.optLong("view_at")
                historyCursorBusiness = cursor.optString("business")
            }
            historyPage += 1
            HistoryPage(
                items = items,
                page = historyPage,
                returnedCount = list?.length() ?: 0,
                hasMore = list != null && list.length() > 0 && historyCursorViewAt > 0L
            )
        }
    }

    fun fetchHistoryVideoUrl(
        item: HistoryItem,
        callback: (Result<String>) -> Unit
    ): Call {
        if (item.business == "pgc") {
            val url = PGC_PLAY_URL.toHttpUrl().newBuilder()
                .addQueryParameter("avid", item.aid.toString())
                .addQueryParameter("cid", item.cid.toString())
                .addQueryParameter("ep_id", item.epId.toString())
                .addQueryParameter("qn", "64")
                .addQueryParameter("fnval", "0")
                .addQueryParameter("platform", "html5")
                .build()
            val request = authenticatedRequest(url.toString())
                .header("Referer", "https://www.bilibili.com/bangumi/play/ep${item.epId}")
                .build()
            return enqueueJson(request, callback) { root ->
                requireApiSuccess(root)
                progressiveUrl(root.optJSONObject("result") ?: root.getJSONObject("data"))
            }
        }
        return fetchArchiveVideoUrl(item.aid, item.cid, callback)
    }

    fun reportPlaybackProgress(
        tracking: PlaybackTracking,
        playedSeconds: Long,
        realtimeSeconds: Long,
        startedAt: Long,
        callback: (Result<Unit>) -> Unit = {}
    ): Call? {
        val csrf = cookieValue("bili_jct") ?: return null
        val form = FormBody.Builder()
            .add("aid", tracking.aid.toString())
            .add("cid", tracking.cid.toString())
            .add("played_time", playedSeconds.coerceAtLeast(0L).toString())
            .add("realtime", realtimeSeconds.coerceAtLeast(0L).toString())
            .add("start_ts", startedAt.toString())
            .add("type", if (tracking.business == "pgc") "4" else "3")
            .add("dt", "2")
            .add("play_type", "0")
            .add("csrf", csrf)
            .add("csrf_token", csrf)
            .apply {
                if (tracking.epId > 0L) add("epid", tracking.epId.toString())
            }
            .build()
        val request = authenticatedRequest(HEARTBEAT_URL)
            .post(form)
            .build()
        return enqueueJson(request, callback) { root ->
            requireApiSuccess(root)
        }
    }

    private fun fetchArchiveVideoUrl(
        aid: Long,
        cid: Long,
        callback: (Result<String>) -> Unit
    ): Call {
        val request = authenticatedRequest(
            "https://api.bilibili.com/x/player/playurl" +
                "?avid=$aid&cid=$cid&qn=64&fnval=0" +
                "&platform=html5&high_quality=1"
        )
            .header("Referer", "https://www.bilibili.com/video/av$aid")
            .build()
        return enqueueJson(request, callback) { root ->
            requireApiSuccess(root)
            progressiveUrl(root.getJSONObject("data"))
        }
    }

    private fun progressiveUrl(data: JSONObject): String {
        val durl = data.optJSONArray("durl")
            ?: error("Progressive playback is unavailable for this video")
        return durl.optJSONObject(0)?.optString("url")
            ?.takeIf(String::isNotBlank)
            ?: error("Playback URL was missing")
    }

    private fun selectLiveStreamUrl(streams: JSONArray?): String? {
        if (streams == null) return null
        val candidates = mutableListOf<LiveStreamCandidate>()
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
                    if (!fullUrl.startsWith("http")) continue
                    val rank = when {
                        protocol.contains("hls") && formatName.contains("ts") -> 0
                        protocol.contains("hls") -> 1
                        formatName.contains("flv") -> 2
                        else -> 3
                    }
                    candidates += LiveStreamCandidate(rank, fullUrl)
                }
            }
        }
        return candidates.minByOrNull(LiveStreamCandidate::rank)?.url
    }

    private fun authenticatedRequest(url: String): Request.Builder {
        return Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://www.bilibili.com/")
            .apply {
                cookieHeader().takeIf(String::isNotBlank)?.let { header("Cookie", it) }
            }
    }

    private fun stableIdentifier(
        key: String,
        prefix: String = "",
        suffix: String = "",
        length: Int
    ): String {
        preferences.getString(key, null)?.takeIf(String::isNotBlank)?.let { return it }
        val randomPart = buildString {
            while (this.length < length) append(UUID.randomUUID().toString().replace("-", ""))
        }.take(length)
        return "$prefix$randomPart$suffix".also {
            preferences.edit().putString(key, it).apply()
        }
    }

    private fun saveSession(response: Response, redirectUrl: String) {
        val cookies = linkedMapOf<String, String>()
        response.headers.values("Set-Cookie").forEach { header ->
            addCookiePair(cookies, header.substringBefore(';'))
        }
        val rawQuery = redirectUrl.substringAfter('?', "").substringBefore('#')
        rawQuery.split('&').forEach { pair ->
            val name = pair.substringBefore('=', "")
            if (name in LOGIN_COOKIE_NAMES) addCookiePair(cookies, pair)
        }
        if (cookies.isEmpty()) error("Bilibili did not return a login session")
        preferences.edit().putString(COOKIES_KEY, JSONObject(cookies as Map<*, *>).toString()).commit()
    }

    private fun addCookiePair(cookies: MutableMap<String, String>, pair: String) {
        val separator = pair.indexOf('=')
        if (separator <= 0) return
        val name = pair.substring(0, separator).trim()
        val value = pair.substring(separator + 1).trim()
        if (name.isNotEmpty() && value.isNotEmpty()) cookies[name] = value
    }

    private fun cookieHeader(): String {
        val raw = preferences.getString(COOKIES_KEY, null) ?: return ""
        return runCatching {
            val json = JSONObject(raw)
            json.keys().asSequence().joinToString("; ") { name -> "$name=${json.getString(name)}" }
        }.getOrDefault("")
    }

    private fun cookieValue(name: String): String? {
        val raw = preferences.getString(COOKIES_KEY, null) ?: return null
        return runCatching { JSONObject(raw).optString(name).takeIf(String::isNotBlank) }
            .getOrNull()
    }

    private fun clearSession() {
        preferences.edit().remove(COOKIES_KEY).apply()
    }

    private fun <T> enqueueJson(
        request: Request,
        callback: (Result<T>) -> Unit,
        transform: (JSONObject) -> T
    ): Call {
        val call = httpClient.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (!call.isCanceled()) callback(Result.failure(error))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    callback(runCatching {
                        if (!it.isSuccessful) error("HTTP ${it.code}")
                        transform(JSONObject(it.body?.string().orEmpty()))
                    })
                }
            }
        })
        return call
    }

    private fun requireApiSuccess(root: JSONObject) {
        if (root.optInt("code", -1) != 0) {
            error(root.optString("message", "Bilibili API error"))
        }
    }

    data class QrChallenge(val url: String, val key: String)
    data class QrPollResult(val state: QrState, val message: String)
    data class Account(val mid: Long, val name: String)
    data class Recommendation(
        val aid: Long,
        val cid: Long,
        val title: String,
        val coverUrl: String,
        val uploader: String,
        val viewCount: String,
        val duration: String
    )
    data class RecommendationPage(
        val videos: List<Recommendation>,
        val page: Int,
        val signedIn: Boolean
    )
    data class LiveRoom(
        val roomId: Long,
        val title: String,
        val coverUrl: String,
        val anchor: String,
        val popularity: Long,
        val parentArea: String,
        val area: String
    )
    data class LiveRoomPage(
        val rooms: List<LiveRoom>,
        val page: Int,
        val hasMore: Boolean
    )
    data class HistoryItem(
        val aid: Long,
        val cid: Long,
        val epId: Long,
        val business: String,
        val title: String,
        val subtitle: String,
        val coverUrl: String,
        val author: String,
        val durationSeconds: Long,
        val progressSeconds: Long,
        val viewedAt: Long
    )
    data class HistoryPage(
        val items: List<HistoryItem>,
        val page: Int,
        val returnedCount: Int,
        val hasMore: Boolean
    )
    data class PlaybackTracking(
        val aid: Long,
        val cid: Long,
        val epId: Long = 0L,
        val business: String = "archive"
    )

    enum class QrState {
        WAITING_FOR_SCAN,
        WAITING_FOR_CONFIRMATION,
        EXPIRED,
        AUTHENTICATED
    }

    private data class LiveStreamCandidate(val rank: Int, val url: String)

    companion object {
        private const val PREFERENCES = "bilibili_session"
        private const val COOKIES_KEY = "cookies"
        private const val BUVID_KEY = "feed_buvid"
        private const val FEED_SESSION_KEY = "feed_session_id"
        private const val FEED_URL = "https://app.bilibili.com/x/v2/feed/index"
        private const val LIVE_ROOMS_URL =
            "https://api.live.bilibili.com/room/v3/area/getRoomList"
        private const val LIVE_PLAY_URL =
            "https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo"
        private const val HISTORY_URL = "https://api.bilibili.com/x/web-interface/history/cursor"
        private const val PGC_PLAY_URL = "https://api.bilibili.com/pgc/player/web/playurl"
        private const val HEARTBEAT_URL = "https://api.bilibili.com/x/click-interface/web/heartbeat"
        private const val ANDROID_APP_KEY = "1d8b6e7d45233436"
        private const val ANDROID_BUILD = "8290300"
        private const val ANDROID_APP_VERSION = "8.29.0"
        private const val LIVE_PAGE_SIZE = 20
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 5.1; BileebileeTV/0.3) " +
                "AppleWebKit/537.36 Mobile Safari/537.36"
        private val PLAYABLE_FEED_TYPES = setOf("av", "vertical_av")
        private val PLAYABLE_HISTORY_TYPES = setOf("archive", "pgc")
        private val LOGIN_COOKIE_NAMES = setOf(
            "DedeUserID",
            "DedeUserID__ckMd5",
            "SESSDATA",
            "bili_jct",
            "sid"
        )
    }
}
