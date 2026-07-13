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
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlin.time.Duration
import java.text.SimpleDateFormat
import java.util.*

fun parseXmltvTimeToEpoch(timeStr: String): Long {
    return try {
        // XMLTV standard format: 20260709060000 -0400
        val formatter = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
        val date = formatter.parse(timeStr)
        date?.time ?: 0L
    } catch (e: Exception) {
        0L
    }
}

val supabaseKey = BuildConfig.SUPABASE_API
val supabase = createSupabaseClient(
    supabaseUrl = "https://kwqbwdmmwwpufkownclf.supabase.co",
    supabaseKey = supabaseKey
) {
    install(Storage)
}

fun getPublicUrl(filename: String): String {
    return supabase.storage.from("Main").publicUrl(path = filename)
}

suspend fun getSignedUrl(filename : String): String {
    return supabase.storage.from("Main").createSignedUrl(
        path = filename,
        expiresIn = Duration.parse("20m") // Expires in 60 seconds
    )
}

suspend fun downloadFileToMemory(filename : String): ByteArray {
    // Fetches the file data from your bucket path into a ByteArray
    val fileBytes: ByteArray = supabase.storage
        .from("Main")
        .downloadAuthenticated(path = filename)

    return fileBytes
}

object UserLocaleDateTimeSerializer : KSerializer<LocalDateTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("UserLocaleDateTime", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): LocalDateTime {
        // 1. Fetch the raw JSON string
        val rawString = decoder.decodeString()

        // 2. Execute your exact parsing logic
        val instant = customFormat.parse(rawString).toInstantUsingOffset()
        val phoneZone = TimeZone.currentSystemDefault()

        return instant.toLocalDateTime(phoneZone)
    }

    override fun serialize(encoder: Encoder, value: LocalDateTime) {
        // Convert the local time to an Instant using the current system offset to save it back
        val currentOffset = TimeZone.currentSystemDefault().offsetAt(value.toInstant(TimeZone.UTC))
        val components = DateTimeComponents.Format { }.parse("") // Creates an empty DateTimeComponents instance
            .apply {
                setDateTime(value)
                setOffset(currentOffset)
            }
        encoder.encodeString(customFormat.format(components))
    }
}

// Define the reusable custom formatter
val customFormat = DateTimeComponents.Format {
    year(); monthNumber(); dayOfMonth()
    hour(); minute(); second()
    chars(" ")
    offset(UtcOffset.Formats.FOUR_DIGITS)
}

// Helper function to parse your specific string directly into an Instant
fun parseToInstant(input: String): Instant {
    return customFormat.parse(input).toInstantUsingOffset()
}

fun parseToLocalDateTime(input: String): LocalDateTime {
    val instant = customFormat.parse(input).toInstantUsingOffset()
    val phoneZone = TimeZone.currentSystemDefault() // Automatically detects device timezone
    return instant.toLocalDateTime(phoneZone)
}

fun testCompare() {
    val dateStr1 = "20260706030000 -0400"
    val dateStr2 = "20260706090000 +0100" // 1 hour later than dateStr1

    val instant1 = parseToInstant(dateStr1)
    val instant2 = parseToInstant(dateStr2)

    // 1. COMPARE TWO DATES
    // Instants are standardized to UTC, making chronological comparisons simple
    if (instant1 < instant2) {
        println("Date 1 happens before Date 2")
    } else if (instant1 > instant2) {
        println("Date 1 happens after Date 2")
    } else {
        println("Both dates represent the exact same moment")
    }

    // 2. CONVERT TO USER'S LOCAL PHONE TIME
    val phoneZone = TimeZone.currentSystemDefault() // Automatically detects device timezone
    val localDateTime: LocalDateTime = instant1.toLocalDateTime(phoneZone)

    println("User's Local Phone Time: $localDateTime")
}

data class Category(
    @JsonProperty("category_name") val category_name: String,
    @JsonProperty("category_id") val category_id: String,
    @JsonProperty("parent_id") val parent_id: Int,
    @JsonProperty("category_channels") val channels: List<Channel>,

    )
data class Channel(
    @JsonProperty("num") val num: Int,
    @JsonProperty("name") val name: String,
    @JsonProperty("stream_type") val stream_type: String,
    @JsonProperty("stream_id") val stream_id: Int,
    @JsonProperty("stream_icon") val streamIcon: String,
    @JsonProperty("epg_channel_id") val epg_channel_id: String,
    @JsonProperty("added") val added: String,
    @JsonProperty("category_id") val category_id: String,
    @JsonProperty("custom_sid") val custom_sid: String,
    @JsonProperty("tv_archive") val tv_archive: Int,
    @JsonProperty("direct_source") val direct_source: String,
    @JsonProperty("tv_archive_duration") val tv_archive_duration: Int,
    @JsonProperty("stream_url") val streamUrl: String,
    @JsonProperty("epg") val epg: List<EPG>,

    )

data class EPG(
    @JsonProperty("title") val title: String,
    @JsonProperty("desc") val desc: String,
    @JsonProperty("start_time") val startTime: String,
    @JsonProperty("stop_time") val stopTime: String,

    )
