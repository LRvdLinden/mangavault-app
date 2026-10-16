package nl.lrvdlinden.mangavault.model

import kotlinx.serialization.Serializable
import nl.lrvdlinden.mangavault.core.FlexInt
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// Field names mirror the Base44 entity schema exactly (snake_case), so the
// app reads and writes the same records as the web app. Everything is optional.

fun nonEmpty(s: String?): String? = if (s.isNullOrEmpty()) null else s

/** "€12.50" like the web app. */
fun Double.euro(): String = "€" + String.format(Locale.US, "%.2f", this)

@Serializable
data class AppUser(val id: String = "", val email: String? = null, val full_name: String? = null)

@Serializable
data class VolumeCover(val volume: Double = 0.0, val url: String? = null)

@Serializable
data class Manga(
    val id: String = "",
    val title: String = "",
    val publisher: String? = null,
    val is_shonen_jump: Boolean? = null,
    val price: Double? = null,
    val total_volumes: Double? = null,
    val genre: String? = null,
    val description: String? = null,
    val languages: List<String>? = null,
    val cover_seed: String? = null,
    val cover_image_url: String? = null,
    val edition: String? = null,
    val volumes_included: String? = null,
    val omnibus_of: String? = null,
    val mangadex_id: String? = null,
    val volume_covers: List<VolumeCover>? = null,
    val mangaFireUrl: String? = null,
    val mangaFireSlug: String? = null,
) {
    val isOmnibus get() = edition == "omnibus"
    val isShonenJump get() = is_shonen_jump ?: false
    val totalVolumes get() = (total_volumes ?: 0.0).toInt()
    val priceValue get() = price ?: 0.0

    companion object {
        val listFields = listOf("title", "cover_image_url", "cover_seed", "total_volumes", "price", "edition",
            "volumes_included", "is_shonen_jump", "genre", "publisher", "omnibus_of")
    }
}

enum class ReadStatus(val raw: String, val label: String) {
    PLAN_TO_READ("plan_to_read", "Plan to Read"),
    READING("reading", "Reading"),
    COMPLETED("completed", "Completed"),
    ON_HOLD("on_hold", "On Hold"),
    DROPPED("dropped", "Dropped");

    /** Label used on the volume detail page of the web app. */
    val volumeLabel get() = if (this == PLAN_TO_READ) "Want to Read" else label

    companion object {
        fun of(raw: String?): ReadStatus? = entries.firstOrNull { it.raw == raw }
        /** The volume detail page only offers these four. */
        val volumeCases = listOf(PLAN_TO_READ, READING, COMPLETED, ON_HOLD)
    }
}

@Serializable
data class CollectionItem(
    val id: String = "",
    val manga_id: String? = null,
    val title: String? = null,
    val publisher: String? = null,
    val price: Double? = null,
    val total_volumes: Double? = null,
    val is_shonen_jump: Boolean? = null,
    val cover_seed: String? = null,
    val cover_image_url: String? = null,
    val volume_number: Double? = null,
    val volume_cover_url: String? = null,
    val read: Boolean? = null,
    val edition: String? = null,
    val volumes_included: String? = null,
    val omnibus_of: String? = null,
    val status: String? = null,
    val rating: Double? = null,
    val notes: String? = null,
    val favorite: Boolean? = null,
    val current_chapter: Double? = null,
    val last_read_date: String? = null,
    val created_date: String? = null,
    val updated_date: String? = null,
) {
    /** Explicit status, else `read` → completed / plan_to_read (same as the web app). */
    val effectiveStatus: ReadStatus get() = ReadStatus.of(status) ?: if (read == true) ReadStatus.COMPLETED else ReadStatus.PLAN_TO_READ
    val isOmnibus get() = edition == "omnibus"
    val volume: Int? get() = volume_number?.toInt()
    val ratingValue get() = (rating ?: 0.0).toInt()
    val isFavorite get() = favorite ?: false
    val chapter get() = (current_chapter ?: 0.0).toInt()
    val hasNotes get() = !notes.isNullOrBlank()
    val displayCover: String? get() = nonEmpty(volume_cover_url) ?: nonEmpty(cover_image_url)
    val volumeLabel: String get() = if (isOmnibus) (nonEmpty(volumes_included) ?: "Omnibus") else "Vol. ${volume?.toString() ?: ""}"
    val titleText get() = title ?: ""
}

@Serializable
data class WishlistItem(
    val id: String = "",
    val manga_id: String? = null,
    val title: String? = null,
    val publisher: String? = null,
    val price: Double? = null,
    val is_shonen_jump: Boolean? = null,
    val cover_seed: String? = null,
    val cover_image_url: String? = null,
    val volume_number: Double? = null,
    val volume_cover_url: String? = null,
    val edition: String? = null,
    val volumes_included: String? = null,
    val omnibus_of: String? = null,
    val created_date: String? = null,
) {
    val isOmnibus get() = edition == "omnibus"
    val volume: Int? get() = volume_number?.toInt()
    val displayCover: String? get() = nonEmpty(volume_cover_url) ?: nonEmpty(cover_image_url)
    val volumeLabel: String get() = if (isOmnibus) (nonEmpty(volumes_included) ?: "Omnibus") else "Vol. ${volume?.toString() ?: ""}"
}

@Serializable
data class ReadingActivity(
    val id: String = "",
    val type: String? = null,
    val manga_id: String? = null,
    val title: String? = null,
    val volume_number: Double? = null,
    val meta: String? = null,
    val created_date: String? = null,
) {
    /** Local day key (yyyy-MM-dd) of created_date. */
    val day: String get() = ServerDate.localDay(created_date)
}

@Serializable
data class UnlockedAchievement(val id: String = "", val date: String? = null)

@Serializable
data class UserProfile(
    val id: String = "",
    val display_name: String? = null,
    val bio: String? = null,
    val avatar_url: String? = null,
    val banner_url: String? = null,
    val theme_id: String? = null,
    val profile_public: Boolean? = null,
    val collection_public: Boolean? = null,
    val xp_bonus: Double? = null,
    val unlocked_achievements: List<UnlockedAchievement>? = null,
    val featured_achievements: List<String>? = null,
    val daily_date: String? = null,
    val daily_claimed: List<String>? = null,
    val weekly_date: String? = null,
    val weekly_claimed: List<String>? = null,
    val favorite_manga: List<String>? = null,
    val favorite_characters: List<String>? = null,
    val favorite_genres: List<String>? = null,
    val dismissed_recommendations: List<String>? = null,
    val dashboard_layout: List<DashboardSection>? = null,
    val created_by_id: String? = null,
)

@Serializable
data class ReadingSession(
    val id: String = "",
    val manga_id: String? = null,
    val title: String? = null,
    val volume_number: Double? = null,
    val date: String? = null,
    val minutes: Double? = null,
    val chapters_read: Double? = null,
    val notes: String? = null,
    val created_date: String? = null,
) {
    val day: String get() = nonEmpty(date)?.take(10) ?: ServerDate.localDay(created_date)
}

enum class JournalType(val raw: String, val label: String) {
    NOTE("note", "Note"), MOMENT("moment", "Favorite Moment"), REMINDER("reminder", "Reminder"), RATING("rating", "Rating");
    companion object { fun of(raw: String?) = entries.firstOrNull { it.raw == raw } ?: NOTE }
}

@Serializable
data class JournalEntry(
    val id: String = "",
    val manga_id: String? = null,
    val title: String? = null,
    val volume_number: Double? = null,
    val entry_type: String? = null,
    val content: String? = null,
    val rating: Double? = null,
    val is_spoiler: Boolean? = null,
    val created_date: String? = null,
) {
    val type get() = JournalType.of(entry_type)
}

@Serializable
data class TierDef(val name: String = "", val color: String = "#64748b")

@Serializable
data class TierEntry(
    val kind: String = "manga",
    val ref_id: String? = null,
    val label: String = "",
    val cover_url: String? = null,
    @Serializable(with = FlexInt::class) val tier: Int = -1,
    @Serializable(with = FlexInt::class) val order: Int = 0,
) {
    fun toMap(): Map<String, Any?> = mapOf("kind" to kind, "ref_id" to (ref_id ?: ""), "label" to label,
        "cover_url" to (cover_url ?: ""), "tier" to tier, "order" to order)
}

@Serializable
data class TierList(
    val id: String = "",
    val name: String? = null,
    val description: String? = null,
    val list_type: String? = null,
    val tiers: List<TierDef>? = null,
    val entries: List<TierEntry>? = null,
    val is_public: Boolean? = null,
    val created_date: String? = null,
) {
    companion object {
        val defaultTiers = listOf(TierDef("S", "#FF7A00"), TierDef("A", "#f59e0b"), TierDef("B", "#64748b"),
            TierDef("C", "#475569"), TierDef("D", "#334155"))
    }
}

/** Public view of another reader (social* backend functions). `id` = UserProfile id, `user_id` = auth user id. */
@Serializable
data class SocialUser(
    val id: String = "",
    val user_id: String? = null,
    val display_name: String? = null,
    val bio: String? = null,
    val avatar_url: String? = null,
    val banner_url: String? = null,
    val favorite_manga: List<String>? = null,
    val favorite_characters: List<String>? = null,
    val favorite_genres: List<String>? = null,
    val friend: Boolean? = null,
    val request_id: String? = null,
) {
    val name get() = nonEmpty(display_name) ?: "Reader"
}

@Serializable
data class FriendsOverview(
    val friends: List<SocialUser> = emptyList(),
    val incoming: List<SocialUser> = emptyList(),
    val outgoing: List<SocialUser> = emptyList(),
)

@Serializable
data class Conversation(
    val id: String = "",
    val other_user_id: String? = null,
    val other: SocialUser? = null,
    val last_message: String? = null,
    val last_message_date: String? = null,
    val last_sender_id: String? = null,
    @Serializable(with = FlexInt::class) val unread: Int = 0,
)

@Serializable
data class ChatMessage(
    val id: String = "",
    val conversation_id: String? = null,
    val sender_id: String? = null,
    val receiver_id: String? = null,
    val content: String? = null,
    val read: Boolean? = null,
    val created_date: String? = null,
)

@Serializable
data class VaultNotification(
    val id: String = "",
    val type: String? = null,
    val title: String? = null,
    val body: String? = null,
    val related_id: String? = null,
    val read: Boolean? = null,
    val created_date: String? = null,
)

@Serializable
data class SocialProfileResponse(
    val profile: SocialUser? = null,
    val isOwn: Boolean? = null,
    val friend: Boolean? = null,
    @Serializable(with = FlexInt::class) val friendCount: Int = 0,
    @Serializable(with = FlexInt::class) val volCount: Int = 0,
    @Serializable(with = FlexInt::class) val seriesCount: Int = 0,
    val profile_public: Boolean? = null,
    val collection_public: Boolean? = null,
    val is_private: Boolean? = null,
    val error: String? = null,
)

@Serializable
data class SocialCollectionResponse(
    val authorized: Boolean? = null,
    val isOwn: Boolean? = null,
    val friend: Boolean? = null,
    val collection_public: Boolean? = null,
    val owner: SocialUser? = null,
    val items: List<CollectionItem>? = null,
)

/** Response of the `getVolumeSynopsis` backend function. */
@Serializable
data class VolumeSynopsis(
    val synopsis: String? = null,
    val recap: String? = null,
    val location: String? = null,
    val setting: String? = null,
    val main_characters: List<String>? = null,
)

/** Base44 timestamps: "2026-10-14T15:57:21.434000" without zone = UTC, or with "Z"/offset. */
object ServerDate {
    fun parse(s: String?): Instant? {
        if (s.isNullOrBlank()) return null
        runCatching { return OffsetDateTime.parse(s).toInstant() }
        runCatching { return LocalDateTime.parse(s).toInstant(ZoneOffset.UTC) }
        runCatching { return java.time.LocalDate.parse(s.take(10)).atStartOfDay(ZoneId.systemDefault()).toInstant() }
        return null
    }

    /** Local yyyy-MM-dd of a server timestamp. */
    fun localDay(s: String?): String {
        val i = parse(s) ?: return (s ?: "").take(10)
        return i.atZone(ZoneId.systemDefault()).toLocalDate().toString()
    }

    /** "now", "5m", "3h", "2d" or a short date — like the web chat list. */
    fun relative(s: String?): String {
        val d = parse(s) ?: return ""
        val secs = (System.currentTimeMillis() - d.toEpochMilli()) / 1000
        return when {
            secs < 60 -> "now"
            secs < 3600 -> "${secs / 60}m"
            secs < 86_400 -> "${secs / 3600}h"
            secs < 604_800 -> "${secs / 86_400}d"
            else -> DateTimeFormatter.ofPattern("d-M-yyyy").format(d.atZone(ZoneId.systemDefault()))
        }
    }

    /** "Oct 14, 15:57" */
    fun short(s: String?): String {
        val d = parse(s) ?: return ""
        return DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.US).format(d.atZone(ZoneId.systemDefault()))
    }

    /** "Oct 14, 2026" */
    fun date(s: String?): String {
        val d = parse(s) ?: return ""
        return DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US).format(d.atZone(ZoneId.systemDefault()))
    }
}
