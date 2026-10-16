import Foundation

/// Domain operations shared by several screens (mirrors the helper functions of the web app).
enum VaultService {
    static var api: Base44Client { .shared }

    // MARK: Loading

    static func collection(limit: Int = 500) async throws -> [CollectionItem] {
        try await api.list("CollectionItem", CollectionItem.self, sort: "-created_date", limit: limit).items
    }

    static func wishlist(limit: Int = 500) async throws -> [WishlistItem] {
        try await api.list("WishlistItem", WishlistItem.self, sort: "-created_date", limit: limit).items
    }

    static func activities(limit: Int = 500) async throws -> [ReadingActivity] {
        try await api.list("ReadingActivity", ReadingActivity.self, sort: "-created_date", limit: limit).items
    }

    /// manga_id → genre string, for the manga in the collection.
    static func genreMap(for items: [CollectionItem]) async throws -> [String: String] {
        let ids = Array(Set(items.compactMap { $0.manga_id }.filter { !$0.isEmpty }))
        guard !ids.isEmpty else { return [:] }
        struct G: Decodable { let id: String; let genre: String? }
        var map: [String: String] = [:]
        // Chunk to keep URLs a sensible length.
        var chunks: [[String]] = []
        var start = 0
        while start < ids.count {
            let end: Int = min(start + 150, ids.count)
            chunks.append(Array(ids[start..<end]))
            start = end
        }
        for chunk in chunks {
            let page = try await api.list("Manga", G.self, query: ["id": ["$in": chunk]], limit: 500, fields: ["id", "genre", "title"])
            for m in page.items { if let g = m.genre, !g.isEmpty { map[m.id] = g } }
        }
        return map
    }

    static func genres(of genre: String) -> [String] {
        genre.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces).lowercased() }.filter { !$0.isEmpty }
    }

    static func genreCount(_ items: [CollectionItem], _ map: [String: String]) -> Int {
        var set = Set<String>()
        for i in items { if let g = map[i.manga_id ?? ""] { genres(of: g).forEach { set.insert($0) } } }
        return set.count
    }

    static func genreDistribution(_ items: [CollectionItem], _ map: [String: String], top: Int) -> [(String, Int)] {
        var counts: [String: Int] = [:]
        for i in items { if let g = map[i.manga_id ?? ""] { genres(of: g).forEach { counts[$0, default: 0] += 1 } } }
        return counts.sorted { $0.value > $1.value }.prefix(top).map { ($0.key, $0.value) }
    }

    static func streak(_ activities: [ReadingActivity]) -> Int {
        DayKey.streak(activities.map { $0.day }.filter { !$0.isEmpty })
    }

    // MARK: Activity log

    static func log(_ type: String, mangaId: String?, title: String?, volume: Int?, meta: String = "") {
        var payload: [String: Any] = [:]
        payload["type"] = type
        payload["manga_id"] = (mangaId ?? "") as String
        payload["title"] = (title ?? "") as String
        if let volume { payload["volume_number"] = volume } else { payload["volume_number"] = NSNull() }
        payload["meta"] = meta
        let body: [String: Any] = payload
        Task.detached {
            _ = try? await Base44Client.shared.create("ReadingActivity", body, ReadingActivity.self)
        }
    }

    // MARK: Collection / wishlist creation payloads

    static func collectionPayload(manga: Manga, volume: Int?, volumeCover: String, status: ReadStatus? = nil) -> [String: Any] {
        var d: [String: Any] = [:]
        d["manga_id"] = manga.id
        if let volume {
            d["title"] = "\(manga.title) Vol. \(volume)"
            d["volume_number"] = volume
            d["edition"] = "standard"
        } else {
            d["title"] = manga.title
            d["volume_number"] = NSNull()
            d["edition"] = "omnibus"
        }
        d["cover_image_url"] = (manga.cover_image_url ?? "") as String
        d["cover_seed"] = (manga.cover_seed ?? manga.title) as String
        d["publisher"] = (manga.publisher ?? "") as String
        let price: Double = manga.price ?? 0
        d["price"] = price
        d["is_shonen_jump"] = manga.isShonenJump
        d["read"] = false
        if volume != nil { d["volume_cover_url"] = volumeCover }
        if volume == nil {
            d["volumes_included"] = (manga.volumes_included ?? "") as String
            d["omnibus_of"] = (manga.omnibus_of ?? "") as String
        }
        if let status { d["status"] = status.rawValue }
        return d
    }

    static func wishlistPayload(manga: Manga, volume: Int?, volumeCover: String) -> [String: Any] {
        var d = collectionPayload(manga: manga, volume: volume, volumeCover: volumeCover)
        d.removeValue(forKey: "read")
        return d
    }

    // MARK: Volume covers

    static func volumeCovers(for manga: Manga) async -> [VolumeCover] {
        let stored = (manga.volume_covers ?? []).filter { $0.volume > 0 && nonEmpty($0.url) != nil }
        if !stored.isEmpty { return stored }
        guard let mdx = manga.mangadex_id, !mdx.isEmpty else { return [] }
        struct R: Decodable { let covers: [VolumeCover]? }
        guard let data = try? await api.invoke("getVolumeCovers", ["mangadex_id": mdx, "manga_id": manga.id]),
              let r = try? api.decode(R.self, data) else { return [] }
        return (r.covers ?? []).filter { $0.volume > 0 && nonEmpty($0.url) != nil }
    }
}

/// Caches the signed-in user's `UserProfile` record (like `c_()` / `l_()` in the web app).
actor ProfileStore {
    static let shared = ProfileStore()
    private var cached: UserProfile?

    func invalidate() { cached = nil }

    func current(userId: String) async -> UserProfile? {
        if let cached { return cached }
        let page = try? await Base44Client.shared.list("UserProfile", UserProfile.self, query: ["created_by_id": userId], limit: 1)
        cached = page?.items.first
        return cached
    }

    func ensure(userId: String) async throws -> UserProfile {
        if let p = await current(userId: userId) { return p }
        let p = try await Base44Client.shared.create("UserProfile", ["theme_id": "kaiachu-orange"], UserProfile.self)
        cached = p
        return p
    }

    func set(_ p: UserProfile) { cached = p }
}

/// Achievements, XP and levels now come from the server (`rewards` function).
/// Progress values are still computed locally, exactly like the web app does.
enum AchievementEngine {
    struct Result {
        let evals: [AchievementEval]
        let unlockedDates: [String: String]
        let fresh: [FreshReward]
        let state: RewardsState
        var level: LevelInfo { state.level.info }
        var totalXP: Int { state.xp.total }
    }

    static func run() async throws -> Result {
        async let itemsT = VaultService.collection()
        async let actsT = VaultService.activities()
        let items = try await itemsT, acts = try await actsT
        let gmap = try await VaultService.genreMap(for: items)
        let genres = VaultService.genreCount(items, gmap)
        let days = acts.map { $0.day }.filter { !$0.isEmpty }
        let local = AchievementEval.evaluate(AchievementStats(items: items, genres: genres, streak: DayKey.streak(days), longStreak: DayKey.longest(days)))
        let state = try await Engines.rewardsState()
        var dates: [String: String] = [:]
        for a in state.achievements ?? [] { dates[a.id] = a.date ?? "" }
        let evals = local.map { AchievementEval(def: $0.def, value: $0.value, unlocked: dates[$0.def.id] != nil) }
        await ProfileStore.shared.invalidate()
        return Result(evals: evals, unlockedDates: dates, fresh: state.fresh ?? [], state: state)
    }
}
