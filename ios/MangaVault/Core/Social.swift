import Foundation

/// Wrappers around the social* backend functions (same calls as the web app).
enum Social {
    private static var api: Base44Client { .shared }

    private struct Items<T: Decodable>: Decodable {
        let items: [T]?
        let unread: Int?
        let next_cursor: String?
        let has_more: Bool?
    }

    static func search(_ query: String) async throws -> [SocialUser] {
        let d = try await api.invoke("socialSearch", ["query": query])
        return try api.decode(Items<SocialUser>.self, d).items ?? []
    }

    static func overview() async throws -> FriendsOverview {
        try api.decode(FriendsOverview.self, try await api.invoke("socialFriends", ["action": "overview"]))
    }

    /// action: send / accept / decline / cancel / remove
    static func friends(_ action: String, _ params: [String: Any]) async throws {
        var body = params
        body["action"] = action
        let d = try await api.invoke("socialFriends", body)
        if let obj = try? JSONSerialization.jsonObject(with: d) as? [String: Any], let err = obj["error"] as? String, !err.isEmpty {
            throw APIError(status: 0, message: err)
        }
    }

    static func conversations() async throws -> [Conversation] {
        try api.decode(Items<Conversation>.self, try await api.invoke("socialChat", ["action": "list"])).items ?? []
    }

    static func messages(_ conversationId: String) async throws -> [ChatMessage] {
        let d = try await api.invoke("socialChat", ["action": "messages", "conversation_id": conversationId, "limit": 50])
        return try api.decode(Items<ChatMessage>.self, d).items ?? []
    }

    static func send(to receiverId: String, _ content: String) async throws {
        let d = try await api.invoke("socialChat", ["action": "send", "receiver_id": receiverId, "content": content])
        if let obj = try? JSONSerialization.jsonObject(with: d) as? [String: Any], let err = obj["error"] as? String, !err.isEmpty {
            throw APIError(status: 0, message: err)
        }
    }

    static func markRead(_ conversationId: String) async {
        _ = try? await api.invoke("socialChat", ["action": "read", "conversation_id": conversationId])
    }

    static func profile(_ profileId: String) async throws -> SocialProfileResponse {
        try api.decode(SocialProfileResponse.self, try await api.invoke("socialProfile", ["profile_id": profileId]))
    }

    static func collection(_ profileId: String) async throws -> SocialCollectionResponse {
        try api.decode(SocialCollectionResponse.self, try await api.invoke("socialCollection", ["profile_id": profileId]))
    }

    static func notifications() async throws -> (items: [VaultNotification], unread: Int) {
        let r = try api.decode(Items<VaultNotification>.self, try await api.invoke("socialNotifications", ["action": "list"]))
        return (r.items ?? [], r.unread ?? 0)
    }

    static func markAllNotificationsRead() async {
        _ = try? await api.invoke("socialNotifications", ["action": "readAll"])
    }
}
