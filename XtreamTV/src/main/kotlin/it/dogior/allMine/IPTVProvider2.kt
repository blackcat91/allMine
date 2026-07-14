package it.dogior.allMine

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.DecodeSequenceMode
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


// A permissive TrustManager that completely bypasses custom SSL validation issues
val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
})

val sslContext = SSLContext.getInstance("SSL").apply {
    init(null, trustAllCerts, SecureRandom())
}

val clientOk = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .followRedirects(false) // Stop it from automatically resolving
    .followSslRedirects(false)
    // CRITICAL: Attach the relaxed SSL socket configurations here
    .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
    .hostnameVerifier { _, _ -> true } // Accepts the domain verification handshake
    .build()



class MyLiveTVProvider : MainAPI() {
    override var mainUrl = "https://github.com/blackcat91/allMine/tree/builds"
    override var name = "MyLiveTV"
    override val hasQuickSearch = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Live, TvType.TvSeries)

    private val jsonCatalogUrl = "https://kwqbwdmmwwpufkownclf.supabase.co/storage/v1/object/public/Main/myCategories.json"
    override var lang = "en"
    override val hasMainPage = true
    override val mainPage = mainPageOf("channels" to "Live IPTV Channels")

    // Helper method to ensure we only load and parse the massive stream ONCE safely into local memory
    @OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
    private fun ensureCategoriesCached(): List<Category>? {
        if (cachedCategories != null) return cachedCategories

        return fetchWithOkHttp(clientOk, jsonCatalogUrl) { sequence ->
            // CRITICAL: Convert sequence to a standard List to save it in memory safely
            val materializedList = sequence.toList()
            cachedCategories = materializedList
            materializedList
        }
    }

    @OptIn(InternalSerializationApi::class)
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        // 1. Fetch categories from the centralized helper method
        val categories = ensureCategoriesCached() ?: return null

        // 2. Map channels seamlessly into horizontal dashboard shelves
        return newHomePageResponse(categories.map { group ->
            val searchResponses = group.channels.map { channel ->
                newLiveSearchResponse(channel.name, channel.streamUrl, TvType.Live) {
                    this.posterUrl = channel.streamIcon
                }
            }
            HomePageList(group.categoryName, searchResponses)
        }, hasNext = false)
    }

    @OptIn(InternalSerializationApi::class)
    override suspend fun search(query: String): List<LiveSearchResponse>? {
        val categories = ensureCategoriesCached() ?: return null

        return categories.flatMap { it.channels }
            .filter { it.name.contains(query, ignoreCase = true) }
            .map { stream ->
                newLiveSearchResponse(stream.name, stream.streamUrl, TvType.Live) {
                    this.posterUrl = stream.streamIcon
                }
            }
    }

    @OptIn(InternalSerializationApi::class)
    override suspend fun load(url: String): LoadResponse {
        var channelName = "Live Feed"

        // 1. Read your categories safely from the localized memory cache block
        val categories = ensureCategoriesCached()

        val currentEpgText = try {
            val flatChannels = categories?.flatMap { it.channels }
            val matchingChannel = flatChannels?.find { it.streamUrl == url }
            val epgMatch = matchingChannel?.epg
            channelName = matchingChannel?.name ?: "Live Feed"

            if (!epgMatch.isNullOrEmpty()) {
                val nowMs = System.currentTimeMillis()
                val channelSchedule = epgMatch

                val liveNow = channelSchedule.find { program ->
                    val startMs = parseXmltvTimeToEpoch(program.startTime)
                    val stopMs = parseXmltvTimeToEpoch(program.stopTime)
                    nowMs in startMs..<stopMs
                }

                val upNext = liveNow?.let { currentShow ->
                    val currentShowStopMs = parseXmltvTimeToEpoch(currentShow.stopTime)
                    channelSchedule
                        .filter { program -> parseXmltvTimeToEpoch(program.startTime) >= currentShowStopMs }
                        .minByOrNull { parseXmltvTimeToEpoch(it.startTime) }
                }

                buildString {
                    if (liveNow != null) {
                        appendLine("🔴 LIVE NOW: ${liveNow.title}")
                        if (!liveNow.desc.isNullOrBlank()) {
                            appendLine(liveNow.desc)
                        }
                    } else {
                        appendLine("🔴 LIVE NOW: Off-Air / No Schedule")
                    }
                    appendLine()
                    if (upNext != null) {
                        appendLine("⏳ COMING UP NEXT: ${upNext.title}")
                    } else {
                        appendLine("⏳ COMING UP NEXT: Schedule Ends")
                    }
                }
            } else {
                "Live stream feed description unavailable."
            }
        } catch (e: Exception) {
            "Error rendering live EPG data window."
        }

        return newLiveStreamLoadResponse(
            name = channelName,
            url = url,
            dataUrl = url
        ) {
            this.plot = currentEpgText
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (com.lagradost.cloudstream3.SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val resolvedTokenUrl = try {
            val redirectCheckClient = clientOk.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
            val request = Request.Builder().url(data).build()
            redirectCheckClient.newCall(request).execute().use { response ->
                response.header("Location") ?: data
            }
        } catch (e: Exception) {
            data
        }

        val formattedUrlForVlc = if (!resolvedTokenUrl.contains(".m3u8", ignoreCase = true)) {
            "$resolvedTokenUrl#.m3u8"
        } else {
            resolvedTokenUrl
        }

        val streamLink = newExtractorLink(
            source = this.name,
            name = "Live TV (HLS)",

            url = resolvedTokenUrl,
            type = ExtractorLinkType.M3U8
        ) {
            this.referer = "https://ck24.ws"
            this.quality = Qualities.Unknown.value
            this.headers = mapOf(
                "Accept" to "*/*",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Connection" to "keep-alive",
                "X-Disconnect-Retry-Count" to "99",
                "X-Playback-Session-Id" to System.currentTimeMillis().toString(),
                "Accept-Encoding" to "gzip, deflate, br",
                "Cache-Control" to "no-cache",
                "Pragma" to "no-cache",
            )
        }

        callback(streamLink)
        return true
    }
}