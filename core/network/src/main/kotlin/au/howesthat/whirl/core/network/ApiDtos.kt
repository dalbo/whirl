package au.howesthat.whirl.core.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive

/** The envelope every API response carries, plus whichever `get=` keys were requested. */
@Serializable
internal data class ApiEnvelope(
    @SerialName("ALERT") val alert: String = "",
    @SerialName("AUTH") val auth: AuthDto? = null,
    @SerialName("ERROR") val error: String = "",
    @SerialName("USER") val user: AuthDto? = null,
    @SerialName("FORUM") val forums: List<ForumDto>? = null,
    @SerialName("NEWS") val news: List<NewsDto>? = null,
    @SerialName("THREADS") val threads: List<ThreadDto>? = null,
    // Only ever seen empty; assumed to have the THREADS shape (HANDOFF "known unknowns").
    @SerialName("RECENT") val recent: List<ThreadDto>? = null,
    @SerialName("WATCHED") val watched: List<WatchedDto>? = null,
)

@Serializable
internal data class AuthDto(@SerialName("ID") val id: Long = 0, @SerialName("NAME") val name: String = "")

@Serializable
internal data class ForumDto(
    @SerialName("ID") val id: Int,
    @SerialName("TITLE") val title: String,
    @SerialName("SECTION") val section: String = "",
)

@Serializable
internal data class NewsDto(
    @SerialName("ID") val id: Long,
    @SerialName("TITLE") val title: String,
    @SerialName("SOURCE") val source: String = "",
    @SerialName("BLURB") val blurb: String = "",
    @SerialName("DATE") val date: String,
)

@Serializable
internal data class ThreadDto(
    @Serializable(with = StringOrNumber::class) @SerialName("ID") val id: String,
    @SerialName("TITLE") val title: String,
    @SerialName("REPLIES") val replies: Int = 0,
    @SerialName("FORUM_ID") val forumId: Int = 0,
    @SerialName("FORUM_NAME") val forumName: String = "",
    @SerialName("GROUP") val group: String = "",
    @SerialName("FIRST_DATE") val firstDate: String,
    @SerialName("FIRST") val first: AuthDto = AuthDto(),
    @SerialName("LAST_DATE") val lastDate: String,
    @SerialName("LAST") val last: AuthDto = AuthDto(),
    @SerialName("SUBSCRIBED") val subscribed: Boolean = false,
    // A boolean here; WATCHED.UNREAD is a count.
    @SerialName("UNREAD") val unread: Boolean = false,
    @SerialName("PARTICIPATED") val participated: Boolean = false,
)

@Serializable
internal data class WatchedDto(
    @Serializable(with = StringOrNumber::class) @SerialName("ID") val id: String,
    @SerialName("TITLE") val title: String,
    @SerialName("REPLIES") val replies: Int = 0,
    @SerialName("FORUM_ID") val forumId: Int = 0,
    @SerialName("FORUM_NAME") val forumName: String = "",
    @SerialName("FIRST_DATE") val firstDate: String,
    @SerialName("FIRST") val first: AuthDto = AuthDto(),
    @SerialName("LAST_DATE") val lastDate: String,
    @SerialName("LAST") val last: AuthDto = AuthDto(),
    @SerialName("UNREAD") val unread: Int = 0,
    @SerialName("LASTREAD") val lastRead: Int = 0,
    @SerialName("LASTPAGE") val lastPage: Int = 1,
)

/** Thread ids are strings; accept a bare number too so a legacy numeric id can never be mis-typed. */
internal object StringOrNumber : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("StringOrNumber", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String =
        (decoder as? JsonDecoder)?.decodeJsonElement()?.jsonPrimitive?.content ?: decoder.decodeString()

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}
