import Foundation

struct APIError: LocalizedError {
    let status: Int
    let message: String
    var errorDescription: String? { message }
}

/// Cursor page returned by `/entities/<Name>/v2/list`.
struct Page<T: Decodable>: Decodable {
    let items: [T]
    let next_cursor: String?
    let has_more: Bool?

    init(items: [T], next_cursor: String? = nil, has_more: Bool? = false) {
        self.items = items
        self.next_cursor = next_cursor
        self.has_more = has_more
    }
}

/// Thin native port of the Base44 JS SDK (auth, entities, functions, file upload).
final class Base44Client: @unchecked Sendable {
    static let shared = Base44Client()

    private let session: URLSession
    private let decoder = JSONDecoder()
    private let lock = NSLock()
    private var _token: String?

    /// Called when the server rejects the stored token (HTTP 401).
    var onUnauthorized: (@Sendable () -> Void)?

    var token: String? {
        get { lock.lock(); defer { lock.unlock() }; return _token }
        set {
            lock.lock(); _token = newValue; lock.unlock()
            Keychain.set(newValue, for: "base44_access_token")
        }
    }

    private init() {
        let cfg = URLSessionConfiguration.default
        cfg.timeoutIntervalForRequest = 45
        cfg.requestCachePolicy = .reloadIgnoringLocalCacheData
        session = URLSession(configuration: cfg)
        _token = Keychain.get("base44_access_token")
    }

    private var appPath: String { "apps/\(Config.appId)" }

    // MARK: - Low level

    @discardableResult
    func send(_ method: String,
              _ path: String,
              query: [URLQueryItem] = [],
              json: Any? = nil,
              body: Data? = nil,
              contentType: String? = nil,
              authorized: Bool = true) async throws -> Data {
        var comps = URLComponents(url: Config.apiBaseURL.appendingPathComponent(path), resolvingAgainstBaseURL: false)!
        if !query.isEmpty {
            comps.queryItems = query
            // URLComponents leaves "+" untouched, which servers read as a space.
            comps.percentEncodedQuery = comps.percentEncodedQuery?.replacingOccurrences(of: "+", with: "%2B")
        }
        var req = URLRequest(url: comps.url!)
        req.httpMethod = method
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        if authorized, let token { req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        if let json {
            req.httpBody = try JSONSerialization.data(withJSONObject: json, options: [])
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        } else if let body {
            req.httpBody = body
            if let contentType { req.setValue(contentType, forHTTPHeaderField: "Content-Type") }
        }

        let (data, response) = try await session.data(for: req)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            if status == 401, authorized, token != nil { onUnauthorized?() }
            throw APIError(status: status, message: Self.errorMessage(from: data, status: status))
        }
        return data
    }

    private static func errorMessage(from data: Data, status: Int) -> String {
        if let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            for key in ["message", "detail", "error"] {
                if let s = obj[key] as? String, !s.isEmpty { return s }
                if let arr = obj[key] as? [[String: Any]], let m = arr.first?["msg"] as? String { return m }
            }
        }
        if let s = String(data: data, encoding: .utf8), !s.isEmpty, s.count < 200 { return s }
        return "Request failed (\(status))"
    }

    func decode<T: Decodable>(_ type: T.Type, _ data: Data) throws -> T {
        try decoder.decode(T.self, from: data)
    }

    // MARK: - Auth

    struct LoginResponse: Decodable { let access_token: String?; let user: AppUser? }

    func login(email: String, password: String) async throws {
        let data = try await send("POST", "\(appPath)/auth/login",
                                  json: ["email": email, "password": password], authorized: false)
        let res = try decode(LoginResponse.self, data)
        guard let t = res.access_token else { throw APIError(status: 0, message: "Invalid email or password") }
        token = t
    }

    func register(email: String, password: String) async throws {
        try await send("POST", "\(appPath)/auth/register", json: ["email": email, "password": password], authorized: false)
    }

    func verifyOtp(email: String, code: String) async throws {
        let data = try await send("POST", "\(appPath)/auth/verify-otp",
                                  json: ["email": email, "otp_code": code], authorized: false)
        if let res = try? decode(LoginResponse.self, data), let t = res.access_token { token = t }
    }

    func resendOtp(email: String) async throws {
        try await send("POST", "\(appPath)/auth/resend-otp", json: ["email": email], authorized: false)
    }

    func resetPasswordRequest(email: String) async throws {
        try await send("POST", "\(appPath)/auth/reset-password-request", json: ["email": email], authorized: false)
    }

    func me() async throws -> AppUser {
        try decode(AppUser.self, try await send("GET", "\(appPath)/entities/User/me"))
    }

    /// Google sign-in URL (same flow as `base44.auth.loginWithProvider("google")`).
    /// After sign-in the backend redirects to `fromURL?access_token=…`.
    func googleLoginURL(fromURL: String) -> URL {
        var c = URLComponents(url: Config.apiBaseURL.appendingPathComponent("apps/auth/login"), resolvingAgainstBaseURL: false)!
        c.queryItems = [URLQueryItem(name: "app_id", value: Config.appId),
                        URLQueryItem(name: "from_url", value: fromURL)]
        return c.url!
    }

    func logout() { token = nil }

    // MARK: - Entities

    func list<T: Decodable>(_ entity: String,
                            _ type: T.Type,
                            query q: [String: Any] = [:],
                            sort: String? = nil,
                            limit: Int = 100,
                            fields: [String]? = nil,
                            cursor: String? = nil) async throws -> Page<T> {
        var items = [URLQueryItem(name: "q", value: jsonString(q)),
                     URLQueryItem(name: "limit", value: String(limit))]
        if let sort { items.append(URLQueryItem(name: "sort", value: sort)) }
        if let fields { items.append(URLQueryItem(name: "fields", value: fields.joined(separator: ","))) }
        if let cursor { items.append(URLQueryItem(name: "cursor", value: cursor)) }
        let data = try await send("GET", "\(appPath)/entities/\(entity)/v2/list", query: items)
        return try decode(Page<T>.self, data)
    }

    func distinct(_ entity: String, field: String, query q: [String: Any] = [:]) async throws -> [String] {
        let items = [URLQueryItem(name: "q", value: jsonString(q)),
                     URLQueryItem(name: "limit", value: "100"),
                     URLQueryItem(name: "distinct", value: field)]
        let data = try await send("GET", "\(appPath)/entities/\(entity)/v2/list", query: items)
        let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        return (obj?["items"] as? [Any] ?? []).compactMap { $0 as? String }
    }

    func get<T: Decodable>(_ entity: String, _ id: String, _ type: T.Type) async throws -> T {
        try decode(T.self, try await send("GET", "\(appPath)/entities/\(entity)/\(id)"))
    }

    func create<T: Decodable>(_ entity: String, _ data: [String: Any], _ type: T.Type) async throws -> T {
        try decode(T.self, try await send("POST", "\(appPath)/entities/\(entity)", json: data))
    }

    func update(_ entity: String, _ id: String, _ data: [String: Any]) async throws {
        try await send("PUT", "\(appPath)/entities/\(entity)/\(id)", json: data)
    }

    func delete(_ entity: String, _ id: String) async throws {
        try await send("DELETE", "\(appPath)/entities/\(entity)/\(id)")
    }

    // MARK: - Functions & integrations

    func invoke(_ function: String, _ payload: [String: Any]) async throws -> Data {
        try await send("POST", "\(appPath)/functions/\(function)", json: payload)
    }

    /// `integrations.Core.UploadPublicFile` — returns the public file URL.
    func uploadPublicFile(_ data: Data, filename: String, mimeType: String) async throws -> String {
        let boundary = "Boundary-\(UUID().uuidString)"
        var body = Data()
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"file\"; filename=\"\(filename)\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: \(mimeType)\r\n\r\n".data(using: .utf8)!)
        body.append(data)
        body.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)
        let res = try await send("POST", "\(appPath)/integration-endpoints/Core/UploadPublicFile",
                                 body: body, contentType: "multipart/form-data; boundary=\(boundary)")
        let obj = try JSONSerialization.jsonObject(with: res) as? [String: Any]
        guard let url = obj?["file_url"] as? String else { throw APIError(status: 0, message: "Upload failed") }
        return url
    }

    private func jsonString(_ obj: [String: Any]) -> String {
        guard let d = try? JSONSerialization.data(withJSONObject: obj, options: [.sortedKeys]),
              let s = String(data: d, encoding: .utf8) else { return "{}" }
        return s
    }
}
