package nl.lrvdlinden.mangavault.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import kotlin.math.roundToInt

// Port of the XP / level / achievement / quest / streak rules of the web app.

data class LevelInfo(val level: Int, val into: Int, val span: Int, val next: Int, val total: Int) {
    val progress: Float get() = if (span > 0) minOf(1f, into.toFloat() / span) else 1f
    val percent: Int get() = if (span > 0) (into.toDouble() / span * 100).roundToInt() else 100
}

object XP {
    fun threshold(level: Int) = 50 * level * (level - 1)

    fun level(xp: Int): LevelInfo {
        var l = 1
        while (threshold(l + 1) <= xp) l++
        val start = threshold(l)
        val end = threshold(l + 1)
        return LevelInfo(l, xp - start, end - start, end, xp)
    }
}

enum class Rarity(val label: String) { COMMON("Common"), RARE("Rare"), EPIC("Epic"), LEGENDARY("Legendary") }

class AchievementStats(items: List<CollectionItem>, val genres: Int, val streak: Int, val longStreak: Int = 0) {
    val vol = items.size
    val series = items.mapNotNull { nonEmpty(it.manga_id) }.toSet().size
    val completed = items.count { it.effectiveStatus == ReadStatus.COMPLETED }
    val rated = items.count { (it.rating ?: 0.0) > 0 }
    val notes = items.count { it.hasNotes }
    val progressCount = items.count { (it.current_chapter ?: 0.0) > 0 }
}

class AchievementDef(
    val id: String, val name: String, val desc: String, val rarity: Rarity, val reward: Int,
    val icon: String, val total: Int, val value: (AchievementStats) -> Int,
) {
    companion object {
        val all = listOf(
            AchievementDef("first_add", "First Addition", "Add your first manga to your collection", Rarity.COMMON, 50, "+", 1) { it.vol },
            AchievementDef("collector", "Collector", "Collect 10 manga volumes", Rarity.COMMON, 100, "📚", 10) { it.vol },
            AchievementDef("dedicated", "Dedicated Collector", "Collect 50 manga volumes", Rarity.RARE, 300, "🗂", 50) { it.vol },
            AchievementDef("expert", "Manga Expert", "Collect 100 manga volumes", Rarity.EPIC, 800, "👑", 100) { it.vol },
            AchievementDef("first_chapter", "First Chapter", "Record reading progress on a volume", Rarity.COMMON, 50, "📖", 1) { it.progressCount },
            AchievementDef("getting_started", "Getting Started", "Complete your first manga", Rarity.COMMON, 100, "✓", 1) { it.completed },
            AchievementDef("completionist", "Completionist", "Complete 10 manga", Rarity.RARE, 400, "🏆", 10) { it.completed },
            AchievementDef("genre_explorer", "Genre Explorer", "Collect manga from 5 different genres", Rarity.RARE, 200, "🧭", 5) { it.genres },
            AchievementDef("variety_fan", "Variety Fan", "Explore 10 different genres", Rarity.EPIC, 400, "🎨", 10) { it.genres },
            AchievementDef("rating_master", "Rating Master", "Rate 25 manga", Rarity.RARE, 250, "⭐", 25) { it.rated },
            AchievementDef("organized", "Organized Reader", "Add notes to 10 manga", Rarity.COMMON, 150, "📝", 10) { it.notes },
            AchievementDef("first_volume", "First Volume", "Complete your first volume read", Rarity.COMMON, 100, "✓", 1) { it.completed },
            AchievementDef("streak", "Consistent Reader", "Maintain a 7-day reading streak", Rarity.EPIC, 500, "🔥", 7) { it.streak },
            AchievementDef("series_explorer", "Series Explorer", "Collect 15 different manga series", Rarity.EPIC, 500, "🧭", 15) { it.series },
            AchievementDef("manga_master", "Manga Master", "Collect 200 manga volumes", Rarity.LEGENDARY, 1500, "👑", 200) { it.vol },
            AchievementDef("unbreakable", "Unbreakable", "Maintain a 30-day reading streak", Rarity.LEGENDARY, 1200, "🔥", 30) { it.longStreak },
        )
        fun byId(id: String) = all.firstOrNull { it.id == id }
    }
}

data class AchievementEval(val def: AchievementDef, val value: Int, val unlocked: Boolean) {
    val id get() = def.id
    val total get() = def.total
    val percent get() = if (total > 0) minOf(100, (value.toDouble() / total * 100).roundToInt()) else 0

    companion object {
        fun evaluate(stats: AchievementStats) = AchievementDef.all.map { def ->
            val v = def.value(stats)
            AchievementEval(def, minOf(v, def.total), v >= def.total)
        }
    }
}

data class QuestDef(
    val id: String, val name: String, val desc: String, val reward: Int, val bonus: Boolean, val key: String, val total: Int,
) {
    companion object {
        val all = listOf(
            QuestDef("q_add", "Add a Manga", "Add a manga to your collection today", 30, false, "added", 1),
            QuestDef("q_rate", "Rate a Manga", "Rate a manga in your collection today", 30, false, "rated", 1),
            QuestDef("q_progress", "Update Progress", "Update reading progress on a volume today", 40, false, "progress", 1),
            QuestDef("q_complete", "Complete a Manga", "Complete a manga today", 80, true, "completed", 1),
        )
        val weekly = listOf(
            QuestDef("w_active", "Active Week", "Record reading activity on 3 different days this week", 120, false, "activeDays", 3),
            QuestDef("w_progress", "Keep Reading", "Update reading progress on 3 volumes this week", 150, false, "progress", 3),
            QuestDef("w_add", "Grow the Vault", "Add 3 manga to your collection this week", 120, false, "added", 3),
            QuestDef("w_complete", "Finish Strong", "Complete 2 manga this week", 250, true, "completed", 2),
        )
    }
}

data class QuestState(val def: QuestDef, val value: Int, val serverCompleted: Boolean? = null) {
    val id get() = def.id
    val completed get() = serverCompleted ?: (value >= def.total)
    val percent get() = if (def.total > 0) minOf(100, (value.toDouble() / def.total * 100).roundToInt()) else 0
}

object DayKey {
    /** Local yyyy-MM-dd. */
    fun local(date: LocalDate = LocalDate.now()): String = date.toString()

    /** Consecutive-day streak ending today (or yesterday). */
    fun streak(days: Collection<String>): Int {
        val set = days.toSet()
        var d = LocalDate.now()
        if (local(d) !in set && local(d.minusDays(1)) !in set) return 0
        if (local(d) !in set) d = d.minusDays(1)
        var n = 0
        while (local(d) in set) { n++; d = d.minusDays(1) }
        return n
    }

    /** Longest run of consecutive days ever. */
    fun longest(days: Collection<String>): Int {
        val sorted = days.filter { it.isNotEmpty() }.toSet().mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.sorted()
        if (sorted.isEmpty()) return 0
        var best = 1
        var cur = 1
        for (i in 1 until sorted.size) {
            if (ChronoUnit.DAYS.between(sorted[i - 1], sorted[i]) == 1L) { cur++; best = maxOf(best, cur) } else cur = 1
        }
        return best
    }

    /** Monday of the current week. */
    fun weekStart(date: LocalDate = LocalDate.now()): LocalDate =
        date.minusDays((date.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())

    /** ISO week key like "2026-W42". */
    fun isoWeek(date: LocalDate = LocalDate.now()): String =
        String.format("%04d-W%02d", date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))
}
