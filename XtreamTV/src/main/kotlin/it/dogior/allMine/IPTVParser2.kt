package it.dogior.allMine

import java.io.InputStream
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeToSequence
import okhttp3.OkHttpClient
import okhttp3.Request


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
@InternalSerializationApi @Serializable
data class Category(
    @SerialName("category_name") val categoryName: String,
    @SerialName("category_id") val categoryId: String,
    @SerialName("parent_id") val parentId: Int,
    @SerialName("category_channels") val channels: List<Channel>,

    )
@InternalSerializationApi @Serializable
data class Channel(
    @SerialName("num") val num: Int,
    @SerialName("name") val name: String,
    @SerialName("stream_type") val streamType: String,
    @SerialName("stream_id") val streamId: Int,
    @SerialName("stream_icon") val streamIcon: String,
    @SerialName("epg_channel_id") val epgChannelId: String,
    @SerialName("added") val added: String,
    @SerialName("category_id") val categoryId: String,
    @SerialName("custom_sid") val customSid: String,
    @SerialName("tv_archive") val tvArchive: Int,
    @SerialName("direct_source") val directSource: String,
    @SerialName("tv_archive_duration") val tvArchiveuration: Int,
    @SerialName("stream_url") val streamUrl: String,
    @SerialName("epg") val epg: List<EPG> = emptyList(),

    )
@InternalSerializationApi @Serializable
data class EPG(
    @SerialName("title") val title: String,
    @SerialName("desc") val desc: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("stop_time") val stopTime: String,

    )

@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
fun <T> fetchWithOkHttp(
    client: OkHttpClient,
    url: String,
    action: (List<Category>) -> T
): T? {
    val request = Request.Builder().url(url).build()

    return client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return null

        val inputStream = response.body?.byteStream() ?: return null

        val itemSequence = Json.decodeToSequence<Category>(
            stream = inputStream.buffered(),
            format = DecodeSequenceMode.ARRAY_WRAPPED
        )

        // The result of action(itemSequence) is evaluated here
        // and returned out of the .use {} block
        action(itemSequence.toList())
    }
}

@OptIn(InternalSerializationApi::class)
var cachedCategories: List<Category>? = null

@OptIn(InternalSerializationApi::class)
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