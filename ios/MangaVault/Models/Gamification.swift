import Foundation

// Faithful port of the XP / level / achievement / quest / streak rules of the web app.

struct LevelInfo: Hashable {
    let level: Int
    let into: Int
    let span: Int
    let next: Int
    let total: Int

    var progress: Double {
        guard span > 0 else { return 1 }
        let ratio: Double = Double(into) / Double(span)
        return min(1.0, ratio)
    }
    var percent: Int {
        guard span > 0 else { return 100 }
        let ratio: Double = Double(into) / Double(span)
        return Int((ratio * 100).rounded())
    }
}

enum XP {
    static func threshold(_ level: Int) -> Int { 50 * level * (level - 1) }

    static func level(for xp: Int) -> LevelInfo {
        var l = 1
        while threshold(l + 1) <= xp { l += 1 }
        let start = threshold(l), end = threshold(l + 1)
        return LevelInfo(level: l, into: xp - start, span: end - start, next: end, total: xp)
    }

    /// volumes×10 + completed×30 + rated×15 + favorites×5 + notes×8 + genres×20
    static func base(items: [CollectionItem], genres: Int) -> Int {
        let n = items.count
        let completed = items.filter { $0.effectiveStatus == .completed }.count
        let rated = items.filter { ($0.rating ?? 0) > 0 }.count
        let favs = items.filter { $0.isFavorite }.count
        let notes = items.filter { $0.hasNotes }.count
        let volumeXP: Int = n * 10
        let completedXP: Int = completed * 30
        let ratedXP: Int = rated * 15
        let favoriteXP: Int = favs * 5
        let notesXP: Int = notes * 8
        let genreXP: Int = max(0, genres) * 20
        return volumeXP + completedXP + ratedXP + favoriteXP + notesXP + genreXP
    }
}

enum Rarity: String {
    case common, rare, epic, legendary
    var label: String { rawValue.capitalized }
}

struct AchievementStats {
    var vol = 0, series = 0, completed = 0, rated = 0, notes = 0, progressCount = 0, genres = 0, streak = 0, longStreak = 0

    init(items: [CollectionItem], genres: Int, streak: Int, longStreak: Int = 0) {
        vol = items.count
        series = Set(items.compactMap { nonEmpty($0.manga_id) }).count
        self.longStreak = longStreak
        completed = items.filter { $0.effectiveStatus == .completed }.count
        rated = items.filter { ($0.rating ?? 0) > 0 }.count
        notes = items.filter { $0.hasNotes }.count
        progressCount = items.filter { ($0.current_chapter ?? 0) > 0 }.count
        self.genres = genres
        self.streak = streak
    }
}

struct AchievementDef: Identifiable {
    let id: String
    let name: String
    let desc: String
    let rarity: Rarity
    let reward: Int
    let icon: String
    let total: Int
    let value: (AchievementStats) -> Int

    static let all: [AchievementDef] = [
        .init(id: "first_add", name: "First Addition", desc: "Add your first manga to your collection", rarity: .common, reward: 50, icon: "+", total: 1, value: { $0.vol }),
        .init(id: "collector", name: "Collector", desc: "Collect 10 manga volumes", rarity: .common, reward: 100, icon: "📚", total: 10, value: { $0.vol }),
        .init(id: "dedicated", name: "Dedicated Collector", desc: "Collect 50 manga volumes", rarity: .rare, reward: 300, icon: "🗂", total: 50, value: { $0.vol }),
        .init(id: "expert", name: "Manga Expert", desc: "Collect 100 manga volumes", rarity: .epic, reward: 800, icon: "👑", total: 100, value: { $0.vol }),
        .init(id: "first_chapter", name: "First Chapter", desc: "Record reading progress on a volume", rarity: .common, reward: 50, icon: "📖", total: 1, value: { $0.progressCount }),
        .init(id: "getting_started", name: "Getting Started", desc: "Complete your first manga", rarity: .common, reward: 100, icon: "✓", total: 1, value: { $0.completed }),
        .init(id: "completionist", name: "Completionist", desc: "Complete 10 manga", rarity: .rare, reward: 400, icon: "🏆", total: 10, value: { $0.completed }),
        .init(id: "genre_explorer", name: "Genre Explorer", desc: "Collect manga from 5 different genres", rarity: .rare, reward: 200, icon: "🧭", total: 5, value: { $0.genres }),
        .init(id: "variety_fan", name: "Variety Fan", desc: "Explore 10 different genres", rarity: .epic, reward: 400, icon: "🎨", total: 10, value: { $0.genres }),
        .init(id: "rating_master", name: "Rating Master", desc: "Rate 25 manga", rarity: .rare, reward: 250, icon: "⭐", total: 25, value: { $0.rated }),
        .init(id: "organized", name: "Organized Reader", desc: "Add notes to 10 manga", rarity: .common, reward: 150, icon: "📝", total: 10, value: { $0.notes }),
        .init(id: "first_volume", name: "First Volume", desc: "Complete your first volume read", rarity: .common, reward: 100, icon: "✓", total: 1, value: { $0.completed }),
        .init(id: "streak", name: "Consistent Reader", desc: "Maintain a 7-day reading streak", rarity: .epic, reward: 500, icon: "🔥", total: 7, value: { $0.streak }),
        .init(id: "series_explorer", name: "Series Explorer", desc: "Collect 15 different manga series", rarity: .epic, reward: 500, icon: "🧭", total: 15, value: { $0.series }),
        .init(id: "manga_master", name: "Manga Master", desc: "Collect 200 manga volumes", rarity: .legendary, reward: 1500, icon: "👑", total: 200, value: { $0.vol }),
        .init(id: "unbreakable", name: "Unbreakable", desc: "Maintain a 30-day reading streak", rarity: .legendary, reward: 1200, icon: "🔥", total: 30, value: { $0.longStreak })
    ]
}

struct AchievementEval: Identifiable {
    let def: AchievementDef
    let value: Int
    let unlocked: Bool
    var id: String { def.id }
    var total: Int { def.total }
    var percent: Int {
        guard total > 0 else { return 0 }
        let ratio: Double = Double(value) / Double(total)
        return min(100, Int((ratio * 100).rounded()))
    }

    static func evaluate(_ stats: AchievementStats) -> [AchievementEval] {
        AchievementDef.all.map { def in
            let v = def.value(stats)
            return AchievementEval(def: def, value: min(v, def.total), unlocked: v >= def.total)
        }
    }
}

struct QuestDef: Identifiable, Hashable {
    let id: String
    let name: String
    let desc: String
    let reward: Int
    let bonus: Bool
    let key: String
    let total: Int

    static let all: [QuestDef] = [
        .init(id: "q_add", name: "Add a Manga", desc: "Add a manga to your collection today", reward: 30, bonus: false, key: "added", total: 1),
        .init(id: "q_rate", name: "Rate a Manga", desc: "Rate a manga in your collection today", reward: 30, bonus: false, key: "rated", total: 1),
        .init(id: "q_progress", name: "Update Progress", desc: "Update reading progress on a volume today", reward: 40, bonus: false, key: "progress", total: 1),
        .init(id: "q_complete", name: "Complete a Manga", desc: "Complete a manga today", reward: 80, bonus: true, key: "completed", total: 1)
    ]

    static let weekly: [QuestDef] = [
        .init(id: "w_active", name: "Active Week", desc: "Record reading activity on 3 different days this week", reward: 120, bonus: false, key: "activeDays", total: 3),
        .init(id: "w_progress", name: "Keep Reading", desc: "Update reading progress on 3 volumes this week", reward: 150, bonus: false, key: "progress", total: 3),
        .init(id: "w_add", name: "Grow the Vault", desc: "Add 3 manga to your collection this week", reward: 120, bonus: false, key: "added", total: 3),
        .init(id: "w_complete", name: "Finish Strong", desc: "Complete 2 manga this week", reward: 250, bonus: true, key: "completed", total: 2)
    ]
}

struct QuestState: Identifiable, Hashable {
    let def: QuestDef
    let value: Int
    /// Server verdict from the `rewards` function, when available.
    var serverCompleted: Bool? = nil
    var id: String { def.id }
    var completed: Bool { serverCompleted ?? (value >= def.total) }
    var percent: Int {
        guard def.total > 0 else { return 0 }
        let ratio: Double = Double(value) / Double(def.total)
        return min(100, Int((ratio * 100).rounded()))
    }
}

enum DayKey {
    /// Local `yyyy-MM-dd`, like the web app's `S_()` helper.
    static func local(_ date: Date = Date()) -> String {
        let c = Calendar.current.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }

    /// Consecutive-day streak ending today (or yesterday), based on activity days.
    static func streak(_ days: [String]) -> Int {
        let set = Set(days)
        var date = Date()
        let yesterday = date.addingTimeInterval(-86_400)
        if !set.contains(local(date)) && !set.contains(local(yesterday)) { return 0 }
        if !set.contains(local(date)) { date = yesterday }
        var n = 0
        while set.contains(local(date)) {
            n += 1
            date = date.addingTimeInterval(-86_400)
        }
        return n
    }

    /// Longest run of consecutive days ever (web: `mv`).
    static func longest(_ days: [String]) -> Int {
        let sorted = Array(Set(days)).filter { !$0.isEmpty }.sorted()
        guard !sorted.isEmpty else { return 0 }
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; f.timeZone = TimeZone(identifier: "UTC")
        var best = 1, cur = 1
        for i in 1..<sorted.count {
            if let a = f.date(from: sorted[i - 1]), let b = f.date(from: sorted[i]), Int((b.timeIntervalSince(a) / 86_400).rounded()) == 1 {
                cur += 1; best = max(best, cur)
            } else { cur = 1 }
        }
        return best
    }

    /// Monday 00:00 of the current week (local).
    static func weekStart(_ date: Date = Date()) -> Date {
        var cal = Calendar(identifier: .gregorian); cal.timeZone = .current
        let day = cal.startOfDay(for: date)
        let weekday = cal.component(.weekday, from: day) // 1 = Sunday
        let offset = (weekday + 5) % 7                     // days since Monday
        return cal.date(byAdding: .day, value: -offset, to: day) ?? day
    }

    /// ISO week key like "2026-W42" (web: `uv`).
    static func isoWeek(_ date: Date = Date()) -> String {
        var cal = Calendar(identifier: .iso8601); cal.timeZone = .current
        let c = cal.dateComponents([.yearForWeekOfYear, .weekOfYear], from: date)
        return String(format: "%04d-W%02d", c.yearForWeekOfYear ?? 0, c.weekOfYear ?? 0)
    }
}
