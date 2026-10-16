import Foundation

/// Wrappers for the server-side engines the web app uses since build 2:
/// `rewards`, `vaultCapsules`, `communityEngine` and `eventEngine`.
/// XP, levels, achievements and quest claims are owned by the server — the app
/// never writes `xp_bonus` or `unlocked_achievements` itself any more.
enum Engines {
    private static var api: Base44Client { .shared }

    /// Invokes a function and surfaces `{ error: "…" }` bodies as errors.
    @discardableResult
    static func call(_ function: String, _ body: [String: Any]) async throws -> Data {
        let d = try await api.invoke(function, body)
        if let obj = try? JSONSerialization.jsonObject(with: d) as? [String: Any],
           let err = obj["error"] as? String, !err.isEmpty {
            throw APIError(status: 0, message: err)
        }
        return d
    }

    static func json(_ d: Data) -> [String: Any] {
        (try? JSONSerialization.jsonObject(with: d) as? [String: Any]) ?? [:]
    }

    // MARK: rewards

    static func rewardsState() async throws -> RewardsState {
        try api.decode(RewardsState.self, try await call("rewards", ["action": "state"]))
    }

    /// scope: "daily" | "weekly". Returns the XP granted.
    static func claimQuest(scope: String, questId: String) async throws -> Int {
        let o = json(try await call("rewards", ["action": "claim_quest", "scope": scope, "quest_id": questId]))
        return (o["xp"] as? NSNumber)?.intValue ?? 0
    }

    struct DailyClaim { let xp: Int; let capsule: Bool; let state: RewardsState? }

    static func claimDaily() async throws -> DailyClaim {
        let d = try await call("rewards", ["action": "claim_daily"])
        let o = json(d)
        struct Wrap: Decodable { let state: RewardsState? }
        let st = (try? api.decode(Wrap.self, d))?.state
        return DailyClaim(xp: (o["xp"] as? NSNumber)?.intValue ?? 0, capsule: (o["capsule"] as? Bool) ?? false, state: st)
    }

    static func rewardHistory(cursor: String? = nil) async throws -> Page<RewardHistoryItem> {
        var body: [String: Any] = ["action": "history"]
        if let cursor { body["cursor"] = cursor }
        return try api.decode(Page<RewardHistoryItem>.self, try await call("rewards", body))
    }

    // MARK: vaultCapsules

    static func capsules() async throws -> CapsulesState {
        try api.decode(CapsulesState.self, try await call("vaultCapsules", ["action": "state"]))
    }

    static func openCapsule(_ id: String) async throws -> VaultCapsule? {
        struct R: Decodable { let capsule: VaultCapsule? }
        return try api.decode(R.self, try await call("vaultCapsules", ["action": "open", "capsule_id": id])).capsule
    }

    // MARK: communityEngine

    static func community(_ action: String, _ extra: [String: Any] = [:]) async throws -> Data {
        var body = extra
        body["action"] = action
        return try await api.invoke("communityEngine", body)
    }

    static func banner() async throws -> EventBannerData {
        try api.decode(EventBannerData.self, try await community("banner"))
    }

    static func leaderboard(_ eventId: String) async throws -> [LeaderboardRow] {
        struct R: Decodable { let rows: [LeaderboardRow]? }
        return (try? api.decode(R.self, try await community("leaderboard", ["event_id": eventId])))?.rows ?? []
    }

    // MARK: eventEngine

    static func eventState(_ eventId: String, visit: Bool = true) async throws -> EventEngineState {
        let d = try await api.invoke("eventEngine", ["action": "state", "event_id": eventId, "visit": visit])
        return try api.decode(EventEngineState.self, d)
    }

    /// action: join | claim_quest | claim_event
    static func eventAction(_ action: String, eventId: String, questKey: String? = nil) async throws -> [String: Any] {
        var body: [String: Any] = ["action": action, "event_id": eventId]
        if let questKey { body["quest_key"] = questKey }
        return json(try await call("eventEngine", body))
    }

    // MARK: Cards

    static func allCards() async throws -> [TradingCard] {
        try await api.list("TradingCard", TradingCard.self, sort: "rarity", limit: 200,
                           fields: ["card_id", "title", "category", "rarity", "description", "artwork_url", "manga_id", "flavor"]).items
    }

    static func cards(ids: [String]) async throws -> [TradingCard] {
        guard !ids.isEmpty else { return [] }
        return try await api.list("TradingCard", TradingCard.self, query: ["card_id": ["$in": ids]], limit: max(ids.count, 1)).items
    }

    static func myCards(limit: Int = 500, query: [String: Any] = [:], sort: String? = nil) async throws -> [UserCard] {
        try await api.list("UserCard", UserCard.self, query: query, sort: sort, limit: limit,
                           fields: ["card_id", "count", "favorite", "source"]).items
    }
}

extension Base44Client {
    /// `/entities/<Name>/count`
    func count(_ entity: String, query q: [String: Any] = [:]) async throws -> Int {
        let s = (try? JSONSerialization.data(withJSONObject: q)).flatMap { String(data: $0, encoding: .utf8) } ?? "{}"
        let d = try await send("GET", "apps/\(Config.appId)/entities/\(entity)/count", query: [URLQueryItem(name: "q", value: s)])
        let o = (try? JSONSerialization.jsonObject(with: d) as? [String: Any]) ?? [:]
        return (o["count"] as? NSNumber)?.intValue ?? 0
    }
}
