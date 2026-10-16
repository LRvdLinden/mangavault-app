import Foundation

// Field names mirror the Base44 entity schema exactly (snake_case), so the
// native app reads and writes the same records as the web app.

struct AppUser: Codable, Hashable {
    let id: String
    let email: String?
    let full_name: String?
}

struct VolumeCover: Codable, Hashable {
    let volume: Double
    let url: String?
}

struct Manga: Codable, Identifiable, Hashable {
    let id: String
    var title: String
    var publisher: String?
    var is_shonen_jump: Bool?
    var price: Double?
    var total_volumes: Double?
    var genre: String?
    var description: String?
    var languages: [String]?
    var cover_seed: String?
    var cover_image_url: String?
    var edition: String?
    var volumes_included: String?
    var omnibus_of: String?
    var mangadex_id: String?
    var volume_covers: [VolumeCover]?
    var mangaFireUrl: String?
    var mangaFireSlug: String?

    var isOmnibus: Bool { edition == "omnibus" }
    var isShonenJump: Bool { is_shonen_jump ?? false }
    var totalVolumes: Int { Int(total_volumes ?? 0) }
    var priceValue: Double { price ?? 0 }

    static let listFields = ["title", "cover_image_url", "cover_seed", "total_volumes", "price", "edition",
                             "volumes_included", "is_shonen_jump", "genre", "publisher", "omnibus_of"]
}

enum ReadStatus: String, CaseIterable, Identifiable {
    case plan_to_read, reading, completed, on_hold, dropped
    var id: String { rawValue }

    /// The volume detail page of the web app only offers these four.
    static let volumeCases: [ReadStatus] = [.plan_to_read, .reading, .completed, .on_hold]

    var label: String {
        switch self {
        case .reading: return "Reading"
        case .completed: return "Completed"
        case .plan_to_read: return "Plan to Read"
        case .on_hold: return "On Hold"
        case .dropped: return "Dropped"
        }
    }
    /// Label used on the volume detail page of the web app.
    var volumeLabel: String { self == .plan_to_read ? "Want to Read" : label }
}

struct CollectionItem: Codable, Identifiable, Hashable {
    let id: String
    var manga_id: String?
    var title: String?
    var publisher: String?
    var price: Double?
    var total_volumes: Double?
    var is_shonen_jump: Bool?
    var cover_seed: String?
    var cover_image_url: String?
    var volume_number: Double?
    var volume_cover_url: String?
    var read: Bool?
    var edition: String?
    var volumes_included: String?
    var omnibus_of: String?
    var status: String?
    var rating: Double?
    var notes: String?
    var favorite: Bool?
    var current_chapter: Double?
    var last_read_date: String?
    var created_date: String?
    var updated_date: String?

    /// Same fallback as the web app: explicit status, else `read` → completed / plan_to_read.
    var effectiveStatus: ReadStatus {
        if let s = status, let st = ReadStatus(rawValue: s) { return st }
        return (read ?? false) ? .completed : .plan_to_read
    }
    var isOmnibus: Bool { edition == "omnibus" }
    var volume: Int? { volume_number.map { Int($0) } }
    var ratingValue: Int { Int(rating ?? 0) }
    var isFavorite: Bool { favorite ?? false }
    var chapter: Int { Int(current_chapter ?? 0) }
    var hasNotes: Bool { !(notes ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    var displayCover: String? { nonEmpty(volume_cover_url) ?? nonEmpty(cover_image_url) }
    var volumeLabel: String {
        if isOmnibus { return nonEmpty(volumes_included) ?? "Omnibus" }
        let v: String = volume.map { String($0) } ?? ""
        return "Vol. \(v)"
    }
    var titleText: String { title ?? "" }
}

struct WishlistItem: Codable, Identifiable, Hashable {
    let id: String
    var manga_id: String?
    var title: String?
    var publisher: String?
    var price: Double?
    var is_shonen_jump: Bool?
    var cover_seed: String?
    var cover_image_url: String?
    var volume_number: Double?
    var volume_cover_url: String?
    var edition: String?
    var volumes_included: String?
    var omnibus_of: String?
    var created_date: String?

    var isOmnibus: Bool { edition == "omnibus" }
    var volume: Int? { volume_number.map { Int($0) } }
    var displayCover: String? { nonEmpty(volume_cover_url) ?? nonEmpty(cover_image_url) }
    var volumeLabel: String {
        if isOmnibus { return nonEmpty(volumes_included) ?? "Omnibus" }
        let v: String = volume.map { String($0) } ?? ""
        return "Vol. \(v)"
    }
}

struct ReadingActivity: Codable, Identifiable, Hashable {
    let id: String
    var type: String?
    var manga_id: String?
    var title: String?
    var volume_number: Double?
    var meta: String?
    var created_date: String?

    var day: String { String((created_date ?? "").prefix(10)) }
}

struct UnlockedAchievement: Codable, Hashable {
    let id: String
    let date: String?
}

struct UserProfile: Codable, Identifiable, Hashable {
    let id: String
    var display_name: String?
    var bio: String?
    var avatar_url: String?
    var banner_url: String?
    var theme_id: String?
    var profile_public: Bool?
    var collection_public: Bool?
    var xp_bonus: Double?
    var unlocked_achievements: [UnlockedAchievement]?
    var featured_achievements: [String]?
    var daily_date: String?
    var daily_claimed: [String]?
    var weekly_date: String?
    var weekly_claimed: [String]?
    var favorite_manga: [String]?
    var favorite_characters: [String]?
    var favorite_genres: [String]?
    var dismissed_recommendations: [String]?
    var dashboard_layout: [DashboardSection]?
    var created_by_id: String?

    var xpBonus: Int { Int(xp_bonus ?? 0) }
}

// MARK: - Build 2 entities

struct ReadingSession: Codable, Identifiable, Hashable {
    let id: String
    var manga_id: String?
    var title: String?
    var volume_number: Double?
    var date: String?
    var minutes: Double?
    var chapters_read: Double?
    var notes: String?
    var created_date: String?

    var day: String { String((nonEmpty(date) ?? created_date ?? "").prefix(10)) }
}

enum JournalType: String, CaseIterable, Identifiable {
    case note, moment, reminder, rating
    var id: String { rawValue }
    var label: String {
        switch self {
        case .note: return "Note"
        case .moment: return "Favorite Moment"
        case .reminder: return "Reminder"
        case .rating: return "Rating"
        }
    }
    var icon: String {
        switch self {
        case .note: return "note.text"
        case .moment: return "sparkles"
        case .reminder: return "bell"
        case .rating: return "star.fill"
        }
    }
}

struct JournalEntry: Codable, Identifiable, Hashable {
    let id: String
    var manga_id: String?
    var title: String?
    var volume_number: Double?
    var entry_type: String?
    var content: String?
    var rating: Double?
    var is_spoiler: Bool?
    var created_date: String?

    var type: JournalType { JournalType(rawValue: entry_type ?? "") ?? .note }
}

struct TierDef: Codable, Hashable {
    var name: String
    var color: String
}

struct TierEntry: Codable, Hashable {
    var kind: String
    var ref_id: String?
    var label: String
    var cover_url: String?
    var tier: Int
    var order: Int

    init(kind: String, ref_id: String?, label: String, cover_url: String?, tier: Int, order: Int) {
        self.kind = kind; self.ref_id = ref_id; self.label = label; self.cover_url = cover_url; self.tier = tier; self.order = order
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        kind = (try? c.decode(String.self, forKey: .kind)) ?? "manga"
        ref_id = try? c.decode(String.self, forKey: .ref_id)
        label = (try? c.decode(String.self, forKey: .label)) ?? ""
        cover_url = try? c.decode(String.self, forKey: .cover_url)
        tier = Int((try? c.decode(Double.self, forKey: .tier)) ?? -1)
        order = Int((try? c.decode(Double.self, forKey: .order)) ?? 0)
    }

    var json: [String: Any] {
        ["kind": kind, "ref_id": ref_id ?? "", "label": label, "cover_url": cover_url ?? "", "tier": tier, "order": order]
    }
}

struct TierList: Codable, Identifiable, Hashable {
    var id: String
    var name: String?
    var description: String?
    var list_type: String?
    var tiers: [TierDef]?
    var entries: [TierEntry]?
    var is_public: Bool?
    var created_date: String?

    static let defaultTiers: [TierDef] = [
        TierDef(name: "S", color: "#FF7A00"), TierDef(name: "A", color: "#f59e0b"), TierDef(name: "B", color: "#64748b"),
        TierDef(name: "C", color: "#475569"), TierDef(name: "D", color: "#334155")
    ]
}

/// Public view of another reader (as returned by the social* backend functions).
struct SocialUser: Codable, Identifiable, Hashable {
    let id: String              // UserProfile id
    var user_id: String?        // auth user id
    var display_name: String?
    var bio: String?
    var avatar_url: String?
    var banner_url: String?
    var favorite_manga: [String]?
    var favorite_characters: [String]?
    var favorite_genres: [String]?
    var friend: Bool?
    var request_id: String?

    var name: String { nonEmpty(display_name) ?? "Reader" }
}

struct FriendsOverview: Codable, Hashable {
    var friends: [SocialUser]
    var incoming: [SocialUser]
    var outgoing: [SocialUser]

    init(friends: [SocialUser] = [], incoming: [SocialUser] = [], outgoing: [SocialUser] = []) {
        self.friends = friends; self.incoming = incoming; self.outgoing = outgoing
    }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        friends = (try? c.decode([SocialUser].self, forKey: .friends)) ?? []
        incoming = (try? c.decode([SocialUser].self, forKey: .incoming)) ?? []
        outgoing = (try? c.decode([SocialUser].self, forKey: .outgoing)) ?? []
    }
}

struct Conversation: Codable, Identifiable, Hashable {
    let id: String
    var other_user_id: String?
    var other: SocialUser?
    var last_message: String?
    var last_message_date: String?
    var last_sender_id: String?
    var unread: Int?
}

struct ChatMessage: Codable, Identifiable, Hashable {
    let id: String
    var conversation_id: String?
    var sender_id: String?
    var receiver_id: String?
    var content: String?
    var read: Bool?
    var created_date: String?
}

struct VaultNotification: Codable, Identifiable, Hashable {
    let id: String
    var type: String?
    var title: String?
    var body: String?
    var related_id: String?
    var read: Bool?
    var created_date: String?
}

struct SocialProfileResponse: Decodable {
    var profile: SocialUser?
    var isOwn: Bool?
    var friend: Bool?
    var friendCount: Int?
    var volCount: Int?
    var seriesCount: Int?
    var profile_public: Bool?
    var collection_public: Bool?
    var is_private: Bool?
    var error: String?
}

struct SocialCollectionResponse: Decodable {
    var authorized: Bool?
    var isOwn: Bool?
    var friend: Bool?
    var collection_public: Bool?
    var owner: SocialUser?
    var items: [CollectionItem]?
}

/// Parses Base44 timestamps ("2026-10-14T15:57:21.434000" without zone = UTC, or with "Z").
enum ServerDate {
    private static let isoFrac: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter(); f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]; return f
    }()
    private static let iso: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter(); f.formatOptions = [.withInternetDateTime]; return f
    }()

    static func parse(_ s: String?) -> Date? {
        guard var s, !s.isEmpty else { return nil }
        if !(s.hasSuffix("Z") || s.range(of: #"[+-]\d\d:\d\d$"#, options: .regularExpression) != nil) { s += "Z" }
        if let d = isoFrac.date(from: s) ?? iso.date(from: s) { return d }
        // Trim long fractional parts (microseconds) that ISO8601DateFormatter rejects.
        if let dot = s.firstIndex(of: ".") {
            let tail = s[s.index(after: dot)...]
            let digits = tail.prefix { $0.isNumber }
            let rest = tail.dropFirst(digits.count)
            let head: String = String(s[..<dot])
            let frac: String = String(digits.prefix(3))
            let trimmed: String = head + "." + frac + String(rest)
            return isoFrac.date(from: trimmed)
        }
        return nil
    }

    /// "now", "5m", "3h", "2d" or a short date — like the web chat list.
    static func relative(_ s: String?) -> String {
        guard let d = parse(s) else { return "" }
        let secs = Date().timeIntervalSince(d)
        if secs < 60 { return "now" }
        if secs < 3600 { return "\(Int(secs / 60))m" }
        if secs < 86_400 { return "\(Int(secs / 3600))h" }
        if secs < 604_800 { return "\(Int(secs / 86_400))d" }
        return d.formatted(date: .numeric, time: .omitted)
    }

    static func short(_ s: String?) -> String {
        guard let d = parse(s) else { return "" }
        return d.formatted(.dateTime.month(.abbreviated).day().hour().minute())
    }
}

/// Response of the `getVolumeSynopsis` backend function.
struct VolumeSynopsis: Codable, Hashable {
    var synopsis: String?
    var recap: String?
    var location: String?
    var setting: String?
    var main_characters: [String]?
}

/// Decodes a JSON scalar (string or number) as a string — chapter numbers come back as either.
struct LooseString: Decodable, Hashable {
    let value: String
    init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if let s = try? c.decode(String.self) { value = s }
        else if let i = try? c.decode(Int.self) { value = String(i) }
        else if let d = try? c.decode(Double.self) { value = d == d.rounded() ? String(Int(d)) : String(d) }
        else { value = "" }
    }
}

func nonEmpty(_ s: String?) -> String? {
    guard let s, !s.isEmpty else { return nil }
    return s
}

extension Double {
    var euro: String { String(format: "€%.2f", self) }
}
