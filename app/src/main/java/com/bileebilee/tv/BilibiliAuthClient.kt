package com.bileebilee.tv

import android.content.Context
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class BilibiliAuthClient(
    context: Context,
    private val httpClient: OkHttpClient
) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

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

    fun fetchRecommendations(callback: (Result<List<Recommendation>>) -> Unit): Call {
        val request = authenticatedRequest(
            "https://app.bilibili.com/x/v2/feed/index" +
                "?build=8290300&mobi_app=android&platform=android&device=phone" +
                "&pull=true&idx=0&column=4"
        ).build()
        return enqueueJson(request, callback) { root ->
            requireApiSuccess(root)
            val items = root.getJSONObject("data").getJSONArray("items")
            buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    if (item.optString("goto") != "av" || item.optInt("can_play", 1) == 0) continue
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
        }
    }

    fun fetchVideoUrl(
        video: Recommendation,
        callback: (Result<String>) -> Unit
    ): Call {
        val request = authenticatedRequest(
            "https://api.bilibili.com/x/player/playurl" +
                "?avid=${video.aid}&cid=${video.cid}&qn=64&fnval=0" +
                "&platform=html5&high_quality=1"
        )
            .header("Referer", "https://www.bilibili.com/video/av${video.aid}")
            .build()
        return enqueueJson(request, callback) { root ->
            requireApiSuccess(root)
            val data = root.getJSONObject("data")
            val durl = data.optJSONArray("durl")
                ?: error("Progressive playback is unavailable for this video")
            durl.optJSONObject(0)?.optString("url")
                ?.takeIf(String::isNotBlank)
                ?: error("Playback URL was missing")
        }
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

    enum class QrState {
        WAITING_FOR_SCAN,
        WAITING_FOR_CONFIRMATION,
        EXPIRED,
        AUTHENTICATED
    }

    companion object {
        private const val PREFERENCES = "bilibili_session"
        private const val COOKIES_KEY = "cookies"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 5.1; BileebileeTV/0.2) AppleWebKit/537.36 Mobile Safari/537.36"
        private val LOGIN_COOKIE_NAMES = setOf(
            "DedeUserID",
            "DedeUserID__ckMd5",
            "SESSDATA",
            "bili_jct",
            "sid"
        )
    }
}
