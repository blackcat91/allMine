package it.dogior.allMine

import java.io.InputStream
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.datetime.*
import kotlinx.datetime.format.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Duration
import java.text.SimpleDateFormat
import java.util.*
import kotlin.time.Instant

fun parseXmltvTimeToEpoch(timeStr: String): Long {
    return try {
        // XMLTV standard format: 20260709060000 -0400
        val formatter = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
        val date = formatter.parse(timeStr)
        date?.time ?: 0L
    } catch (e: Exception) {
        println("Bad Error: ${e.message}")
        0L
    }
}


data class Link(
    val name: String,
    val url: String,
    val username: String,
    val password: String
)
data class Category(
    @JsonProperty("category_name") val categoryName: String,
    @JsonProperty("category_id") val categoryId: String,
    @JsonProperty("parent_id") val parentId: Int,
    @JsonProperty("category_channels") val channels: List<Channel>,

    )
data class Channel(
    @JsonProperty("num") val num: Int,
    @JsonProperty("name") val name: String,
    @JsonProperty("stream_type") val streamType: String,
    @JsonProperty("stream_id") val streamId: Int,
    @JsonProperty("stream_icon") val streamIcon: String,
    @JsonProperty("epg_channel_id") val epgChannelId: String,
    @JsonProperty("added") val added: String,
    @JsonProperty("category_id") val categoryId: String,
    @JsonProperty("custom_sid") val customSid: String,
    @JsonProperty("tv_archive") val tvArchive: Int,
    @JsonProperty("direct_source") val directSource: String,
    @JsonProperty("tv_archive_duration") val tvArchiveuration: Int,
    @JsonProperty("stream_url") val streamUrl: String,
    @JsonProperty("epg") val epg: List<EPG>,

    )

data class EPG(
    @JsonProperty("title") val title: String,
    @JsonProperty("desc") val desc: String,
    @JsonProperty("start_time") val startTime: String,
    @JsonProperty("stop_time") val stopTime: String,

    )

var cachedCategories: List<Category>? = null

suspend fun getCategories(jsonCatalogUrl : String): List<Category>? {
    // If we already downloaded it, return the cache
    cachedCategories?.let { return it }

    try {
        // Otherwise, fetch and cache it
        val jsonRaw = app.get(jsonCatalogUrl).text
        val parsed = parseJson<List<Category>>(jsonRaw)
        cachedCategories = parsed
        return parsed
    }
    catch (e: Exception){
        println("Bad Error: ${e.message}" )
    }
    return cachedCategories
}