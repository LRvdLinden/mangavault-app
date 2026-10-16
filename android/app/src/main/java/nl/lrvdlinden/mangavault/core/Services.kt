package nl.lrvdlinden.mangavault.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import nl.lrvdlinden.mangavault.model.*

/**
 * Server-side engines used by the web app: `rewards`, `vaultCapsules`, `communityEngine`, `eventEngine`.
 * XP, levels, achievements and quest claims are owned by the server — the app never writes them itself.
 */
object Engines {
    /** Invokes a function and turns `{ "error": "…" }` bodies into an ApiException. Returns the raw JSON text. */
    suspend fun call(function: String, body: Map<String, Any?>): String {
        val text = Base44.invoke(function, body)
        parseObject(text).str("error")?.takeIf { it.isNotBlank() }?.let { throw ApiException(0, it) }
        return text
    }

    // rewards
    suspend fun rewardsState(): RewardsState = Base44.decode(call("rewards", mapOf("action" to "state")))

    /** scope: "daily" | "weekly". Returns the XP granted. */
    suspend fun claimQuest(scope: String, questId: String): Int =
        parseObject(call("rewards", mapOf("action" to "claim_quest", "scope" to scope, "quest_id" to questId))).int("xp")

    data class DailyClaim(val xp: Int, val capsule: Boolean, val state: RewardsState?)

    @Serializable private data class StateWrap(val state: RewardsState? = null)

    suspend fun claimDaily(): DailyClaim {
        val text = call("rewards", mapOf("action" to "claim_daily"))
        val o = parseObject(text)
        val st = runCatching { Base44.decode<StateWrap>(text).state }.getOrNull()
        return DailyClaim(o.int("xp"), o.bool("capsule"), st)
    }

    suspend fun rewardHistory(cursor: String? = null): Page<RewardHistoryItem> {
        val body = mutableMapOf<String, Any?>("action" to "history")
        if (cursor != null) body["cursor"] = cursor
        return Base44.decode(call("rewards", body))
    }

    // vaultCapsules
    suspend fun capsules(): CapsulesState = Base44.decode(call("vaultCapsules", mapOf("action" to "state")))

    @Serializable private data class CapsuleWrap(val capsule: VaultCapsule? = null)

    suspend fun openCapsule(id: String): VaultCapsule? =
        Base44.decode<CapsuleWrap>(call("vaultCapsules", mapOf("action" to "open", "capsule_id" to id))).capsule

    // communityEngine
    /** Raw call; actions include banner, leaderboard, grand_state, grand_claim, release_state, release_claim, christmas_state, christmas_claim … */
    suspend fun community(action: String, extra: Map<String, Any?> = emptyMap()): String =
        Base44.invoke("communityEngine", extra + ("action" to action))

    suspend fun banner(): EventBannerData = Base44.decode(community("banner"))

    @Serializable private data class Rows(val rows: List<LeaderboardRow>? = null)

    suspend fun leaderboard(eventId: String): List<LeaderboardRow> =
        runCatching { Base44.decode<Rows>(community("leaderboard", mapOf("event_id" to eventId))).rows }.getOrNull() ?: emptyList()

    // eventEngine
    suspend fun eventState(eventId: String, visit: Boolean = true): EventEngineState =
        Base44.decode(Base44.invoke("eventEngine", mapOf("action" to "state", "event_id" to eventId, "visit" to visit)))

    /** action: join | claim_quest | claim_event */
    suspend fun eventAction(action: String, eventId: String, questKey: String? = null): JsonObject {
        val body = mutableMapOf<String, Any?>("action" to action, "event_id" to eventId)
        if (questKey != null) body["quest_key"] = questKey
        return parseObject(call("eventEngine", body))
    }

    // Cards
    suspend fun allCards(): List<TradingCard> =
        Base44.list<TradingCard>("TradingCard", sort = "rarity", limit = 200,
            fields = listOf("card_id", "title", "category", "rarity", "description", "artwork_url", "manga_id", "flavor")).items

    suspend fun cards(ids: List<String>): List<TradingCard> {
        if (ids.isEmpty()) return emptyList()
        return Base44.list<TradingCard>("TradingCard", query = mapOf("card_id" to mapOf("\$in" to ids)), limit = maxOf(ids.size, 1)).items
    }

    suspend fun myCards(limit: Int = 500, query: Map<String, Any?> = emptyMap(), sort: String? = null): List<UserCard> =
        Base44.list<UserCard>("UserCard", query = query, sort = sort, limit = limit,
            fields = listOf("card_id", "count", "favorite", "source", "created_date")).items
}

/** Wrappers around the social* backend functions (same calls as the web app). */
object Social {
    @Serializable
    private data class Items<T>(
        val items: List<T>? = null,
        @Serializable(with = FlexInt::class) val unread: Int = 0,
    )

    private fun throwIfError(text: String) {
        parseObject(text).str("error")?.takeIf { it.isNotBlank() }?.let { throw ApiException(0, it) }
    }

    suspend fun search(query: String): List<SocialUser> =
        Base44.decode<Items<SocialUser>>(Base44.invoke("socialSearch", mapOf("query" to query))).items ?: emptyList()

    suspend fun overview(): FriendsOverview = Base44.decode(Base44.invoke("socialFriends", mapOf("action" to "overview")))

    /** action: send (params: profile_id or user_id) / accept / decline / cancel (request_id) / remove (user_id / profile_id) */
    suspend fun friends(action: String, params: Map<String, Any?>) {
        throwIfError(Base44.invoke("socialFriends", params + ("action" to action)))
    }

    suspend fun conversations(): List<Conversation> =
        Base44.decode<Items<Conversation>>(Base44.invoke("socialChat", mapOf("action" to "list"))).items ?: emptyList()

    suspend fun messages(conversationId: String): List<ChatMessage> =
        Base44.decode<Items<ChatMessage>>(Base44.invoke("socialChat",
            mapOf("action" to "messages", "conversation_id" to conversationId, "limit" to 50))).items ?: emptyList()

    suspend fun send(receiverId: String, content: String) {
        throwIfError(Base44.invoke("socialChat", mapOf("action" to "send", "receiver_id" to receiverId, "content" to content)))
    }

    suspend fun markRead(conversationId: String) {
        runCatching { Base44.invoke("socialChat", mapOf("action" to "read", "conversation_id" to conversationId)) }
    }

    suspend fun profile(profileId: String): SocialProfileResponse =
        Base44.decode(Base44.invoke("socialProfile", mapOf("profile_id" to profileId)))

    suspend fun collection(profileId: String): SocialCollectionResponse =
        Base44.decode(Base44.invoke("socialCollection", mapOf("profile_id" to profileId)))

    suspend fun notifications(): Pair<List<VaultNotification>, Int> {
        val r = Base44.decode<Items<VaultNotification>>(Base44.invoke("socialNotifications", mapOf("action" to "list")))
        return (r.items ?: emptyList()) to r.unread
    }

    suspend fun markAllNotificationsRead() {
        runCatching { Base44.invoke("socialNotifications", mapOf("action" to "readAll")) }
    }
}

/** Domain operations shared by several screens (mirrors the web app's helpers). */
object VaultService {
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun collection(limit: Int = 500): List<CollectionItem> =
        Base44.list<CollectionItem>("CollectionItem", sort = "-created_date", limit = limit).items

    /** Entire collection (cursor paging). */
    suspend fun allCollection(): List<CollectionItem> =
        Base44.listAll("CollectionItem", sort = "-created_date")

    suspend fun wishlist(limit: Int = 500): List<WishlistItem> =
        Base44.list<WishlistItem>("WishlistItem", sort = "-created_date", limit = limit).items

    suspend fun activities(limit: Int = 500): List<ReadingActivity> =
        Base44.list<ReadingActivity>("ReadingActivity", sort = "-created_date", limit = limit).items

    @Serializable private data class G(val id: String = "", val genre: String? = null, val title: String? = null)

    /** manga_id → genre string, for the manga in the collection (chunks of 150 ids). */
    suspend fun genreMap(items: List<CollectionItem>): Map<String, String> {
        val ids = items.mapNotNull { nonEmpty(it.manga_id) }.distinct()
        val map = mutableMapOf<String, String>()
        for (chunk in ids.chunked(150)) {
            val page = Base44.list<G>("Manga", query = mapOf("id" to mapOf("\$in" to chunk)), limit = 500, fields = listOf("id", "genre", "title"))
            for (m in page.items) nonEmpty(m.genre)?.let { map[m.id] = it }
        }
        return map
    }

    fun genres(of: String): List<String> = of.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    fun genreCount(items: List<CollectionItem>, map: Map<String, String>): Int =
        items.flatMap { i -> map[i.manga_id ?: ""]?.let { genres(it) } ?: emptyList() }.toSet().size

    fun genreDistribution(items: List<CollectionItem>, map: Map<String, String>, top: Int): List<Pair<String, Int>> {
        val counts = mutableMapOf<String, Int>()
        for (i in items) map[i.manga_id ?: ""]?.let { g -> genres(g).forEach { counts[it] = (counts[it] ?: 0) + 1 } }
        return counts.entries.sortedByDescending { it.value }.take(top).map { it.key to it.value }
    }

    fun streak(activities: List<ReadingActivity>): Int = DayKey.streak(activities.map { it.day }.filter { it.isNotEmpty() })

    /** Fire-and-forget ReadingActivity log (types: added, rated, progress, completed, wishlist, …). */
    fun log(type: String, mangaId: String?, title: String?, volume: Int?, meta: String = "") {
        background.launch {
            runCatching {
                Base44.create<ReadingActivity>("ReadingActivity", mapOf(
                    "type" to type, "manga_id" to (mangaId ?: ""), "title" to (title ?: ""),
                    "volume_number" to volume, "meta" to meta,
                ))
            }
        }
    }

    fun collectionPayload(manga: Manga, volume: Int?, volumeCover: String, status: ReadStatus? = null): Map<String, Any?> {
        val d = mutableMapOf<String, Any?>(
            "manga_id" to manga.id,
            "title" to (if (volume != null) "${manga.title} Vol. $volume" else manga.title),
            "volume_number" to volume,
            "cover_image_url" to (manga.cover_image_url ?: ""),
            "cover_seed" to (manga.cover_seed ?: manga.title),
            "publisher" to (manga.publisher ?: ""),
            "price" to (manga.price ?: 0.0),
            "is_shonen_jump" to manga.isShonenJump,
            "edition" to (if (volume == null) "omnibus" else "standard"),
            "read" to false,
        )
        if (volume != null) d["volume_cover_url"] = volumeCover
        if (volume == null) {
            d["volumes_included"] = manga.volumes_included ?: ""
            d["omnibus_of"] = manga.omnibus_of ?: ""
        }
        if (status != null) d["status"] = status.raw
        return d
    }

    fun wishlistPayload(manga: Manga, volume: Int?, volumeCover: String): Map<String, Any?> =
        collectionPayload(manga, volume, volumeCover) - "read"

    @Serializable private data class Covers(val covers: List<VolumeCover>? = null)

    suspend fun volumeCovers(manga: Manga): List<VolumeCover> {
        val stored = (manga.volume_covers ?: emptyList()).filter { it.volume > 0 && nonEmpty(it.url) != null }
        if (stored.isNotEmpty()) return stored
        val mdx = nonEmpty(manga.mangadex_id) ?: return emptyList()
        return runCatching {
            Base44.decode<Covers>(Base44.invoke("getVolumeCovers", mapOf("mangadex_id" to mdx, "manga_id" to manga.id))).covers
        }.getOrNull().orEmpty().filter { it.volume > 0 && nonEmpty(it.url) != null }
    }
}

/** Caches the signed-in user's UserProfile record. */
object ProfileStore {
    private val mutex = Mutex()
    @Volatile private var cached: UserProfile? = null

    fun invalidate() { cached = null }
    fun set(p: UserProfile) { cached = p }
    fun peek(): UserProfile? = cached

    suspend fun current(userId: String): UserProfile? = mutex.withLock {
        cached ?: runCatching {
            Base44.list<UserProfile>("UserProfile", query = mapOf("created_by_id" to userId), limit = 1).items.firstOrNull()
        }.getOrNull().also { cached = it }
    }

    suspend fun ensure(userId: String): UserProfile {
        current(userId)?.let { return it }
        val p = Base44.create<UserProfile>("UserProfile", mapOf("theme_id" to "kaiachu-orange"))
        cached = p
        return p
    }
}

/** Achievements, XP and levels come from the server; progress values are computed locally like the web app. */
object AchievementEngine {
    data class Result(
        val evals: List<AchievementEval>,
        val unlockedDates: Map<String, String>,
        val fresh: List<FreshReward>,
        val state: RewardsState,
    ) {
        val level get() = state.level.info
        val totalXP get() = state.xp.total
    }

    suspend fun run(): Result = coroutineScope {
        val itemsT = async { VaultService.collection() }
        val actsT = async { VaultService.activities() }
        val items = itemsT.await()
        val acts = actsT.await()
        val gmap = VaultService.genreMap(items)
        val days = acts.map { it.day }.filter { it.isNotEmpty() }
        val local = AchievementEval.evaluate(AchievementStats(items, VaultService.genreCount(items, gmap), DayKey.streak(days), DayKey.longest(days)))
        val state = Engines.rewardsState()
        val dates = (state.achievements ?: emptyList()).associate { it.id to (it.date ?: "") }
        val evals = local.map { AchievementEval(it.def, it.value, dates.containsKey(it.def.id)) }
        ProfileStore.invalidate()
        Result(evals, dates, state.fresh ?: emptyList(), state)
    }
}
