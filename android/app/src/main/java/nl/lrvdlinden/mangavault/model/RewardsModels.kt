package nl.lrvdlinden.mangavault.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import nl.lrvdlinden.mangavault.core.FlexInt
import nl.lrvdlinden.mangavault.core.flexInt
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

// Server-side rewards (function `rewards`), Vault Capsules (`vaultCapsules`),
// trading cards, community events and manga rooms. All fields optional.

@Serializable
data class ServerLevel(
    @Serializable(with = FlexInt::class) val level: Int = 1,
    @Serializable(with = FlexInt::class) val into: Int = 0,
    @Serializable(with = FlexInt::class) val span: Int = 0,
    @Serializable(with = FlexInt::class) val next: Int = 0,
    @Serializable(with = FlexInt::class) val total: Int = 0,
) {
    val info get() = LevelInfo(maxOf(level, 1), into, span, next, total)
}

@Serializable
data class ServerXP(
    @Serializable(with = FlexInt::class) val base: Int = 0,
    @Serializable(with = FlexInt::class) val bonus: Int = 0,
    @Serializable(with = FlexInt::class) val total: Int = 0,
)

@Serializable
data class ServerQuest(
    val id: String = "",
    @Serializable(with = FlexInt::class) val value: Int = 0,
    @Serializable(with = FlexInt::class) val total: Int = 0,
    @Serializable(with = FlexInt::class) val xp: Int = 0,
    val completed: Boolean? = null,
    val claimed: Boolean? = null,
)

@Serializable
data class DailyRewardState(
    val claimedToday: Boolean? = null,
    @Serializable(with = FlexInt::class) val streak: Int = 0,
    @Serializable(with = FlexInt::class) val nextStreak: Int = 0,
    @Serializable(with = FlexInt::class) val xp: Int = 0,
    val capsule: Boolean? = null,
    val nextAvailable: String? = null,
)

@Serializable
data class LevelReward(
    @Serializable(with = FlexInt::class) val level: Int = 0,
    val tier: String? = null,
    val label: String? = null,
    val unlocked: Boolean? = null,
)

@Serializable
data class FreshReward(
    val kind: String? = null,
    val label: String? = null,
    @Serializable(with = FlexInt::class) val xp: Int = 0,
)

@Serializable
data class RewardsState(
    val xp: ServerXP = ServerXP(),
    val level: ServerLevel = ServerLevel(),
    val achievements: List<UnlockedAchievement>? = null,
    val quests: List<ServerQuest>? = null,
    val weekly: List<ServerQuest>? = null,
    val daily: DailyRewardState? = null,
    val today: String? = null,
    val levelRewards: List<LevelReward>? = null,
    val fresh: List<FreshReward>? = null,
)

@Serializable
data class RewardHistoryItem(
    val id: String = "",
    val kind: String? = null,
    val label: String? = null,
    @Serializable(with = FlexInt::class) val xp: Int = 0,
    val status: String? = null,
    val granted_at: String? = null,
    val created_date: String? = null,
) {
    companion object {
        val kindLabels = mapOf(
            "daily_quest" to "Daily quest", "weekly_quest" to "Weekly quest", "achievement" to "Achievement",
            "daily_reward" to "Daily reward", "level_reward" to "Level reward", "migration" to "Migration",
        )
    }
}

// MARK: Vault Capsules

@Serializable
data class CapsuleReward(
    val kind: String? = null,
    val title: String? = null,
    val rarity: String? = null,
    val artwork_url: String? = null,
)

@Serializable
data class VaultCapsule(
    val id: String = "",
    val tier: String? = null,
    val source: String? = null,
    val opened: Boolean? = null,
    val opened_date: String? = null,
    val reward: CapsuleReward? = null,
    val created_date: String? = null,
)

/** vaultCapsules { action: state }. `odds`: tier → (reward kind → probability), `fallback_xp`: tier → xp. */
@Serializable
data class CapsulesState(
    val capsules: List<VaultCapsule> = emptyList(),
    val odds: Map<String, Map<String, Double>> = emptyMap(),
    val fallback_xp: Map<String, JsonElement> = emptyMap(),
) {
    fun fallbackXp(tier: String): Int = (fallback_xp[tier] as? JsonPrimitive)?.flexInt() ?: 0
}

// MARK: Trading cards

enum class CardRarity(val raw: String, val hex: String) {
    COMMON("common", "#9ca3af"),
    UNCOMMON("uncommon", "#22c55e"),
    RARE("rare", "#3b82f6"),
    EPIC("epic", "#a855f7"),
    LEGENDARY("legendary", "#f59e0b"),
    MYTHIC("mythic", "#ef4444");

    val label get() = raw.replaceFirstChar { it.uppercase() }

    companion object {
        fun of(s: String?) = entries.firstOrNull { it.raw == s } ?: COMMON
    }
}

@Serializable
data class TradingCard(
    val id: String = "",
    val card_id: String = "",
    val title: String = "Card",
    val category: String? = null,
    val rarity: String? = null,
    val description: String? = null,
    val artwork_url: String? = null,
    val manga_id: String? = null,
    val flavor: String? = null,
) {
    val key get() = card_id.ifEmpty { id }
    val rarityValue get() = CardRarity.of(rarity)
    val isLimited get() = category == "limited"
}

@Serializable
data class UserCard(
    val id: String = "",
    val card_id: String = "",
    @Serializable(with = FlexInt::class) val count: Int = 1,
    val favorite: Boolean? = null,
    val source: String? = null,
    val created_date: String? = null,
)

// MARK: Community events

@Serializable
data class EventQuestDef(
    val key: String = "",
    val label: String? = null,
    val description: String? = null,
    val icon: String? = null,
    val kind: String? = null,
    @Serializable(with = FlexInt::class) val target: Int = 0,
    @Serializable(with = FlexInt::class) val xp: Int = 0,
    @Serializable(with = FlexInt::class) val points: Int = 0,
    val reward_card_id: String? = null,
    val badge_id: String? = null,
    val repeatable: Boolean? = null,
    // State (filled by eventEngine / grand_state)
    @Serializable(with = FlexInt::class) val count: Int = 0,
    val done: Boolean? = null,
    val claimed: Boolean? = null,
) {
    val kindLabel get() = kindLabels[kind ?: ""] ?: (kind ?: "")

    companion object {
        val kindLabels = mapOf(
            "collect_volumes" to "Add volumes to the collection",
            "new_series" to "Add a manga not yet in the collection",
            "progress_volumes" to "Update reading progress",
            "chapters" to "Log reading activity",
            "review" to "Publish a review",
            "daily_quests" to "Complete daily quests",
            "visit" to "Open the event page",
            "community_goal" to "Reach the community goal",
            "quests_done" to "Complete other quests of this event",
        )
    }
}

@Serializable
data class CommunityEvent(
    val id: String = "",
    val title: String? = null,
    val subtitle: String? = null,
    val short_description: String? = null,
    val long_description: String? = null,
    val description: String? = null,
    val type: String? = null,
    val icon: String? = null,
    val banner_url: String? = null,
    val background_url: String? = null,
    val accent_color: String? = null,
    val decoration: String? = null,
    val theme: String? = null,
    val start_date: String? = null,
    val start_time: String? = null,
    val end_date: String? = null,
    val end_time: String? = null,
    val timezone: String? = null,
    val reward_claim_end: String? = null,
    val publish_status: String? = null,
    val status: String? = null,
    val featured: Boolean? = null,
    val requires_join: Boolean? = null,
    val enabled: Boolean? = null,
    val requirement_type: String? = null,
    @Serializable(with = FlexInt::class) val requirement_value: Int = 0,
    val reward_card_id: String? = null,
    val extra_card_ids: List<String>? = null,
    val badge_name: String? = null,
    val image_source: String? = null,
    val quests: List<EventQuestDef>? = null,
) {
    val typeLabel get() = typeLabels[type ?: ""] ?: (type ?: "")
    val accent get() = nonEmpty(accent_color) ?: "#FF7A00"
    val heroImage get() = nonEmpty(background_url) ?: nonEmpty(banner_url)
    val displayTitle get() = listOfNotNull(nonEmpty(icon), title).joinToString(" ")

    companion object {
        val typeLabels = mapOf(
            "manga_week" to "Manga of the Week", "genre_month" to "Genre of the Month", "challenge" to "Challenge",
            "marathon" to "Reading Marathon", "discovery" to "Discovery Event", "card_event" to "Card Event",
            "halloween_event" to "Halloween Event", "christmas_event" to "Christmas Event", "holiday_event" to "Holiday Event",
            "special_release" to "Special Release", "grand_event" to "Grand Event", "manga_release" to "Manga Release",
            "community_challenge" to "Community Challenge", "special_event" to "Special Event",
        )
        val requirementLabels = mapOf(
            "added" to "volumes added", "completed" to "volumes completed", "progress" to "progress updates",
            "minutes" to "reading minutes", "chapters" to "chapters read", "series" to "new series tracked", "rated" to "manga rated",
        )
    }
}

@Serializable
data class EventParticipation(
    val id: String? = null,
    val event_id: String? = null,
    @Serializable(with = FlexInt::class) val progress: Int = 0,
    val completed: Boolean? = null,
    val reward_claimed: Boolean? = null,
)

@Serializable
data class CommunityProgress(
    @Serializable(with = FlexInt::class) val total: Int = 0,
    @Serializable(with = FlexInt::class) val goal: Int = 0,
    @Serializable(with = FlexInt::class) val participants: Int = 0,
    @Serializable(with = FlexInt::class) val mine: Int = 0,
)

/** communityEngine { action: banner }. kind: "christmas" | "event" | "grand" | other (release). */
@Serializable
data class EventBannerData(
    val kind: String? = null,
    val status: String? = null,
    val target: String? = null,
    val event: CommunityEvent? = null,
    val participation: EventParticipation? = null,
    @Serializable(with = FlexInt::class) val max_points: Int = 0,
)

@Serializable
data class EventRewards(val cards: List<TradingCard>? = null, val badge_name: String? = null)

/** eventEngine { action: state } */
@Serializable
data class EventEngineState(
    val event: CommunityEvent? = null,
    val status: String? = null,
    val can_claim: Boolean? = null,
    val start: String? = null,
    val end: String? = null,
    val participation: EventParticipation? = null,
    val quests: List<EventQuestDef>? = null,
    @Serializable(with = FlexInt::class) val points: Int = 0,
    @Serializable(with = FlexInt::class) val earned: Int = 0,
    @Serializable(with = FlexInt::class) val done_count: Int = 0,
    val quests_completed: Boolean? = null,
    @Serializable(with = FlexInt::class) val max_xp: Int = 0,
    val rewards: EventRewards? = null,
    val owned_cards: List<String>? = null,
    val community: CommunityProgress? = null,
    val error: String? = null,
)

@Serializable
data class GrandQuests(
    @Serializable(with = FlexInt::class) val points: Int = 0,
    val quests: List<EventQuestDef>? = null,
)

/** communityEngine { action: grand_state | grand_claim } */
@Serializable
data class GrandEventState(
    val status: String? = null,
    val event: CommunityEvent? = null,
    val start: String? = null,
    val end: String? = null,
    val quests: GrandQuests? = null,
    val community: CommunityProgress? = null,
    val granted: List<String>? = null,
    val error: String? = null,
)

/** communityEngine { action: release_state | christmas_state }. quest_states: key → { done, count } */
@Serializable
data class SpecialQuests(
    val quest_states: Map<String, JsonElement>? = null,
    @Serializable(with = FlexInt::class) val points: Int = 0,
)

@Serializable
data class SpecialEventState(
    val status: String? = null,
    val participation: EventParticipation? = null,
    val quests: SpecialQuests? = null,
) {
    private fun flag(key: String): JsonObject? = quests?.quest_states?.get(key) as? JsonObject
    fun isDone(key: String): Boolean = (flag(key)?.get("done") as? JsonPrimitive)?.booleanOrNull ?: false
    fun count(key: String): Int = (flag(key)?.get("count") as? JsonPrimitive)?.let { it.flexInt() ?: it.doubleOrNull?.toInt() } ?: 0
}

@Serializable
data class LeaderboardRow(
    val user_id: String? = null,
    val name: String? = null,
    @Serializable(with = FlexInt::class) val progress: Int = 0,
) {
    val key get() = (user_id ?: "") + (name ?: "")
}

// MARK: Manga room

@Serializable
data class RoomItem(
    val kind: String = "decoration",
    val ref: String? = null,
    val label: String? = null,
    val art: String? = null,
    val x: Double = 50.0,
    val y: Double = 50.0,
    val scale: Double? = 1.0,
) {
    fun toMap(): Map<String, Any?> = mapOf("kind" to kind, "ref" to (ref ?: ""), "label" to (label ?: ""),
        "art" to (art ?: ""), "x" to x, "y" to y, "scale" to (scale ?: 1.0))
}

@Serializable
data class RoomLayout(
    val id: String = "",
    val items: List<RoomItem>? = null,
    val is_public: Boolean? = null,
    val created_by_id: String? = null,
)

// MARK: Dashboard layout (UserProfile.dashboard_layout)

@Serializable
data class DashboardSection(val id: String = "", val hidden: Boolean = false) {
    val label get() = all.firstOrNull { it.first == id }?.second ?: id

    companion object {
        val all = listOf(
            "continue" to "Continue Reading", "quests" to "Daily Quests", "achievements" to "Recent Achievements",
            "cards" to "Recent Trading Cards", "recent" to "Recently Added", "charts" to "Charts",
        )
        val defaults get() = all.map { DashboardSection(it.first) }

        /** Keep known ids in stored order, append missing ones (same as the web app). */
        fun normalized(stored: List<DashboardSection>?): List<DashboardSection> {
            val known = all.map { it.first }.toSet()
            val out = (stored ?: emptyList()).filter { it.id in known }.distinctBy { it.id }.toMutableList()
            for ((id, _) in all) if (out.none { it.id == id }) out += DashboardSection(id)
            return out
        }
    }
}
