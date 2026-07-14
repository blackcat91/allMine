package it.dogior.allMine

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.buildDefaultClient
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

val clientOk = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()


class MyLiveTVProvider : MainAPI() { // All providers must be an instance of MainAPI
    override var mainUrl = "https://github.com/blackcat91/allMine/tree/builds"
    override var name = "MyLiveTV"
    override val hasQuickSearch = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Live, TvType.TvSeries)
    private val jsonCatalogUrl = "https://kwqbwdmmwwpufkownclf.supabase.co/storage/v1/object/public/Main/myCategories.json"
    override var lang = "en"
    // Enable this when your provider has a main page
    override val hasMainPage = false
    override val mainPage = mainPageOf("" to "Live IPTV Channels")

    // Memory cache for the parsed categories



    @OptIn(InternalSerializationApi::class)
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {



        // Map every JSON category entry to a dedicated horizontal shelf row
          val pages : HomePageResponse? = fetchWithOkHttp(clientOk, jsonCatalogUrl)  { categories ->
                 cachedCategories = categories
                 newHomePageResponse(categories.map { group ->
                    val searchResponses = group.channels.map { channel ->
                        // Use TvType.Live here
                        newLiveSearchResponse(channel.name, channel.streamUrl, TvType.Live) {
                            this.posterUrl = channel.streamIcon
                        }
                    }
                    HomePageList(group.categoryName, searchResponses)
                }, hasNext = false)

        }

    return pages
    }

    // This function gets called when you search for something
    // The search function takes a 'query' string (whatever the user typed in the search bar)
    @OptIn(InternalSerializationApi::class)
    override suspend fun search(query: String): List<LiveSearchResponse>? {

        if (cachedCategories != null) {

            return cachedCategories?.flatMap { it.channels }
                ?.filter { it.name.contains(query, ignoreCase = true) }
                ?.map { stream ->
                    newLiveSearchResponse(stream.name, stream.streamUrl, TvType.Live) {
                        this.posterUrl = stream.streamIcon
                    }
                }

        } else {

            return fetchWithOkHttp(clientOk, jsonCatalogUrl) { categories ->
                cachedCategories = categories
                categories?.flatMap { it.channels }
                    ?.filter { it.name.contains(query, ignoreCase = true) }
                    ?.map { stream ->
                        newLiveSearchResponse(stream.name, stream.streamUrl, TvType.Live) {
                            this.posterUrl = stream.streamIcon
                        }
                    }
            }

        }
    }


    @OptIn(InternalSerializationApi::class)
    override suspend fun load(url: String): LoadResponse {



        // Fetch current EPG track data matching this stream if available
        val currentEpgText = try {

          fetchWithOkHttp(clientOk, jsonCatalogUrl) { it ->


              // Find the active channel inside our catalog to grab its epgId
              val flatChannels = it.flatMap { it.channels }
              val matchingChannel = flatChannels.find { it.streamUrl == url }
              val epgMatch = matchingChannel?.epg

              if (epgMatch != null) {
                  // Get current system time in absolute milliseconds
                  val nowMs = System.currentTimeMillis()

                  // Filter down to the schedule for this channel
                  val channelSchedule = epgMatch

                  // 1. Find the program running RIGHT NOW
                  val liveNow = channelSchedule.find { program ->
                      val startMs = parseXmltvTimeToEpoch(program.startTime)
                      val stopMs = parseXmltvTimeToEpoch(program.stopTime)
                      nowMs in startMs..<stopMs
                  }

                  // 2. Find the program starting NEXT
                  val upNext = liveNow?.let { currentShow ->
                      val currentShowStopMs = parseXmltvTimeToEpoch(currentShow.stopTime)

                      channelSchedule
                          .filter { program ->
                              parseXmltvTimeToEpoch(program.startTime) >= currentShowStopMs
                          }
                          .minByOrNull { parseXmltvTimeToEpoch(it.startTime) }
                  }

                  // 3. Construct your UI string layout
                  buildString {
                      if (liveNow != null) {
                          appendLine("🔴 LIVE NOW: ${liveNow.title}")
                          if (!liveNow.desc.isNullOrBlank()) {
                              appendLine(liveNow.desc)
                          }
                      } else {
                          appendLine("🔴 LIVE NOW: Off-Air / No Schedule")
                      }

                      appendLine() // Visual separator space

                      if (upNext != null) {
                          appendLine("⏳ COMING UP NEXT: ${upNext.title}")
                      } else {
                          appendLine("⏳ COMING UP NEXT: Schedule Ends")
                      }
                  }
              } else {
                  "Live stream feed description unavailable."
              }
          }
        } catch (e: Exception) {
            "Error rendering live EPG data window."
        }


        println("This IS THE URL!!!!  $url")

        return newLiveStreamLoadResponse(
            name = "Live Feed",
            url = url,
            dataUrl = url
        ) {
            this.plot = currentEpgText

        }
    }

    // 2. STEP TWO: Resolve the token link and pop it into the VLC player menu
    override suspend fun loadLinks(
        data: String, // This is the 'url' string passed from dataUrl above
        isCasting: Boolean,
        subtitleCallback: (com.lagradost.cloudstream3.SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // A. Resolve the location redirect header to grab your tokenized live link
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
        println("This IS THE RESOLVED URL!!!!  $resolvedTokenUrl")
        // B. Apply the fragment extension suffix trick so VLC forces its HLS adaptive engine
        val formattedUrlForVlc = if (!resolvedTokenUrl.contains(".m3u8", ignoreCase = true)) {
            "$resolvedTokenUrl#.m3u8"
        } else {
            resolvedTokenUrl
        }

        // C. Construct the modern extractor link tracking packet
        val streamLink = newExtractorLink(
            source = this.name,
            name = "Live TV (HLS)",
            url = formattedUrlForVlc,
            type = ExtractorLinkType.M3U8 // Forces VLC to skip raw file extension validation
        ) {
            this.quality = Qualities.P1080.value
            // INJECT PERSISTENT RECONNECTION PROPERTIES HERE:
            this.headers = mapOf(
                "Accept" to "*/*",
                "User-Agent" to "VLC/3.0.0 LibVLC/3.0.0",

                // Tells ExoPlayer/VLC to repeatedly attempt loading broken transport segments
                "X-Disconnect-Retry-Count" to "99",
                "X-Playback-Session-Id" to System.currentTimeMillis().toString(),

                // Modifies low-level Socket connection parameters to keep the pipe alive
                "keep-alive" to "timeout=60, max=100"
            )
        }

        // D. Invoke the callback. This fills that empty popup option menu instantly!
        callback(streamLink)

        return true // Signals the framework that links were successfully resolved
    }
}