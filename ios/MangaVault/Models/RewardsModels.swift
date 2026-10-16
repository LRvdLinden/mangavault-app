import Foundation

// Models for the server-side reward system (function `rewards`), Vault Capsules
// (function `vaultCapsules`), trading cards, community events and manga rooms.
// All fields are optional / defaulted so new backend fields never break decoding.

// MARK: - Flexible number decoding

/// Decodes Int or Double (Base44 often returns 3.0 for integers).
@propertyWrapper
struct FlexInt: Codable, Hashable {
    var wrappedValue: Int
    init(wrappedValue: Int) { self.wrappedValue = wrappedValue }
    init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if let i = try? c.decode(Int.self) { wrappedValue = i }
        else if let d = try? c.decode(Double.self) { wrappedValue = Int(d) }
        else if let s = try? c.decode(String.self), let i = Int(s) { wrappedValue = i }
        else { wrappedValue = 0 }
    }
    func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer(); try c.encode(wrappedValue)
    }
}

extension KeyedDecodingContainer {
    func decode(_ type: FlexInt.Type, forKey key: Key) throws -> FlexInt {
        (try? decodeIfPresent(FlexInt.self, forKey: key)) ?? FlexInt(wrappedValue: 0)
    }
}

// MARK: - rewards { action: state }

struct ServerLevel: Codable, Hashable {
    @FlexInt var level: Int
    @FlexInt var into: Int
    @FlexInt var span: Int
    @FlexInt var next: Int
    @FlexInt var total: Int

    var info: LevelInfo { LevelInfo(level: max(level, 1), into: into, span: span, next: next, total: total) }
}

struct ServerXP: Codable, Hashable {
    @FlexInt var base: Int
    @FlexInt var bonus: Int
    @FlexInt var total: Int
}

struct ServerQuest: Codable, Hashable, Identifiable {
    let id: String
    @FlexInt var value: Int
    @FlexInt var total: Int
    @FlexInt var xp: Int
    var completed: Bool?
    var claimed: Bool?
}

struct DailyRewardState: Codable, Hashable {
    var claimedToday: Bool?
    @FlexInt var streak: Int
    @FlexInt var nextStreak: Int
    @FlexInt var xp: Int
    var capsule: Bool?
    var nextAvailable: String?
}

struct LevelReward: Codable, Hashable, Identifiable {
    @FlexInt var level: Int
    var tier: String?
    var label: String?
    var unlocked: Bool?
    var id: Int { level }
}

struct FreshReward: Codable, Hashable {
    var kind: String?
    var label: String?
    @FlexInt var xp: Int
}

struct RewardsState: Codable, Hashable {
    var xp: ServerXP
    var level: ServerLevel
    var achievements: [UnlockedAchievement]?
    var quests: [ServerQuest]?
    var weekly: [ServerQuest]?
    var daily: DailyRewardState?
    var today: String?
    var levelRewards: [LevelReward]?
    var fresh: [FreshReward]?
}

struct RewardHistoryItem: Codable, Hashable, Identifiable {
    let id: String
    var kind: String?
    var label: String?
    @FlexInt var xp: Int
    var status: String?
    var granted_at: String?
    var created_date: String?

    static let kindLabels: [String: String] = [
        "daily_quest": "Daily quest", "weekly_quest": "Weekly quest", "achievement": "Achievement",
        "daily_reward": "Daily reward", "level_reward": "Level reward", "migration": "Migration"
    ]
}

// MARK: - Vault Capsules

struct CapsuleReward: Codable, Hashable {
    var kind: String?
    var title: String?
    var rarity: String?
    var artwork_url: String?
}

struct VaultCapsule: Codable, Hashable, Identifiable {
    let id: String
    var tier: String?
    var source: String?
    var opened: Bool?
    var opened_date: String?
    var reward: CapsuleReward?
    var created_date: String?
}

struct CapsulesState: Decodable {
    var capsules: [VaultCapsule]
    var odds: [String: [String: Double]]
    var fallback_xp: [String: Int]

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        capsules = (try? c.decode([VaultCapsule].self, forKey: .capsules)) ?? []
        odds = (try? c.decode([String: [String: Double]].self, forKey: .odds)) ?? [:]
        if let ints = try? c.decode([String: Int].self, forKey: .fallback_xp) { fallback_xp = ints }
        else if let d = try? c.decode([String: Double].self, forKey: .fallback_xp) { fallback_xp = d.mapValues { Int($0) } }
        else { fallback_xp = [:] }
    }
    enum CodingKeys: String, CodingKey { case capsules, odds, fallback_xp }
}

// MARK: - Trading cards

enum CardRarity: String, CaseIterable, Identifiable {
    case common, uncommon, rare, epic, legendary, mythic
    var id: String { rawValue }
    var label: String { rawValue.capitalized }
    var hex: String {
        switch self {
        case .common: return "#9ca3af"
        case .uncommon: return "#22c55e"
        case .rare: return "#3b82f6"
        case .epic: return "#a855f7"
        case .legendary: return "#f59e0b"
        case .mythic: return "#ef4444"
        }
    }
    static func of(_ s: String?) -> CardRarity { CardRarity(rawValue: s ?? "") ?? .common }
}

struct TradingCard: Codable, Hashable, Identifiable {
    var id: String
    var card_id: String
    var title: String
    var category: String?
    var rarity: String?
    var description: String?
    var artwork_url: String?
    var manga_id: String?
    var flavor: String?

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        card_id = (try? c.decode(String.self, forKey: .card_id)) ?? ""
        id = (try? c.decode(String.self, forKey: .id)) ?? card_id
        title = (try? c.decode(String.self, forKey: .title)) ?? "Card"
        category = try? c.decode(String.self, forKey: .category)
        rarity = try? c.decode(String.self, forKey: .rarity)
        description = try? c.decode(String.self, forKey: .description)
        artwork_url = try? c.decode(String.self, forKey: .artwork_url)
        manga_id = try? c.decode(String.self, forKey: .manga_id)
        flavor = try? c.decode(String.self, forKey: .flavor)
    }
    enum CodingKeys: String, CodingKey { case id, card_id, title, category, rarity, description, artwork_url, manga_id, flavor }

    var rarityValue: CardRarity { CardRarity.of(rarity) }
    var isLimited: Bool { category == "limited" }
}

struct UserCard: Codable, Hashable, Identifiable {
    let id: String
    var card_id: String
    @FlexInt var count: Int
    var favorite: Bool?
    var source: String?

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = (try? c.decode(String.self, forKey: .id)) ?? UUID().uuidString
        card_id = (try? c.decode(String.self, forKey: .card_id)) ?? ""
        _count = (try? c.decodeIfPresent(FlexInt.self, forKey: .count)) ?? FlexInt(wrappedValue: 1)
        favorite = try? c.decode(Bool.self, forKey: .favorite)
        source = try? c.decode(String.self, forKey: .source)
    }
    enum CodingKeys: String, CodingKey { case id, card_id, count, favorite, source }
}

// MARK: - Community events

struct EventQuestDef: Codable, Hashable, Identifiable {
    var key: String
    var label: String?
    var description: String?
    var icon: String?
    var kind: String?
    @FlexInt var target: Int
    @FlexInt var xp: Int
    @FlexInt var points: Int
    var reward_card_id: String?
    var badge_id: String?
    var repeatable: Bool?
    // State (filled by eventEngine / grand_state)
    @FlexInt var count: Int
    var done: Bool?
    var claimed: Bool?

    var id: String { key }

    static let kindLabels: [String: String] = [
        "collect_volumes": "Add volumes to the collection",
        "new_series": "Add a manga not yet in the collection",
        "progress_volumes": "Update reading progress",
        "chapters": "Log reading activity",
        "review": "Publish a review",
        "daily_quests": "Complete daily quests",
        "visit": "Open the event page",
        "community_goal": "Reach the community goal",
        "quests_done": "Complete other quests of this event"
    ]
    var kindLabel: String { EventQuestDef.kindLabels[kind ?? ""] ?? (kind ?? "") }
}

struct CommunityEvent: Codable, Hashable, Identifiable {
    let id: String
    var title: String?
    var subtitle: String?
    var short_description: String?
    var long_description: String?
    var description: String?
    var type: String?
    var icon: String?
    var banner_url: String?
    var background_url: String?
    var accent_color: String?
    var decoration: String?
    var theme: String?
    var start_date: String?
    var start_time: String?
    var end_date: String?
    var end_time: String?
    var timezone: String?
    var reward_claim_end: String?
    var publish_status: String?
    var status: String?
    var featured: Bool?
    var requires_join: Bool?
    var enabled: Bool?
    var requirement_type: String?
    @FlexInt var requirement_value: Int
    var reward_card_id: String?
    var extra_card_ids: [String]?
    var badge_name: String?
    var image_source: String?
    var quests: [EventQuestDef]?

    static let typeLabels: [String: String] = [
        "manga_week": "Manga of the Week", "genre_month": "Genre of the Month", "challenge": "Challenge",
        "marathon": "Reading Marathon", "discovery": "Discovery Event", "card_event": "Card Event",
        "halloween_event": "Halloween Event", "christmas_event": "Christmas Event", "holiday_event": "Holiday Event",
        "special_release": "Special Release", "grand_event": "Grand Event", "manga_release": "Manga Release",
        "community_challenge": "Community Challenge", "special_event": "Special Event"
    ]
    static let requirementLabels: [String: String] = [
        "added": "volumes added", "completed": "volumes completed", "progress": "progress updates",
        "minutes": "reading minutes", "chapters": "chapters read", "series": "new series tracked", "rated": "manga rated"
    ]

    var typeLabel: String { CommunityEvent.typeLabels[type ?? ""] ?? (type ?? "") }
    var accent: String { nonEmpty(accent_color) ?? "#FF7A00" }
    var heroImage: String? { nonEmpty(background_url) ?? nonEmpty(banner_url) }
    var displayTitle: String { [nonEmpty(icon), title].compactMap { $0 }.joined(separator: " ") }
}

struct EventParticipation: Codable, Hashable {
    var event_id: String?
    @FlexInt var progress: Int
    var completed: Bool?
    var reward_claimed: Bool?
}

struct CommunityProgress: Codable, Hashable {
    @FlexInt var total: Int
    @FlexInt var goal: Int
    @FlexInt var participants: Int
    @FlexInt var mine: Int
}

/// communityEngine { action: banner }
struct EventBannerData: Decodable {
    var kind: String?
    var status: String?
    var target: String?
    var event: CommunityEvent?
    var participation: EventParticipation?
    @FlexInt var max_points: Int
}

/// eventEngine { action: state }
struct EventEngineState: Decodable {
    var event: CommunityEvent?
    var status: String?
    var can_claim: Bool?
    var start: String?
    var end: String?
    var participation: EventParticipation?
    var quests: [EventQuestDef]?
    @FlexInt var points: Int
    @FlexInt var earned: Int
    @FlexInt var done_count: Int
    var quests_completed: Bool?
    @FlexInt var max_xp: Int
    var rewards: EventRewards?
    var owned_cards: [String]?
    var community: CommunityProgress?
    var error: String?

    struct EventRewards: Decodable {
        var cards: [TradingCard]?
        var badge_name: String?
    }
}

/// communityEngine { action: grand_state | grand_claim }
struct GrandEventState: Decodable {
    var status: String?
    var event: CommunityEvent?
    var start: String?
    var end: String?
    var quests: GrandQuests?
    var community: CommunityProgress?
    var granted: [String]?
    var error: String?

    struct GrandQuests: Decodable {
        @FlexInt var points: Int
        var quests: [EventQuestDef]?
    }
}

/// communityEngine { action: release_state | christmas_state }
struct SpecialEventState: Decodable {
    var status: String?
    var participation: EventParticipation?
    var quests: SpecialQuests?

    struct SpecialQuests: Decodable {
        var quest_states: [String: QuestFlag?]?
        @FlexInt var points: Int
    }
    struct QuestFlag: Decodable {
        var done: Bool?
        @FlexInt var count: Int
    }

    func isDone(_ key: String) -> Bool { (quests?.quest_states?[key] ?? nil)?.done ?? false }
    func count(_ key: String) -> Int { (quests?.quest_states?[key] ?? nil)?.count ?? 0 }
}

struct LeaderboardRow: Decodable, Hashable, Identifiable {
    var user_id: String?
    var name: String?
    @FlexInt var progress: Int
    var id: String { (user_id ?? "") + (name ?? "") }
}

// MARK: - Manga room

struct RoomItem: Codable, Hashable {
    var kind: String
    var ref: String?
    var label: String?
    var art: String?
    var x: Double
    var y: Double
    var scale: Double?

    init(kind: String, ref: String? = nil, label: String?, art: String?, x: Double = 50, y: Double = 50, scale: Double? = 1) {
        self.kind = kind; self.ref = ref; self.label = label; self.art = art; self.x = x; self.y = y; self.scale = scale
    }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        kind = (try? c.decode(String.self, forKey: .kind)) ?? "decoration"
        ref = try? c.decode(String.self, forKey: .ref)
        label = try? c.decode(String.self, forKey: .label)
        art = try? c.decode(String.self, forKey: .art)
        x = (try? c.decode(Double.self, forKey: .x)) ?? 50
        y = (try? c.decode(Double.self, forKey: .y)) ?? 50
        scale = try? c.decode(Double.self, forKey: .scale)
    }
    var json: [String: Any] {
        ["kind": kind, "ref": ref ?? "", "label": label ?? "", "art": art ?? "", "x": x, "y": y, "scale": scale ?? 1]
    }
}

struct RoomLayout: Codable, Hashable, Identifiable {
    let id: String
    var items: [RoomItem]?
    var is_public: Bool?
    var created_by_id: String?
}

// MARK: - Dashboard layout (UserProfile.dashboard_layout)

struct DashboardSection: Codable, Hashable, Identifiable {
    var id: String
    var hidden: Bool

    init(id: String, hidden: Bool = false) { self.id = id; self.hidden = hidden }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = (try? c.decode(String.self, forKey: .id)) ?? ""
        hidden = (try? c.decode(Bool.self, forKey: .hidden)) ?? false
    }

    static let all: [(id: String, label: String)] = [
        ("continue", "Continue Reading"), ("quests", "Daily Quests"), ("achievements", "Recent Achievements"),
        ("cards", "Recent Trading Cards"), ("recent", "Recently Added"), ("charts", "Charts")
    ]
    static var defaults: [DashboardSection] { all.map { DashboardSection(id: $0.id) } }

    /// Same normalisation as the web app: keep known ids in stored order, append missing ones.
    static func normalized(_ stored: [DashboardSection]?) -> [DashboardSection] {
        let known = Set(all.map { $0.id })
        var out = (stored ?? []).filter { known.contains($0.id) }
        for s in all where !out.contains(where: { $0.id == s.id }) { out.append(DashboardSection(id: s.id)) }
        return out
    }
    var label: String { DashboardSection.all.first { $0.id == id }?.label ?? id }
}
