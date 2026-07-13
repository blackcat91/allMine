package it.dogior.allMine

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import okhttp3.Interceptor

class MyLiveTVProvider : MainAPI() { // All providers must be an instance of MainAPI
    override var mainUrl = "https://kwqbwdmmwwpufkownclf.supabase.co/"
    override var name = "IPTV Provider"
    override val hasQuickSearch = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Live)
    private val jsonCatalogUrl = getPublicUrl("myCategories.json")
    private val jsonEpgUrl = getPublicUrl("epg.xml")

    override var lang = "en"

    // Enable this when your provider has a main page
    override val hasMainPage = true

    // Memory cache for the parsed categories



    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {

        val categories = getCategories(jsonCatalogUrl)

        // Map every JSON category entry to a dedicated horizontal shelf row

        if (categories != null) {
            return newHomePageResponse(categories.map { group ->
                val searchResponses = group.channels.map { channel ->
                    // Use TvType.Live here
                    newLiveSearchResponse(channel.name, channel.streamUrl, TvType.Live) {
                        this.posterUrl = channel.streamIcon
                    }
                }
                HomePageList(group.category_name, searchResponses)
            }, hasNext = false)
        }
        return TODO("Something Went Wrong!")
    }

    // This function gets called when you search for something
    // The search function takes a 'query' string (whatever the user typed in the search bar)
    override suspend fun search(query: String): List<LiveSearchResponse>? {
        val categories = getCategories(jsonCatalogUrl) // Uses cache if available

        return categories
            ?.flatMap { it.channels }
            ?.filter { it.name.contains(query, ignoreCase = true) }
            ?.map { stream ->
                newLiveSearchResponse(stream.name, stream.streamUrl, TvType.Live) {
                    this.posterUrl = stream.streamIcon
                }
            }
    }

    override suspend fun load(url: String): LoadResponse? {
        // Fetch current EPG track data matching this stream if available
        val currentEpgText = try {
            val epgRaw = app.get(jsonEpgUrl).text
            val epgList = parseJson<List<EPG>>(epgRaw)

            // Find the active channel inside our catalog to grab its epgId
            val flatChannels = getCategories(jsonCatalogUrl)?.flatMap { it.channels }
            val matchingChannel = flatChannels?.find { it.streamUrl == url }
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
                    nowMs >= startMs && nowMs < stopMs
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
        } catch (e: Exception) {
            "Error rendering live EPG data window."
        }

        return newLiveStreamLoadResponse(
            name = "Live Feed",
            url = url,
            dataUrl = url
        ) {
            this.plot = currentEpgText
        }
    }
}